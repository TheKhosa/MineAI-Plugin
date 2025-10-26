package com.mineagents.sensors.botmanager;

import com.mineagents.sensors.AgentSensorPlugin;
import com.mineagents.sensors.uuid.UUIDFetcher;
import org.bukkit.Bukkit;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages server-side mineflayer bot processes
 * Spawns Node.js mineflayer instances that connect to localhost
 */
public class MineflayerBotManager {

    private final AgentSensorPlugin plugin;
    private final Map<String, Process> botProcesses = new ConcurrentHashMap<>();
    private final Map<String, BotInfo> botInfo = new ConcurrentHashMap<>();
    private final Path botsDirectory;
    private final int serverPort;
    private final UUIDFetcher uuidFetcher;

    /**
     * Bot information for monitoring
     */
    public static class BotInfo {
        public String name;
        public String agentType;
        public long spawnedAt;
        public boolean isAlive;

        public BotInfo(String name, String agentType) {
            this.name = name;
            this.agentType = agentType;
            this.spawnedAt = System.currentTimeMillis();
            this.isAlive = true;
        }
    }

    public MineflayerBotManager(AgentSensorPlugin plugin) {
        this.plugin = plugin;
        this.botsDirectory = Paths.get(plugin.getDataFolder().getAbsolutePath(), "bots");
        this.serverPort = Bukkit.getServer().getPort();
        this.uuidFetcher = new UUIDFetcher(plugin.getDataFolder());

        // Create bots directory
        try {
            Files.createDirectories(botsDirectory);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create bots directory", e);
        }
    }

    /**
     * Spawn a new mineflayer bot
     */
    public boolean spawnBot(String botName, String agentType) {
        if (botProcesses.containsKey(botName)) {
            plugin.getLogger().warning("[BotManager] Bot " + botName + " already exists");
            return false;
        }

        try {
            // Create bot script
            String botScript = createBotScript(botName, agentType);
            Path scriptPath = botsDirectory.resolve(botName + ".js");
            Files.writeString(scriptPath, botScript);

            // Spawn Node.js process with local node_modules
            ProcessBuilder pb = new ProcessBuilder(
                "node",
                scriptPath.toString()
            );
            pb.directory(botsDirectory.toFile());
            pb.redirectErrorStream(true);

            // Set NODE_PATH to find local modules
            Map<String, String> env = pb.environment();
            String nodePath = botsDirectory.resolve("node_modules").toString();
            env.put("NODE_PATH", nodePath);
            plugin.getLogger().info("[BotManager] NODE_PATH set to: " + nodePath);

            Process process = pb.start();
            botProcesses.put(botName, process);

            // Track bot info
            botInfo.put(botName, new BotInfo(botName, agentType));

            // Monitor bot output
            startOutputMonitor(botName, process);

            plugin.getLogger().info("[BotManager] Spawned bot: " + botName + " (Type: " + agentType + ")");
            return true;

        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "[BotManager] Failed to spawn bot " + botName, e);
            return false;
        }
    }

    /**
     * Spawn a new mineflayer bot with a real player UUID and name
     */
    public boolean spawnBotWithRealUUID(String agentType) {
        try {
            // Get a real UUID and player name
            plugin.getLogger().info("[BotManager] Fetching real player UUID...");
            UUIDFetcher.UUIDResult result = uuidFetcher.getNextValidUUID(50);

            if (result == null) {
                plugin.getLogger().warning("[BotManager] Could not find valid UUID after 50 attempts");
                return false;
            }

            String playerName = result.getPlayerName();
            plugin.getLogger().info("[BotManager] Found player: " + playerName);

            // Spawn the bot with the real player name
            return spawnBot(playerName, agentType);

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[BotManager] Failed to spawn bot with real UUID", e);
            return false;
        }
    }

    /**
     * Auto-spawn multiple NPCs with real UUIDs
     */
    public void autoSpawnBots(int count, String agentType) {
        plugin.getLogger().info("[BotManager] Auto-spawning " + count + " NPCs...");

        for (int i = 0; i < count; i++) {
            boolean success = spawnBotWithRealUUID(agentType);

            if (success) {
                plugin.getLogger().info("[BotManager] Spawned NPC " + (i + 1) + "/" + count);
            } else {
                plugin.getLogger().warning("[BotManager] Failed to spawn NPC " + (i + 1) + "/" + count);
            }

            // Small delay between spawns
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        plugin.getLogger().info("[BotManager] Finished auto-spawning. Active bots: " + getBotCount());
    }

    /**
     * Stop a bot
     */
    public boolean stopBot(String botName) {
        Process process = botProcesses.remove(botName);
        if (process == null) {
            return false;
        }

        // Mark bot as dead
        BotInfo info = botInfo.get(botName);
        if (info != null) {
            info.isAlive = false;
        }

        process.destroy();
        try {
            process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Clean up script file
        try {
            Files.deleteIfExists(botsDirectory.resolve(botName + ".js"));
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to delete bot script", e);
        }

        // Remove from tracking
        botInfo.remove(botName);

        plugin.getLogger().info("[BotManager] Stopped bot: " + botName);
        return true;
    }

    /**
     * Get active bot count
     */
    public int getBotCount() {
        return botProcesses.size();
    }

    /**
     * Get all bot information for monitoring
     */
    public Map<String, BotInfo> getAllBotInfo() {
        return new HashMap<>(botInfo);
    }

    /**
     * Get specific bot info
     */
    public BotInfo getBotInfo(String botName) {
        return botInfo.get(botName);
    }

    /**
     * Stop all bots
     */
    public void stopAllBots() {
        plugin.getLogger().info("[BotManager] Stopping all bots (" + botProcesses.size() + ")...");

        for (String botName : botProcesses.keySet()) {
            stopBot(botName);
        }
    }

    /**
     * Create mineflayer bot script
     */
    private String createBotScript(String botName, String agentType) {
        return String.format("""
            const mineflayer = require('mineflayer');
            const { WebSocket } = require('ws');

            const bot = mineflayer.createBot({
                host: 'localhost',
                port: %d,
                username: '%s',
                auth: 'offline',
                version: '1.21.1'
            });

            // Connect to plugin WebSocket
            let ws = null;

            bot.once('spawn', () => {
                console.log('[%s] Spawned in game');

                // Connect to plugin WebSocket
                ws = new WebSocket('ws://localhost:3002');

                ws.on('open', () => {
                    console.log('[%s] Connected to plugin WebSocket');

                    // Authenticate
                    ws.send(JSON.stringify({
                        type: 'auth',
                        token: 'mineagent-sensor-2024'
                    }));
                });

                ws.on('message', (data) => {
                    try {
                        const msg = JSON.parse(data);

                        if (msg.type === 'auth_success') {
                            console.log('[%s] Authenticated with plugin');

                            // Register bot
                            ws.send(JSON.stringify({
                                type: 'register_bot',
                                botName: '%s',
                                agentType: '%s'
                            }));
                        } else if (msg.type === 'action') {
                            // Execute action from ML training
                            executeAction(msg.action, msg.params);
                        }
                    } catch (err) {
                        console.error('[%s] Error handling message:', err);
                    }
                });

                ws.on('error', (err) => {
                    console.error('[%s] WebSocket error:', err.message);
                });
            });

            bot.on('error', (err) => {
                console.error('[%s] Bot error:', err);
            });

            bot.on('kicked', (reason) => {
                console.log('[%s] Kicked:', reason);
                if (ws) ws.close();
                process.exit(0);
            });

            bot.on('end', () => {
                console.log('[%s] Disconnected');
                if (ws) ws.close();
                process.exit(0);
            });

            // Action execution
            function executeAction(action, params) {
                try {
                    switch (action) {
                        case 'move_forward':
                            bot.setControlState('forward', true);
                            setTimeout(() => bot.setControlState('forward', false), params.duration || 1000);
                            break;
                        case 'move_back':
                            bot.setControlState('back', true);
                            setTimeout(() => bot.setControlState('back', false), params.duration || 1000);
                            break;
                        case 'turn_left':
                            bot.look(bot.entity.yaw - Math.PI/4, bot.entity.pitch, true);
                            break;
                        case 'turn_right':
                            bot.look(bot.entity.yaw + Math.PI/4, bot.entity.pitch, true);
                            break;
                        case 'jump':
                            bot.setControlState('jump', true);
                            setTimeout(() => bot.setControlState('jump', false), 100);
                            break;
                        case 'dig':
                            const block = bot.blockAtCursor(5);
                            if (block) bot.dig(block);
                            break;
                        // Add more actions as needed
                    }
                } catch (err) {
                    console.error('[%s] Error executing action:', err);
                }
            }

            // Send observations periodically
            setInterval(() => {
                if (ws && ws.readyState === WebSocket.OPEN) {
                    const obs = {
                        type: 'observation',
                        botName: '%s',
                        position: bot.entity.position,
                        health: bot.health,
                        food: bot.food,
                        inventory: bot.inventory.slots.filter(s => s).map(s => ({
                            name: s.name,
                            count: s.count
                        })),
                        nearbyEntities: Object.keys(bot.entities).length,
                        timeOfDay: bot.time.timeOfDay
                    };

                    ws.send(JSON.stringify(obs));
                }
            }, 1000);
            """,
            serverPort, botName, botName, botName, botName, botName, agentType, botName,
            botName, botName, botName, botName, botName, botName, botName, botName
        );
    }

    /**
     * Monitor bot process output
     */
    private void startOutputMonitor(String botName, Process process) {
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    plugin.getLogger().info("[Bot:" + botName + "] " + line);
                }
            } catch (IOException e) {
                if (botProcesses.containsKey(botName)) {
                    plugin.getLogger().log(Level.WARNING, "[Bot:" + botName + "] Output stream closed", e);
                }
            }
        }, "BotMonitor-" + botName).start();
    }
}
