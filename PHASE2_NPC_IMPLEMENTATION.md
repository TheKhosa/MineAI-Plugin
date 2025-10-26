# Phase 2: NPC Implementation Guide

## Current Status (Phase 1 Complete)

✅ **Completed:**
- Plugin compiled and deployed without NMS dependencies
- WebSocket server running on port 3002
- Plugin successfully loaded in Minecraft server
- Ready for Phase 2 NPC implementation

## Phase 2 Overview

Phase 2 will add server-side NPC (Non-Player Character) functionality using Minecraft's NMS (Net Minecraft Server) classes. This allows spawning realistic player NPCs that can be controlled by the AI hub.

## Why NMS is Required

NMS classes provide direct access to Minecraft's internal server code, allowing us to:
- Create realistic player entities that look exactly like real players
- Spawn NPCs with real Minecraft UUIDs and player skins
- Control NPC movement, actions, and interactions at a low level
- Avoid the overhead of mineflayer client connections

## Implementation Steps

### Step 1: Install Build Tools on VPS

BuildTools generates the Spigot JAR and installs NMS classes into Maven's local repository.

```bash
ssh debian@vps-99365978.vps.ovh.net
cd /home/debian
wget https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar
java -jar BuildTools.jar --rev 1.21.1
```

**Time Required:** 10-15 minutes (downloads dependencies, compiles Spigot)

**Expected Output:**
- `spigot-1.21.1.jar` in current directory
- NMS classes installed to `~/.m2/repository/org/spigotmc/spigot/1.21.1-R0.1-SNAPSHOT/`

### Step 2: Restore NPC Implementation

The NPC classes were moved to `phase2_npc_implementation/` during Phase 1. Restore them:

```bash
# On VPS:
cd /home/debian/MineAI-Plugin
mv phase2_npc_implementation/npc src/main/java/com/mineagents/sensors/
mv phase2_npc_implementation/uuid src/main/java/com/mineagents/sensors/
```

### Step 3: Uncomment NPC Code

Restore the commented-out code in:

**AgentSensorPlugin.java:**
```java
// Change:
// PHASE 2: import com.mineagents.sensors.npc.NPCManager;
// To:
import com.mineagents.sensors.npc.NPCManager;

// Restore npcManager field, initialization, and shutdown code
```

**SensorWebSocketServer.java:**
```java
// Restore:
// - NPCManager imports
// - npcManager field
// - setNPCManager() method
// - handleSpawnAgent(), handleAction(), handleRemoveAgent() methods
// - sendSpawnConfirm() method
```

### Step 4: Build Plugin with NMS Support

```bash
cd /home/debian/MineAI-Plugin
mvn clean package -Dbuild.number=PHASE2-NPC
```

**Expected:** Build SUCCESS (no compilation errors)

### Step 5: Deploy and Test

```bash
# Deploy plugin
cp target/AgentSensorPlugin-*.jar /home/debian/mc/plugins/

# Restart Minecraft server
pkill -f "spigot.*jar"
cd /home/debian/mc
screen -dmS minecraft java -Xmx4G -Xms2G -jar spigot-*.jar nogui

# Wait for server to load (~1 minute)
# Check logs
tail -f logs/latest.log
# Look for: "[AgentSensorPlugin] [NPC Manager] Initialized and ready to spawn agents"
```

### Step 6: Start V2 Hub

The hub will connect to the plugin and request NPC spawns:

```bash
cd /home/debian/minerl-hub
screen -dmS hub node core/hub.js
```

**Expected Hub Behavior:**
1. Connect to plugin WebSocket on port 3002
2. Authenticate with token
3. Auto-spawn 10 initial agents
4. Request NPC spawn for each agent
5. Receive spawn confirmations with real player names

### Step 7: Verify In-Game

1. Connect to server: `vps-99365978.vps.ovh.net:25565`
2. Run command: `/agentsensor status`

**Expected Output:**
```
=== Agent Sensor Plugin Status ===
Version: 1.0.PHASE2-NPC
WebSocket Server: Running on port 3002
Connected Clients: 1
NPC Agents: 10
UUID Cache: 50 valid, 12 invalid
```

3. Look around spawn - you should see 10 NPCs with real player names (Steve, Alex, etc.)
4. NPCs should have real player skins from Mojang

## NPC Implementation Details

### UUIDFetcher.java
- Fetches real Minecraft UUIDs from GitHub repository (chunks 0001-0675)
- Validates UUIDs via Mojang session server API
- Caches valid/invalid UUIDs to avoid repeated API calls
- Returns both UUID and player name

**Location:** `src/main/java/com/mineagents/sensors/uuid/UUIDFetcher.java`

### NPCManager.java
- Manages NPC lifecycle (spawn, update, remove)
- Uses NMS classes to create ServerPlayer entities
- Sends player info packets to make NPCs appear as real players
- Handles NPC actions (movement, block breaking, etc.)

**Key NMS Classes Used:**
- `net.minecraft.server.MinecraftServer`
- `net.minecraft.server.level.ServerLevel`
- `net.minecraft.server.level.ServerPlayer`
- `com.mojang.authlib.GameProfile`
- `net.minecraft.network.protocol.game.*` (packets)

**Location:** `src/main/java/com/mineagents/sensors/npc/NPCManager.java`

### NPCAgent.java
- Wraps ServerPlayer with agent metadata
- Stores agent name, type, UUID, player name
- Provides action execution methods
- Ready for 216-action ML integration

**Location:** `src/main/java/com/mineagents/sensors/npc/NPCAgent.java`

## WebSocket Protocol

### Hub → Plugin Messages

**Spawn Agent:**
```json
{
  "type": "spawn_agent",
  "agentName": "MIN_A3F2_G1",
  "agentType": "MINING",
  "location": {
    "world": "world",
    "x": 0, "y": 64, "z": 0
  }
}
```

**Send Action:**
```json
{
  "type": "action",
  "agentName": "MIN_A3F2_G1",
  "action": "move_forward",
  "params": { "distance": 5 }
}
```

**Remove Agent:**
```json
{
  "type": "remove_agent",
  "agentName": "MIN_A3F2_G1"
}
```

### Plugin → Hub Messages

**Spawn Confirmation:**
```json
{
  "type": "spawn_confirm",
  "agentName": "MIN_A3F2_G1",
  "entityUUID": "minecraft-entity-uuid",
  "location": { "world": "world", "x": 0, "y": 64, "z": 0 },
  "playerInfo": {
    "realName": "Steve",
    "uuid": "real-minecraft-uuid"
  }
}
```

## Troubleshooting

### Build Fails with NMS Errors
```bash
# Verify BuildTools completed
ls ~/.m2/repository/org/spigotmc/spigot/
# Should show 1.21.1-R0.1-SNAPSHOT/

# Clean and rebuild
mvn clean
mvn package -Dbuild.number=PHASE2-NPC
```

### NPCs Don't Spawn
```bash
# Check plugin logs
grep -i "NPC\|UUID" /home/debian/mc/logs/latest.log

# Check for errors
grep -i "error\|exception" /home/debian/mc/logs/latest.log

# Verify UUID fetcher
# In-game: /agentsensor status
# Should show UUID cache stats
```

### NPCs Appear as "NPC" Instead of Real Names
- UUID fetcher may not have found valid UUIDs yet
- Check chunk files are accessible: https://github.com/TheKhosa/MC-UUID
- Verify Mojang API is reachable from VPS

### Hub Not Connecting
```bash
# Check hub logs
tail -f /home/debian/minerl-hub/hub.log

# Verify WebSocket server
netstat -tulpn | grep 3002
# Should show Java process listening

# Test connection
curl -i -N -H "Connection: Upgrade" -H "Upgrade: websocket" \
  http://localhost:3002
```

## File Structure (Phase 2)

```
AgentSensorPlugin/
├── src/main/java/com/mineagents/sensors/
│   ├── AgentSensorPlugin.java          [Modified: NPC manager integration]
│   ├── npc/
│   │   ├── NPCManager.java             [Restored from phase2/]
│   │   └── NPCAgent.java               [Restored from phase2/]
│   ├── uuid/
│   │   └── UUIDFetcher.java            [Restored from phase2/]
│   └── websocket/
│       └── SensorWebSocketServer.java  [Modified: NPC message handlers]
├── phase2_npc_implementation/          [Backup location]
│   ├── npc/
│   └── uuid/
└── V2_NPC_IMPLEMENTATION.md           [Original design doc]
```

## Success Criteria

✅ Plugin builds without errors
✅ NMS classes resolved
✅ NPCs spawn with real player names
✅ NPCs visible in-game with skins
✅ Hub can spawn/control/remove NPCs
✅ WebSocket messages working bidirectionally
✅ `/agentsensor status` shows NPC count

## Next Steps After Phase 2

Once NPCs are spawning successfully:

1. **Implement 216 ML Actions** - Connect NPCAgent.executeAction() to all 216 action types
2. **Sensor Data Streaming** - Send NPC observations to hub via WebSocket
3. **ML Integration** - Connect hub's ML trainer to NPC actions
4. **Collaborative Behaviors** - Test multi-NPC coordination
5. **Performance Tuning** - Optimize for 1000+ NPCs

## Estimated Timeline

- BuildTools install: 15 minutes
- Code restoration: 10 minutes
- Build and deploy: 5 minutes
- Testing and verification: 15 minutes

**Total: ~45 minutes for Phase 2 implementation**
