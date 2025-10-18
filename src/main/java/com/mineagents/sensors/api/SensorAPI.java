package com.mineagents.sensors.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mineagents.sensors.AgentSensorPlugin;
import com.mineagents.sensors.api.sensors.*;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Sensor API - Enhanced world data for AI agents
 *
 * Provides rich environmental data beyond what mineflayer can access:
 * - Detailed block metadata
 * - Entity AI states
 * - Mob pathfinding data
 * - Weather patterns
 * - Chunk loading states
 * - Item spawn predictions
 */
public class SensorAPI {

    private final AgentSensorPlugin plugin;
    private final Gson gson;

    // Sensor modules
    private final BlockSensor blockSensor;
    private final EntitySensor entitySensor;
    private final MobAISensor mobAISensor;
    private final WeatherSensor weatherSensor;
    private final ChunkSensor chunkSensor;
    private final ItemSensor itemSensor;

    public SensorAPI(AgentSensorPlugin plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().setPrettyPrinting().create();

        // Initialize sensor modules
        this.blockSensor = new BlockSensor();
        this.entitySensor = new EntitySensor();
        this.mobAISensor = new MobAISensor();
        this.weatherSensor = new WeatherSensor();
        this.chunkSensor = new ChunkSensor();
        this.itemSensor = new ItemSensor();
    }

    public void initialize() {
        plugin.getLogger().info("[Sensor API] Initializing enhanced sensors...");

        // Start sensor update tasks
        startSensorTasks();

        plugin.getLogger().info("[Sensor API] All sensors active");
    }

    public void shutdown() {
        plugin.getLogger().info("[Sensor API] Shutting down sensors...");
        // Cleanup tasks
    }

    public void reload() {
        plugin.getLogger().info("[Sensor API] Reloading sensor configuration...");
        shutdown();
        initialize();
    }

    /**
     * Start periodic sensor update tasks
     */
    private void startSensorTasks() {
        // Update sensors every 2 seconds
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                updatePlayerSensors(player);
            }
        }, 0L, 40L); // 40 ticks = 2 seconds
    }

    /**
     * Update sensor data for a specific player/agent
     */
    private void updatePlayerSensors(Player player) {
        // This method will push sensor data to agents via custom packets or events
        // For now, data is available via getSensorData()
    }

    /**
     * Get comprehensive sensor data for a location
     */
    public SensorData getSensorData(Location location, int radius) {
        SensorData data = new SensorData();

        data.location = new LocationData(location);
        data.blocks = blockSensor.getBlockData(location, radius);
        data.entities = entitySensor.getEntitiesNearby(location, radius);
        data.mobAI = mobAISensor.getMobAIStates(location, radius);
        data.weather = weatherSensor.getWeatherData(location.getWorld());
        data.chunks = chunkSensor.getChunkData(location, radius);
        data.items = itemSensor.getNearbyItems(location, radius);

        return data;
    }

    /**
     * Get sensor data for a player
     */
    public SensorData getSensorDataForPlayer(Player player, int radius) {
        return getSensorData(player.getLocation(), radius);
    }

    /**
     * Get sensor data as JSON
     */
    public String getSensorDataJSON(Location location, int radius) {
        SensorData data = getSensorData(location, radius);
        return gson.toJson(data);
    }

    /**
     * Get individual sensor modules
     */
    public BlockSensor getBlockSensor() {
        return blockSensor;
    }

    public EntitySensor getEntitySensor() {
        return entitySensor;
    }

    public MobAISensor getMobAISensor() {
        return mobAISensor;
    }

    public WeatherSensor getWeatherSensor() {
        return weatherSensor;
    }

    public ChunkSensor getChunkSensor() {
        return chunkSensor;
    }

    public ItemSensor getItemSensor() {
        return itemSensor;
    }

    /**
     * Main sensor data container
     */
    public static class SensorData {
        public LocationData location;
        public List<BlockData> blocks;
        public List<EntityData> entities;
        public List<MobAIData> mobAI;
        public WeatherData weather;
        public List<ChunkData> chunks;
        public List<ItemData> items;
    }

    /**
     * Location data
     */
    public static class LocationData {
        public double x, y, z;
        public String world;
        public float yaw, pitch;

        public LocationData(Location loc) {
            this.x = loc.getX();
            this.y = loc.getY();
            this.z = loc.getZ();
            this.world = loc.getWorld().getName();
            this.yaw = loc.getYaw();
            this.pitch = loc.getPitch();
        }
    }

    /**
     * Block data
     */
    public static class BlockData {
        public int x, y, z;
        public String type;
        public Map<String, Object> metadata;
        public int lightLevel;
        public boolean isPassable;
    }

    /**
     * Entity data
     */
    public static class EntityData {
        public String uuid;
        public String type;
        public String name; // ADDED: Entity name field to prevent undefined errors in ML encoder
        public double x, y, z;
        public float yaw, pitch;
        public double health;
        public boolean isHostile;
        public String aiState;
    }

    /**
     * Mob AI data
     */
    public static class MobAIData {
        public String entityUUID;
        public String currentGoal;
        public String targetUUID;
        public List<String> pathfindingNodes;
        public boolean isAggressive;
    }

    /**
     * Weather data
     */
    public static class WeatherData {
        public boolean isRaining;
        public boolean isThundering;
        public int weatherDuration;
        public long worldTime;
        public String timeOfDay;
    }

    /**
     * Chunk data
     */
    public static class ChunkData {
        public int chunkX, chunkZ;
        public boolean isLoaded;
        public int entityCount;
        public int tileEntityCount;
    }

    /**
     * Item data
     */
    public static class ItemData {
        public String uuid;
        public String itemType;
        public int amount;
        public double x, y, z;
        public int ticksLived;
    }
}
