# Minecraft AntiCheat Plugin — Development Plan
> Designed for use with Claude Code. Paper/Spigot 1.20+, Java 17+.

---

## Architecture Overview

```
Plugin
├── PacketListener (PacketEvents / Netty inbound/outbound)
│   └── routes raw packets → PlayerData pipeline (async)
├── PlayerData (per-player state, held in ConcurrentHashMap)
│   ├── PositionBuffer     – last N positions + timestamps
│   ├── VelocityTracker    – server-authoritative velocity
│   ├── SimulatorWorld     – block/world snapshot for physics sim
│   └── CombatTracker      – attack timing, reach, target history
├── SimulationEngine (async thread pool)
│   ├── PhysicsSimulator   – predicts legal next position
│   └── ReachSimulator     – ray/hitbox intersection
├── CheckManager
│   ├── MovementChecks     – Speed, Flight, NoFall, Step, Timer
│   └── CombatChecks       – Reach, Hitbox, KillAura, AutoClicker, AimBot
├── ViolationManager       – tracks VL per check, fires thresholds
├── AlertManager           – broadcasts to staff, logs to file/DB
└── Config (YAML, hot-reloadable)
```

---

## Tech Stack

| Concern | Choice | Reason |
|---|---|---|
| Server API | Paper 1.20+ | Event API + async scheduler |
| Packet interception | **PacketEvents 2.x** | Modern, no external dep, built-in async, GrimAC-style |
| Async execution | `CompletableFuture` + fixed thread pool | Off main thread, ordered per player |
| Config | `config.yml` via Bukkit `FileConfiguration` | Simple, reloadable |
| Persistence (optional) | SQLite / flat file | Violation history logs |

---

## Thread Model

```
PacketEvents Netty Pipeline (per player)
  └─► SimplePacketListenerAbstract.onPacketReceive()
        └─► enqueue(PlayerCheckTask) to player's single-threaded executor
              └─► CheckManager.run()  ← ALL checks here, never main thread
                    ├─► SimulationEngine.simulate()
                    └─► ViolationManager.flag()
                          └─► IF threshold reached → scheduleSync(punishment)
                                                        ← only sync call
```

**Rules:**
- Every check runs on a **dedicated per-player single-thread executor** (prevents race conditions without locking).
- The main thread is only touched to execute punishments (kick/ban/teleport).
- Block lookups are done on a **world snapshot** copied at packet time, never live world queries.

---

## SimulatorWorld

The core concept borrowed from GrimAC: maintain a **frozen snapshot** of the world state relevant to the player's bounding box area so physics can be simulated off-thread without touching the live world.

```java
public class SimulatorWorld {
    // 7x7x7 block region around player, updated each position packet
    private final Map<BlockPos, BlockData> blockSnapshot = new HashMap<>();
    private double gravity;
    private boolean isInWater, isInLava, isOnIce, isOnSlime;

    // Called SYNC when player moves significantly or block changes nearby
    public void snapshot(Player player) { ... }

    // Called ASYNC in checks
    public boolean isOnGround(BoundingBox bb) { ... }
    public Set<Material> getCollidingBlocks(BoundingBox bb) { ... }
    public boolean canStepTo(BoundingBox from, BoundingBox to) { ... }
}
```

Block change events (`BlockBreakEvent`, `BlockPlaceEvent`, piston/redstone, etc.) must invalidate and refresh the snapshot for affected players.

---

## PhysicsSimulator

Predicts the **legal** velocity and position after one tick given current state.

```java
public class PhysicsSimulator {
    public SimulationResult simulate(PlayerData data, Vec3d input) {
        Vec3d velocity = data.getVelocity();
        SimulatorWorld world = data.getWorld();

        // 1. Apply input acceleration (capped by max speed for gamemode)
        // 2. Apply gravity if not on ground and not in fluid
        // 3. Apply friction (ground: 0.6 * slipperiness, air: 0.98)
        // 4. Collide with world snapshot bounding boxes
        // 5. Return predicted position + velocity

        return new SimulationResult(predictedPos, predictedVel, onGround);
    }
}
```

Accounts for: sprint multiplier, sneak slowdown, soul sand, ice, slime, knockback, water/lava drag, elytra.

---

## Movement Checks

### Speed
- Compare `actual delta XZ` vs `predicted delta XZ` from simulator.
- Allow tolerance of `±0.05` blocks per tick.
- **VL threshold:** 10 → alert, 20 → setback (teleport to last safe position).

### Flight
- If `!onGround && !isInFluid && !hasElytra && gamemode != CREATIVE`:
  - Simulator must predict Y velocity to be negative (or zero on first tick of jump).
  - Flag if player gains or maintains altitude without a valid jump.
- Levitation / Slow Falling potion effects must be excluded.

### NoFall
- Track `fallDistance` server-side in `PlayerData`.
- If player sends `onGround=true` packet and server fall distance ≥ 3, but no damage was dealt → flag.

### Timer
- Count position packets per 1000 ms window.
- Expected: ≤ 22 packets/sec (20 ticks + 10% jitter allowance).
- Flag if > 24 packets/sec sustained over 2 seconds.

### Step / HeadHitter
- If Y delta > 0.6 but ≤ 1.25 and player was on ground → validate step-up using simulator.
- If predicted step height from simulator is < actual → flag.

---

## Combat Checks

### Reach
```
Ray from eye position (interpolated from position buffer)
→ intersect with target's hitbox expanded by server-side hitbox + latency compensation
→ if ray miss and player swung → flag
```
- Compensate for latency: `ping / 50 ticks` of extra hitbox expansion, capped at 6 ticks.
- Threshold: flag if reach > `3.2 + latencyCompensation` blocks consistently (5/10 attacks).

### KillAura / MultiAura
- **No-swing flag:** Player deals damage without an `ANIMATION` (swing) packet preceding the `INTERACT_ENTITY` attack packet within the same tick window → flag.
- **Multi-target:** Player attacks ≥ 3 distinct entities within 1 tick → flag.
- **No-rotation:** Attack packet arrives but player's yaw/pitch has not changed in 3+ ticks while multiple entities are nearby → flag.

### AutoClicker
- Track inter-click deltas in a rolling 20-sample buffer.
- Compute **standard deviation** of deltas.
- Human clicking: StdDev typically > 15ms.
- Flag if StdDev < 5ms over 15+ samples (too consistent).
- Also flag CPS > 20 sustained over 3 seconds.

### AimBot / InvalidRotation
- Track rotation deltas (Δyaw, Δpitch) per tick from position packets.
- Flag: `Δyaw > 180°` in a single tick (impossible human movement).
- Flag: rotation snaps exactly to target center every tick while attacking (aim lock pattern).
- Flag: pitch outside `[-90, 90]` range.

### Velocity / AntiKB
- When knockback is applied server-side, store expected velocity in `PlayerData`.
- If next 3 position packets show near-zero XZ velocity change when knockback was applied → flag.

---

## ViolationManager

```yaml
# Per-check VL config example
checks:
  speed:
    enabled: true
    max-vl: 20
    actions:
      10: "alert %player% &cSpeed A (VL: %vl%)"
      15: "setback"
      20: "kick %player% &cSpeed cheating detected"
  reach:
    enabled: true
    max-vl: 15
    actions:
      5:  "alert %player% &cReach A (VL: %vl%)"
      10: "setback"
      15: "ban %player% 7d Cheating"
```

VL decays by `decay-rate` per second when no violations occur.

---

## Configuration (`config.yml`)

```yaml
anticheat:
  debug: false
  prefix: "&8[&cAC&8] "
  alert-permission: "anticheat.alerts"
  thread-pool-size: 4          # per-player executor threads
  latency-compensation: true
  max-ping-compensation-ticks: 6

setback:
  enabled: true
  save-interval-ticks: 40      # save safe position every 2 sec

checks:
  movement:
    speed:       { enabled: true, tolerance: 0.05 }
    flight:      { enabled: true }
    timer:       { enabled: true, max-cps: 22 }
    nofall:      { enabled: true }
    step:        { enabled: true }
  combat:
    reach:       { enabled: true, max-reach: 3.2 }
    killaura:    { enabled: true }
    autoclicker: { enabled: true, max-cps: 20, min-stddev: 5 }
    aimbot:      { enabled: true, max-delta-per-tick: 180 }
    velocity:    { enabled: true }

exemptions:
  gamemodes: [CREATIVE, SPECTATOR]
  permissions: ["anticheat.bypass"]
  worlds: []                   # world names to ignore
```

---

## Project Structure

```
src/main/java/com/yourplugin/anticheat/
├── AntiCheatPlugin.java           # Main plugin class
├── config/
│   └── Config.java                # Config wrapper, hot-reload
├── data/
│   ├── PlayerData.java            # Per-player state
│   ├── PositionBuffer.java        # Rolling position history
│   └── CombatTracker.java
├── packet/
│   └── PacketListener.java        # PacketEvents listener
├── simulation/
│   ├── SimulatorWorld.java        # Block snapshot
│   ├── PhysicsSimulator.java      # Tick prediction
│   └── ReachSimulator.java        # Hitbox ray intersection
├── checks/
│   ├── CheckManager.java
│   ├── movement/
│   │   ├── SpeedCheck.java
│   │   ├── FlightCheck.java
│   │   ├── TimerCheck.java
│   │   ├── NoFallCheck.java
│   │   └── StepCheck.java
│   └── combat/
│       ├── ReachCheck.java
│       ├── KillAuraCheck.java
│       ├── AutoClickerCheck.java
│       ├── AimBotCheck.java
│       └── VelocityCheck.java
├── violation/
│   ├── ViolationManager.java
│   └── ViolationAction.java       # alert / setback / kick / ban
└── alert/
    └── AlertManager.java
```

---

## Maven `pom.xml` Key Dependencies

```xml
<repositories>
  <!-- Paper -->
  <repository>
    <id>papermc</id>
    <url>https://repo.papermc.io/repository/maven-public/</url>
  </repository>
  <!-- PacketEvents -->
  <repository>
    <id>codemc-repo</id>
    <url>https://repo.codemc.io/repository/maven-releases/</url>
  </repository>
</repositories>

<dependencies>
  <!-- Paper API -->
  <dependency>
    <groupId>io.papermc.paper</groupId>
    <artifactId>paper-api</artifactId>
    <version>1.20.4-R0.1-SNAPSHOT</version>
    <scope>provided</scope>
  </dependency>
  <!-- PacketEvents — shade into the plugin jar -->
  <dependency>
    <groupId>com.github.retrooper</groupId>
    <artifactId>packetevents-spigot</artifactId>
    <version>2.3.0</version>
    <scope>compile</scope>
  </dependency>
</dependencies>

<!-- Shade + relocate PacketEvents so it doesn't conflict with other plugins -->
<build>
  <plugins>
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-shade-plugin</artifactId>
      <version>3.5.1</version>
      <executions>
        <execution>
          <phase>package</phase>
          <goals><goal>shade</goal></goals>
          <configuration>
            <relocations>
              <relocation>
                <pattern>com.github.retrooper.packetevents</pattern>
                <shadedPattern>com.yourplugin.anticheat.libs.packetevents</shadedPattern>
              </relocation>
              <relocation>
                <pattern>io.github.retrooper.packetevents</pattern>
                <shadedPattern>com.yourplugin.anticheat.libs.packetevents.impl</shadedPattern>
              </relocation>
            </relocations>
          </configuration>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

---

## Build Order for Claude Code

Feed Claude Code these tasks in order:

1. **Scaffold** — `AntiCheatPlugin.java`, `Config.java`, `PlayerData.java`
2. **Packet pipeline** — `PacketListener.java` (intercept POSITION, USE_ENTITY, ARM_ANIMATION)
3. **SimulatorWorld** — block snapshot + ground detection
4. **PhysicsSimulator** — tick-by-tick velocity prediction
5. **Movement checks** — Speed → Flight → Timer → NoFall → Step
6. **Combat checks** — Reach → KillAura → AutoClicker → AimBot → Velocity
7. **ViolationManager + actions** — VL tracking, decay, action execution
8. **AlertManager + config** — staff alerts, YAML actions, hot-reload `/ac reload`
9. **Testing** — unit tests for PhysicsSimulator, integration tests with MockBukkit

---

## Key Implementation Notes

- **Never call `world.getBlockAt()` off-thread** — always use the `SimulatorWorld` snapshot.
- **Setback positions** must be validated to not be inside blocks before teleporting.
- **Latency compensation** is critical for Reach — without it you will false-flag high-ping players.
- **VL decay** prevents false positives from legitimate lag spikes.
- **Exempt creative/spectator** in every check by reading from `PlayerData.gamemode` (tracked via packets, not `Player#getGameMode()` to avoid sync access).
- Use `PacketType.Play.Client.PLAYER_POSITION` / `PLAYER_POSITION_AND_ROTATION` for movement; `INTERACT_ENTITY` for combat; `ANIMATION` for swing.

---

## PacketEvents API Cheat Sheet

### Plugin bootstrap (`onEnable`)
```java
PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
PacketEvents.getAPI().getSettings()
    .reEncodeByDefault(false)   // don't waste CPU re-encoding packets we don't modify
    .checkForUpdates(false)
    .bStats(false);
PacketEvents.getAPI().load();
PacketEvents.getAPI().getEventManager()
    .registerListener(new PacketListener(playerDataMap));
PacketEvents.getAPI().init();
```

### Listener skeleton
```java
public class PacketListener extends SimplePacketListenerAbstract {

    public PacketListener(Map<UUID, PlayerData> map) {
        // ASYNC priority — runs off Netty I/O thread, never blocks main thread
        super(PacketListenerPriority.NORMAL);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION) {
            WrapperPlayClientPlayerPositionAndRotation wrapper =
                new WrapperPlayClientPlayerPositionAndRotation(event);
            double x = wrapper.getPosition().getX();
            double y = wrapper.getPosition().getY();
            double z = wrapper.getPosition().getZ();
            float yaw   = wrapper.getYaw();
            float pitch = wrapper.getPitch();
            boolean onGround = wrapper.isOnGround();
            // → enqueue to per-player executor
        }

        if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity wrapper =
                new WrapperPlayClientInteractEntity(event);
            if (wrapper.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                int entityId = wrapper.getEntityId();
                // → enqueue combat check
            }
        }

        if (event.getPacketType() == PacketType.Play.Client.ANIMATION) {
            // Swing arm — used for KillAura no-swing detection
            // → record timestamp in PlayerData
        }
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_VELOCITY) {
            WrapperPlayServerEntityVelocity wrapper =
                new WrapperPlayServerEntityVelocity(event);
            if (wrapper.getEntityId() == event.getUser().getEntityId()) {
                // Server sent knockback — store expected velocity in PlayerData
            }
        }
    }
}
```

### Relevant packet types
| Check | Packet |
|---|---|
| Movement / position | `PLAYER_POSITION`, `PLAYER_POSITION_AND_ROTATION`, `PLAYER_ROTATION` |
| Ground-only tick | `PLAYER_FLYING` |
| Attack | `INTERACT_ENTITY` (action = ATTACK) |
| Swing animation | `ANIMATION` |
| Knockback tracking | `ENTITY_VELOCITY` (outbound) |
| Teleport confirmation | `TELEPORT_CONFIRM` |
