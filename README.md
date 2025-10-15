# AgentSensorPlugin

**Self-updating Minecraft Spigot plugin with enhanced sensor API for AI agents**

## Features

### 🔄 Auto-Update System
- Checks TeamCity CI for latest successful builds
- Downloads and installs updates automatically
- Removes old plugin versions on startup
- Configurable update intervals (default: 30 minutes)
- Manual update command: `/agentsensor update`

### 📡 Enhanced Sensor API
Provides rich environmental data beyond standard Minecraft/mineflayer:

1. **Block Sensor**
   - Block metadata (hardness, flammability, etc.)
   - Light levels
   - Passability data
   - Block state information (chests, furnaces, etc.)

2. **Entity Sensor**
   - All nearby entities with detailed states
   - Health tracking
   - Hostile/passive classification
   - AI state (attacking, moving, idle)

3. **Mob AI Sensor**
   - Current AI goals
   - Target tracking
   - Pathfinding nodes
   - Aggression states

4. **Weather Sensor**
   - Rain/thunder status
   - Weather duration
   - Time of day
   - World time

5. **Chunk Sensor**
   - Chunk loading states
   - Entity counts per chunk
   - Tile entity tracking

6. **Item Sensor**
   - Dropped item tracking
   - Item age (ticks lived)
   - Location tracking

## Installation

### Prerequisites
- Minecraft Server (Spigot/Paper 1.21+)
- Java 17+
- Maven (for building)
- TeamCity CI server (for auto-updates)

### Quick Start

1. **Build the plugin**:
   ```bash
   cd AgentSensorPlugin
   mvn clean package
   ```

2. **Install on server**:
   ```bash
   cp target/AgentSensorPlugin-*.jar /path/to/minecraft/server/plugins/
   ```

3. **Start Minecraft server**:
   The plugin will auto-initialize and check for updates

## Configuration

### TeamCity Settings

Edit `AgentSensorPlugin.java` to configure TeamCity:

```java
private static final String TEAMCITY_URL = "http://145.239.253.161:8111";
private static final String TEAMCITY_USERNAME = "AIAgent";
private static final String TEAMCITY_PASSWORD = "D#hp^uC5RuJcn%";
private static final String BUILD_TYPE_ID = "AgentSensorPlugin";
```

### Plugin Configuration

`plugins/AgentSensorPlugin/config.yml`:

```yaml
auto-update:
  enabled: true
  check-interval: 1800  # seconds (30 minutes)

sensors:
  block-radius: 16
  entity-radius: 32
  update-interval: 40  # ticks (2 seconds)
```

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/agentsensor status` | Show plugin status | `agentsensor.admin` |
| `/agentsensor update` | Check for updates now | `agentsensor.admin` |
| `/agentsensor reload` | Reload configuration | `agentsensor.admin` |
| `/agentsensor sensors` | List available sensors | `agentsensor.admin` |

## API Usage

### For Plugin Developers

```java
// Get sensor API instance
SensorAPI api = AgentSensorPlugin.getInstance().getSensorAPI();

// Get sensor data for a location
Location loc = player.getLocation();
SensorAPI.SensorData data = api.getSensorData(loc, 16);

// Get as JSON
String json = api.getSensorDataJSON(loc, 16);
```

### For External Tools (Node.js agents)

The plugin exposes sensor data that can be accessed via:

1. **Custom packets** (future implementation)
2. **REST API** (future implementation)
3. **WebSocket stream** (future implementation)

## Architecture

```
AgentSensorPlugin/
├── src/main/java/com/mineagents/sensors/
│   ├── AgentSensorPlugin.java         # Main plugin class
│   ├── updater/
│   │   └── TeamCityUpdater.java       # Auto-update system
│   └── api/
│       ├── SensorAPI.java             # Main sensor API
│       └── sensors/
│           ├── BlockSensor.java       # Block data
│           ├── EntitySensor.java      # Entity tracking
│           ├── MobAISensor.java       # Mob AI states
│           ├── WeatherSensor.java     # Weather data
│           ├── ChunkSensor.java       # Chunk states
│           └── ItemSensor.java        # Item tracking
├── pom.xml                            # Maven build config
└── teamcity-build.xml                 # TeamCity config template
```

## Auto-Update Flow

```
1. Plugin starts → Clean up old versions
2. Schedule update checker (every 30 min)
3. Check TeamCity REST API for latest build
4. Compare version numbers
5. If newer: Download artifact
6. Install new JAR to plugins folder
7. Mark old JAR for deletion
8. Reload server plugins
```

## TeamCity Integration

### Automated Build Configuration (Recommended)

Use the included Node.js script to automatically configure TeamCity via REST API:

```bash
cd AgentSensorPlugin
node teamcity-setup.js
```

This script will:
1. Create the MineRL project in TeamCity
2. Configure the AgentSensorPlugin build
3. Set up Maven build steps (clean, compile, package)
4. Configure artifact paths
5. Set up VCS and scheduled triggers

**Before running**: Update the Git URL in `teamcity-setup.js` line 11:
```javascript
gitUrl: 'YOUR_GIT_REPOSITORY_URL', // TODO: Update with actual Git URL
```

### Manual Build Configuration

See `TEAMCITY_SETUP.md` for detailed manual setup instructions.

**Quick summary**:
1. Create project in TeamCity
2. Configure Maven build steps (clean, compile, package)
3. Set artifact path: `target/AgentSensorPlugin-*.jar => AgentSensorPlugin.jar`
4. Plugin will auto-detect and download new builds

### REST API

Plugin uses TeamCity REST API:

```
GET /app/rest/builds?locator=buildType:AgentSensorPlugin,status:SUCCESS,count:1
GET /app/rest/builds/id:{buildId}/artifacts/content/AgentSensorPlugin.jar
```

## Development

### Building

```bash
mvn clean package
```

Output: `target/AgentSensorPlugin-{version}.jar`

### Testing Locally

```bash
# Build
mvn package

# Copy to test server
cp target/AgentSensorPlugin-*.jar ~/minecraft-server/plugins/

# Restart server or reload
# Server console: reload confirm
```

### Debugging

Enable debug logging in `config.yml`:

```yaml
debug: true
log-sensor-data: true
log-updates: true
```

## Integration with Node.js Agents

Future integration points:

1. **WebSocket Server** (planned)
   - Real-time sensor data streaming
   - Port: 3002 (configurable)
   - JSON format

2. **HTTP REST API** (planned)
   - GET `/sensors/{playername}?radius=16`
   - Returns JSON sensor data

3. **Custom Packets** (planned)
   - Binary protocol for low-latency data
   - Mineflayer plugin integration

## Sensor Data Format

```json
{
  "location": {
    "x": 100.5,
    "y": 64.0,
    "z": -200.3,
    "world": "world",
    "yaw": 90.0,
    "pitch": 0.0
  },
  "blocks": [
    {
      "x": 100,
      "y": 64,
      "z": -200,
      "type": "STONE",
      "lightLevel": 15,
      "isPassable": false,
      "metadata": {
        "hardness": 1.5,
        "isSolid": true
      }
    }
  ],
  "entities": [
    {
      "uuid": "...",
      "type": "ZOMBIE",
      "x": 105.0,
      "y": 64.0,
      "z": -205.0,
      "health": 20.0,
      "isHostile": true,
      "aiState": "ATTACKING"
    }
  ],
  "weather": {
    "isRaining": false,
    "isThundering": false,
    "timeOfDay": "DAY"
  }
}
```

## Troubleshooting

### Plugin Won't Load
- Check Java version (requires 17+)
- Verify Spigot API version (1.21+)
- Check console for error messages

### Auto-Update Not Working
- Verify TeamCity server is accessible
- Check credentials in plugin code
- Verify Build Type ID matches TeamCity configuration
- Check `/agentsensor status` output

### Old Versions Not Deleted
- Plugin marks old JARs with `deleteOnExit()`
- Restart server to complete cleanup
- Check file permissions on plugins folder

## License

MIT License - See LICENSE file

## Contributing

1. Fork the repository
2. Create feature branch
3. Commit changes
4. Push to branch
5. Create Pull Request

## Support

- GitHub Issues: [Your Repository]
- TeamCity: http://145.239.253.161:8111/
- Discord: [Your Discord Server]

## Roadmap

- [ ] WebSocket sensor streaming
- [ ] REST API endpoints
- [ ] Mineflayer plugin integration
- [ ] Advanced pathfinding data
- [ ] Redstone circuit states
- [ ] Advanced mob behavior prediction
- [ ] Player action tracking
- [ ] Custom event system
