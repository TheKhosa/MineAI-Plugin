package com.mineagents.sensors.websocket;

import com.google.gson.Gson;
import com.mineagents.sensors.AgentSensorPlugin;
import com.mineagents.sensors.api.SensorAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Periodic broadcaster for sensor data
 * Sends sensor updates to WebSocket clients every N ticks
 */
public class SensorBroadcaster extends BukkitRunnable {

    private final AgentSensorPlugin plugin;
    private final WebSocketServer webSocketServer;
    private final SensorAPI sensorAPI;
    private final Gson gson;
    private final int sensorRadius;
    private final int updateInterval;

    public SensorBroadcaster(AgentSensorPlugin plugin, WebSocketServer webSocketServer, int sensorRadius, int updateInterval) {
        this.plugin = plugin;
        this.webSocketServer = webSocketServer;
        this.sensorAPI = plugin.getSensorAPI();
        this.gson = new Gson();
        this.sensorRadius = sensorRadius;
        this.updateInterval = updateInterval;
    }

    @Override
    public void run() {
        try {
            // Only broadcast if there are connected clients
            if (webSocketServer.getRegisteredBotCount() == 0) {
                return;
            }

            // Get all registered bot names from WebSocket
            for (String botName : webSocketServer.getRegisteredBotNames()) {
                // Find player with this name
                Player player = Bukkit.getPlayer(botName);
                if (player != null && player.isOnline()) {
                    // Get sensor data for this bot
                    Location loc = player.getLocation();
                    Map<String, Object> sensorData = collectSensorData(loc);

                    // Broadcast to WebSocket clients
                    webSocketServer.broadcastSensorData(botName, sensorData);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[SensorBroadcaster] Error during broadcast", e);
        }
    }

    /**
     * Collect all sensor data for a location
     */
    private Map<String, Object> collectSensorData(Location location) {
        Map<String, Object> data = new HashMap<>();

        // Location data
        Map<String, Object> loc = new HashMap<>();
        loc.put("x", location.getX());
        loc.put("y", location.getY());
        loc.put("z", location.getZ());
        loc.put("world", location.getWorld().getName());
        loc.put("yaw", location.getYaw());
        loc.put("pitch", location.getPitch());
        data.put("location", loc);

        // Block sensor data
        data.put("blocks", sensorAPI.getBlockSensor().getBlockData(location, sensorRadius));

        // Entity sensor data
        data.put("entities", sensorAPI.getEntitySensor().getEntitiesNearby(location, sensorRadius));

        // Mob AI sensor data
        data.put("mobAI", sensorAPI.getMobAISensor().getMobAIStates(location, sensorRadius));

        // Weather sensor data
        data.put("weather", sensorAPI.getWeatherSensor().getWeatherData(location.getWorld()));

        // Chunk sensor data
        data.put("chunks", sensorAPI.getChunkSensor().getChunkData(location, sensorRadius));

        // Item sensor data
        data.put("items", sensorAPI.getItemSensor().getNearbyItems(location, sensorRadius));

        return data;
    }

    /**
     * Start the broadcaster with configured interval
     */
    public void start() {
        // Run async to avoid blocking main thread
        this.runTaskTimerAsynchronously(plugin, 0L, updateInterval);
        plugin.getLogger().info("[SensorBroadcaster] Started with " + updateInterval + " tick interval");
    }
}
