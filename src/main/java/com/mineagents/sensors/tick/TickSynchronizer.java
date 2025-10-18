package com.mineagents.sensors.tick;

import com.mineagents.sensors.AgentSensorPlugin;
import com.mineagents.sensors.websocket.SensorBroadcaster;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tick Synchronizer
 *
 * Broadcasts server tick events to Node.js ML agents for perfect
 * synchronization between game state and AI decision-making.
 *
 * Features:
 * - Tick-perfect AI steps (1 step = 1 server tick = 50ms)
 * - Synchronized model saving/loading
 * - Evolutionary checkpoints every N ticks
 * - Performance metrics tracking
 */
public class TickSynchronizer {
    private final AgentSensorPlugin plugin;
    private final SensorBroadcaster broadcaster;
    private final AtomicLong currentTick;
    private final Map<String, Long> agentLastAction;
    private boolean enabled;

    // Configuration
    private int broadcastInterval = 1;  // Broadcast every N ticks (1 = every tick, 20 = every second)
    private int evolutionInterval = 12000;  // Save/evolve every 10 minutes (12000 ticks)
    private int checkpointInterval = 1200;  // Checkpoint every minute (1200 ticks)

    // Statistics
    private long ticksProcessed = 0;
    private long lastEvolutionTick = 0;
    private long lastCheckpointTick = 0;

    public TickSynchronizer(AgentSensorPlugin plugin, SensorBroadcaster broadcaster) {
        this.plugin = plugin;
        this.broadcaster = broadcaster;
        this.currentTick = new AtomicLong(0);
        this.agentLastAction = new HashMap<>();
        this.enabled = true;
    }

    /**
     * Start the tick synchronization system
     */
    public void start() {
        plugin.getLogger().info("[TickSync] Starting tick synchronization system");
        plugin.getLogger().info("[TickSync] Broadcast interval: " + broadcastInterval + " ticks");
        plugin.getLogger().info("[TickSync] Evolution interval: " + evolutionInterval + " ticks");
        plugin.getLogger().info("[TickSync] Checkpoint interval: " + checkpointInterval + " ticks");

        // Run every server tick (50ms)
        new BukkitRunnable() {
            @Override
            public void run() {
                if (enabled) {
                    processTick();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);  // 0 delay, run every 1 tick
    }

    /**
     * Process a single server tick
     */
    private void processTick() {
        long tick = currentTick.incrementAndGet();
        ticksProcessed++;

        // Broadcast tick event to Node.js ML agents
        if (tick % broadcastInterval == 0) {
            broadcastTickEvent(tick);
        }

        // Trigger checkpoint save
        if (tick % checkpointInterval == 0) {
            broadcastCheckpointEvent(tick);
            lastCheckpointTick = tick;
        }

        // Trigger evolution event
        if (tick % evolutionInterval == 0) {
            broadcastEvolutionEvent(tick);
            lastEvolutionTick = tick;
        }
    }

    /**
     * Broadcast tick event to all connected agents
     */
    private void broadcastTickEvent(long tick) {
        JSONObject message = new JSONObject();
        message.put("type", "server_tick");
        message.put("tick", tick);
        message.put("timestamp", System.currentTimeMillis());
        message.put("tps", Bukkit.getTPS()[0]);  // Current TPS
        message.put("onlinePlayers", Bukkit.getOnlinePlayers().size());

        broadcaster.broadcast(message.toString());
    }

    /**
     * Broadcast checkpoint event (agents should save models)
     */
    private void broadcastCheckpointEvent(long tick) {
        JSONObject message = new JSONObject();
        message.put("type", "checkpoint");
        message.put("tick", tick);
        message.put("timestamp", System.currentTimeMillis());
        message.put("ticksSinceLastCheckpoint", tick - lastCheckpointTick);

        broadcaster.broadcast(message.toString());
        plugin.getLogger().info("[TickSync] Checkpoint triggered at tick " + tick);
    }

    /**
     * Broadcast evolution event (agents should evolve/select parents)
     */
    private void broadcastEvolutionEvent(long tick) {
        JSONObject message = new JSONObject();
        message.put("type", "evolution");
        message.put("tick", tick);
        message.put("timestamp", System.currentTimeMillis());
        message.put("ticksSinceLastEvolution", tick - lastEvolutionTick);

        broadcaster.broadcast(message.toString());
        plugin.getLogger().info("[TickSync] Evolution triggered at tick " + tick);
    }

    /**
     * Record agent action execution
     */
    public void recordAgentAction(String agentName, long tick) {
        agentLastAction.put(agentName, tick);
    }

    /**
     * Get current server tick
     */
    public long getCurrentTick() {
        return currentTick.get();
    }

    /**
     * Get ticks since agent last acted
     */
    public long getTicksSinceLastAction(String agentName) {
        Long lastAction = agentLastAction.get(agentName);
        if (lastAction == null) {
            return -1;
        }
        return currentTick.get() - lastAction;
    }

    /**
     * Get synchronization statistics
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("currentTick", currentTick.get());
        stats.put("ticksProcessed", ticksProcessed);
        stats.put("lastEvolutionTick", lastEvolutionTick);
        stats.put("lastCheckpointTick", lastCheckpointTick);
        stats.put("activeAgents", agentLastAction.size());
        stats.put("tps", Bukkit.getTPS()[0]);
        return stats;
    }

    /**
     * Enable/disable tick synchronization
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        plugin.getLogger().info("[TickSync] " + (enabled ? "Enabled" : "Disabled"));
    }

    /**
     * Set broadcast interval (how often to send tick events)
     */
    public void setBroadcastInterval(int ticks) {
        this.broadcastInterval = Math.max(1, ticks);
        plugin.getLogger().info("[TickSync] Broadcast interval set to " + broadcastInterval + " ticks");
    }

    /**
     * Set evolution interval (how often to trigger evolution)
     */
    public void setEvolutionInterval(int ticks) {
        this.evolutionInterval = Math.max(100, ticks);
        plugin.getLogger().info("[TickSync] Evolution interval set to " + evolutionInterval + " ticks");
    }

    /**
     * Set checkpoint interval (how often to save models)
     */
    public void setCheckpointInterval(int ticks) {
        this.checkpointInterval = Math.max(20, ticks);
        plugin.getLogger().info("[TickSync] Checkpoint interval set to " + checkpointInterval + " ticks");
    }
}
