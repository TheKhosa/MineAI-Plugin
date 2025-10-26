package com.mineagents.sensors.npc;

import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_21_R1.entity.CraftPlayer;

import java.util.UUID;

/**
 * NPC Agent Wrapper
 *
 * Wraps a ServerPlayer (NPC) with agent metadata
 * Provides methods for controlling the NPC
 */
public class NPCAgent {

    private final String agentName;      // Hub identifier
    private final String agentType;      // MINING, LUMBERJACK, etc.
    private final ServerPlayer npc;      // NMS player entity
    private final UUID uuid;             // Real Minecraft UUID
    private final String playerName;     // Real player name
    private final long spawnTime;

    // State
    private boolean active = true;

    public NPCAgent(String agentName, String agentType, ServerPlayer npc, UUID uuid, String playerName) {
        this.agentName = agentName;
        this.agentType = agentType;
        this.npc = npc;
        this.uuid = uuid;
        this.playerName = playerName;
        this.spawnTime = System.currentTimeMillis();
    }

    /**
     * Get agent name (hub identifier)
     */
    public String getAgentName() {
        return agentName;
    }

    /**
     * Get agent type
     */
    public String getAgentType() {
        return agentType;
    }

    /**
     * Get NPC entity
     */
    public ServerPlayer getNPC() {
        return npc;
    }

    /**
     * Get Bukkit player wrapper
     */
    public org.bukkit.entity.Player getBukkitPlayer() {
        return npc.getBukkitEntity();
    }

    /**
     * Get UUID
     */
    public UUID getUUID() {
        return uuid;
    }

    /**
     * Get entity UUID (should match getUUID())
     */
    public UUID getEntityUUID() {
        return npc.getUUID();
    }

    /**
     * Get player name
     */
    public String getPlayerName() {
        return playerName;
    }

    /**
     * Get spawn time
     */
    public long getSpawnTime() {
        return spawnTime;
    }

    /**
     * Get uptime in seconds
     */
    public long getUptime() {
        return (System.currentTimeMillis() - spawnTime) / 1000;
    }

    /**
     * Check if active
     */
    public boolean isActive() {
        return active && npc.isAlive();
    }

    /**
     * Set active state
     */
    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * Get current location
     */
    public Location getLocation() {
        return npc.getBukkitEntity().getLocation();
    }

    /**
     * Get health
     */
    public float getHealth() {
        return npc.getHealth();
    }

    /**
     * Get max health
     */
    public float getMaxHealth() {
        return npc.getMaxHealth();
    }

    /**
     * Get food level
     */
    public int getFoodLevel() {
        return npc.getFoodData().getFoodLevel();
    }

    /**
     * Check if dead
     */
    public boolean isDead() {
        return !npc.isAlive();
    }

    /**
     * Teleport NPC to location
     */
    public void teleport(Location location) {
        npc.teleportTo(
            ((org.bukkit.craftbukkit.v1_21_R1.CraftWorld) location.getWorld()).getHandle(),
            location.getX(),
            location.getY(),
            location.getZ()
        );
    }

    /**
     * Move NPC towards location (pathfinding)
     * TODO: Implement actual pathfinding
     */
    public void moveTo(Location location) {
        // For now, just teleport
        // In future, implement proper pathfinding with navigation goals
        teleport(location);
    }

    /**
     * Execute action
     * TODO: Implement action execution
     */
    public void executeAction(String actionType, Object params) {
        // This will be expanded to handle all 216 actions
        // For now, just log
        System.out.println("[NPC] " + agentName + " executing: " + actionType);
    }

    @Override
    public String toString() {
        return "NPCAgent{" +
            "agentName='" + agentName + '\'' +
            ", playerName='" + playerName + '\'' +
            ", type='" + agentType + '\'' +
            ", uuid=" + uuid +
            ", alive=" + isActive() +
            ", uptime=" + getUptime() + "s" +
            '}';
    }
}
