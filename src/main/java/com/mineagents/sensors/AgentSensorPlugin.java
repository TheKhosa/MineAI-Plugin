package com.mineagents.sensors;

import com.mineagents.sensors.api.SensorAPI;
// PHASE 2 (Option 2): import com.mineagents.sensors.npc.NPCManager;
import com.mineagents.sensors.tick.TickSynchronizer;
import com.mineagents.sensors.updater.TeamCityUpdater;
import com.mineagents.sensors.websocket.SensorBroadcaster;
import com.mineagents.sensors.websocket.SensorWebSocketServer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;
import java.util.logging.Level;

/**
 * AgentSensorPlugin - Enhanced sensor API for AI agents
 *
 * Features:
 * - Auto-update from TeamCity CI
 * - Enhanced world sensors for agents
 * - Old version cleanup
 * - Automatic plugin reload
 * - WebSocket server for real-time sensor streaming
 */
public class AgentSensorPlugin extends JavaPlugin {

    private static AgentSensorPlugin instance;
    private SensorAPI sensorAPI;
    private TeamCityUpdater updater;
    private SensorWebSocketServer webSocketServer;
    private SensorBroadcaster sensorBroadcaster;
    private TickSynchronizer tickSynchronizer;
    // PHASE 2 (Option 2): private NPCManager npcManager;
    private boolean updateCheckEnabled = true;

    // TeamCity Configuration
    private static final String TEAMCITY_URL = "http://145.239.253.161:8111";
    private static final String TEAMCITY_USERNAME = "AIAgent";
    private static final String TEAMCITY_PASSWORD = "D#hp^uC5RuJcn%";
    private static final String BUILD_TYPE_ID = "AgentSensorPlugin"; // Will be configured in TeamCity

    // WebSocket Configuration
    private static final int WEBSOCKET_PORT = 3002;
    private static final String WEBSOCKET_AUTH_TOKEN = "mineagent-sensor-2024";
    private static final int SENSOR_RADIUS = 16; // blocks (reduced from 32 to prevent stack overflow - was 274k blocks!)
    private static final int SENSOR_UPDATE_INTERVAL = 40; // ticks (2 seconds)

    @Override
    public void onEnable() {
        instance = this;

        getLogger().info("==============================================");
        getLogger().info("  Agent Sensor Plugin - Starting");
        getLogger().info("  Version: " + getDescription().getVersion());
        getLogger().info("==============================================");

        // Save default config
        saveDefaultConfig();

        // Clean up old plugin versions
        cleanupOldVersions();

        // Initialize Sensor API
        sensorAPI = new SensorAPI(this);
        sensorAPI.initialize();

        // Initialize TeamCity Updater
        updater = new TeamCityUpdater(
            this,
            TEAMCITY_URL,
            TEAMCITY_USERNAME,
            TEAMCITY_PASSWORD,
            BUILD_TYPE_ID
        );

        // Check for updates on startup (async)
        if (updateCheckEnabled) {
            Bukkit.getScheduler().runTaskLaterAsynchronously(this, () -> {
                getLogger().info("Checking for updates from TeamCity...");
                updater.checkAndUpdate();
            }, 100L); // Wait 5 seconds after startup
        }

        // Schedule periodic update checks (every 30 minutes)
        if (updateCheckEnabled) {
            Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
                getLogger().info("[Auto-Update] Checking for new builds...");
                updater.checkAndUpdate();
            }, 36000L, 36000L); // 30 minutes = 36000 ticks
        }

        // Initialize WebSocket server
        try {
            webSocketServer = new SensorWebSocketServer(this, WEBSOCKET_PORT, WEBSOCKET_AUTH_TOKEN);
            webSocketServer.start();
            getLogger().info("[WebSocket] Server started on port " + WEBSOCKET_PORT);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "[WebSocket] Failed to start server", e);
        }

        // PHASE 2 (Option 2): Initialize NPC Manager
        // if (webSocketServer != null) {
        //     npcManager = new NPCManager(this);
        //     webSocketServer.setNPCManager(npcManager);
        //     getLogger().info("[NPC Manager] Initialized and ready to spawn agents");
        // }

        // Start sensor broadcaster
        if (webSocketServer != null) {
            sensorBroadcaster = new SensorBroadcaster(this, webSocketServer, SENSOR_RADIUS, SENSOR_UPDATE_INTERVAL);
            sensorBroadcaster.start();
            getLogger().info("[SensorBroadcaster] Started with " + SENSOR_RADIUS + " block radius");
        }

        // Initialize tick synchronizer for ML training
        if (webSocketServer != null) {
            tickSynchronizer = new TickSynchronizer(this, webSocketServer);
            tickSynchronizer.start();
            getLogger().info("[TickSync] Tick synchronization enabled for ML training");
        }

        getLogger().info("Agent Sensor Plugin enabled successfully!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Agent Sensor Plugin disabled");

        // Shutdown tick synchronizer
        if (tickSynchronizer != null) {
            try {
                tickSynchronizer.setEnabled(false);
                getLogger().info("[TickSync] Stopped");
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "[TickSync] Error during shutdown", e);
            }
        }

        // Shutdown sensor broadcaster
        if (sensorBroadcaster != null) {
            try {
                sensorBroadcaster.cancel();
                getLogger().info("[SensorBroadcaster] Stopped");
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "[SensorBroadcaster] Error during shutdown", e);
            }
        }

        // PHASE 2 (Option 2): Shutdown NPC Manager
        // if (npcManager != null) {
        //     try {
        //         npcManager.shutdown();
        //         getLogger().info("[NPC Manager] Shutdown complete");
        //     } catch (Exception e) {
        //         getLogger().log(Level.WARNING, "[NPC Manager] Error during shutdown", e);
        //     }
        // }

        // Shutdown WebSocket server
        if (webSocketServer != null) {
            try {
                webSocketServer.shutdown();
                getLogger().info("[WebSocket] Server stopped");
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "[WebSocket] Error during shutdown", e);
            }
        }

        if (sensorAPI != null) {
            sensorAPI.shutdown();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("agentsensor")) {
            if (!sender.hasPermission("agentsensor.admin")) {
                sender.sendMessage("§cYou don't have permission to use this command!");
                return true;
            }

            if (args.length == 0) {
                sender.sendMessage("§6=== Agent Sensor Plugin ===");
                sender.sendMessage("§e/agentsensor reload §7- Reload plugin configuration");
                sender.sendMessage("§e/agentsensor update §7- Check for updates from TeamCity");
                sender.sendMessage("§e/agentsensor status §7- Show plugin status");
                sender.sendMessage("§e/agentsensor sensors §7- List available sensors");
                return true;
            }

            switch (args[0].toLowerCase()) {
                case "reload":
                    reloadConfig();
                    sensorAPI.reload();
                    sender.sendMessage("§aPlugin configuration reloaded!");
                    return true;

                case "update":
                    sender.sendMessage("§eChecking for updates from TeamCity...");
                    Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                        boolean updated = updater.checkAndUpdate();
                        if (updated) {
                            sender.sendMessage("§aUpdate found and downloaded! Server will reload plugin in 5 seconds...");
                        } else {
                            sender.sendMessage("§aNo updates available. Plugin is up to date!");
                        }
                    });
                    return true;

                case "status":
                    sender.sendMessage("§6=== Agent Sensor Plugin Status ===");
                    sender.sendMessage("§eVersion: §f" + getDescription().getVersion());
                    sender.sendMessage("§eTeamCity URL: §f" + TEAMCITY_URL);
                    sender.sendMessage("§eBuild Type: §f" + BUILD_TYPE_ID);
                    sender.sendMessage("§eAuto-Update: §f" + (updateCheckEnabled ? "Enabled" : "Disabled"));
                    sender.sendMessage("§eSensor API: §f" + (sensorAPI != null ? "Active" : "Inactive"));
                    sender.sendMessage("§eWebSocket Server: §f" + (webSocketServer != null ? "Running on port " + WEBSOCKET_PORT : "Stopped"));
                    if (webSocketServer != null) {
                        sender.sendMessage("§eConnected Clients: §f" + webSocketServer.getAuthenticatedClientCount());
                        sender.sendMessage("§eRegistered Bots: §f" + webSocketServer.getRegisteredBotCount());
                    }
                    // PHASE 2 (Option 2): NPC status
                    // if (npcManager != null) {
                    //     sender.sendMessage("§eNPC Agents: §f" + npcManager.getAgentCount());
                    //     Map<String, Integer> uuidStats = npcManager.getUUIDStats();
                    //     sender.sendMessage("§eUUID Cache: §f" + uuidStats.get("validUUIDs") + " valid, " +
                    //         uuidStats.get("invalidUUIDs") + " invalid");
                    // }
                    return true;

                case "sensors":
                    sender.sendMessage("§6=== Available Sensors ===");
                    sender.sendMessage("§e- Enhanced Block Data");
                    sender.sendMessage("§e- Entity Tracking");
                    sender.sendMessage("§e- Mob AI State");
                    sender.sendMessage("§e- Weather & Time");
                    sender.sendMessage("§e- Chunk Loading Status");
                    sender.sendMessage("§e- Player Proximity");
                    sender.sendMessage("§e- Item Spawn Tracking");
                    return true;

                default:
                    sender.sendMessage("§cUnknown subcommand! Use /agentsensor for help.");
                    return true;
            }
        }

        return false;
    }

    /**
     * Clean up old versions of the plugin
     */
    private void cleanupOldVersions() {
        File pluginsFolder = getDataFolder().getParentFile();
        File[] plugins = pluginsFolder.listFiles((dir, name) ->
            name.startsWith("AgentSensorPlugin") && name.endsWith(".jar") && !name.equals(getFile().getName())
        );

        if (plugins != null && plugins.length > 0) {
            getLogger().info("Found " + plugins.length + " old plugin version(s) to remove");

            for (File oldPlugin : plugins) {
                if (oldPlugin.delete()) {
                    getLogger().info("Removed old version: " + oldPlugin.getName());
                } else {
                    getLogger().warning("Failed to remove old version: " + oldPlugin.getName());
                    // Mark for deletion on next startup
                    oldPlugin.deleteOnExit();
                }
            }
        }
    }

    /**
     * Get plugin instance
     */
    public static AgentSensorPlugin getInstance() {
        return instance;
    }

    /**
     * Get Sensor API
     */
    public SensorAPI getSensorAPI() {
        return sensorAPI;
    }

    /**
     * Get TeamCity Updater
     */
    public TeamCityUpdater getUpdater() {
        return updater;
    }
}
