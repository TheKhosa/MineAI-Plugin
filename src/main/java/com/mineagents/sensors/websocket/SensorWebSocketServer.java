package com.mineagents.sensors.websocket;

import com.google.gson.Gson;
import com.mineagents.sensors.AgentSensorPlugin;
// PHASE 2 (Option 2): import com.mineagents.sensors.npc.NPCAgent;
// PHASE 2 (Option 2): import com.mineagents.sensors.npc.NPCManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * WebSocket server for real-time sensor data streaming
 * Allows Node.js agents to receive server-side sensor data
 */
public class SensorWebSocketServer extends WebSocketServer {

    private final AgentSensorPlugin plugin;
    private final Gson gson;
    private final Set<WebSocket> authenticatedClients;
    private final Map<WebSocket, String> clientBotNames;
    private final String authToken;
    // PHASE 2 (Option 2): private NPCManager npcManager; // NPC manager for spawning agents

    public SensorWebSocketServer(AgentSensorPlugin plugin, int port, String authToken) {
        super(new InetSocketAddress(port));
        this.plugin = plugin;
        this.gson = new Gson();
        this.authenticatedClients = ConcurrentHashMap.newKeySet();
        this.clientBotNames = new ConcurrentHashMap<>();
        this.authToken = authToken;

        plugin.getLogger().info("[WebSocket] Initializing server on port " + port);
    }

    /**
     * PHASE 2 (Option 2): Set NPC manager
     */
    // public void setNPCManager(NPCManager npcManager) {
    //     this.npcManager = npcManager;
    //     plugin.getLogger().info("[WebSocket] NPC Manager registered");
    // }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        plugin.getLogger().info("[WebSocket] Client connected: " + conn.getRemoteSocketAddress());

        // Send authentication request
        Map<String, String> authRequest = new HashMap<>();
        authRequest.put("type", "auth_required");
        authRequest.put("message", "Please send auth token");
        conn.send(gson.toJson(authRequest));
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        authenticatedClients.remove(conn);
        String botName = clientBotNames.remove(conn);

        plugin.getLogger().info("[WebSocket] Client disconnected: " +
            (botName != null ? botName : conn.getRemoteSocketAddress()) +
            " (code: " + code + ", reason: " + reason + ")");
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        try {
            Map<String, Object> data = gson.fromJson(message, Map.class);
            String type = (String) data.get("type");

            if ("auth".equals(type)) {
                handleAuthentication(conn, data);
            } else if ("register_bot".equals(type)) {
                handleBotRegistration(conn, data);
            } else if ("request_sensors".equals(type)) {
                handleSensorRequest(conn, data);
            // PHASE 2 (Option 2): NPC message handlers
            // } else if ("spawn_agent".equals(type)) {
            //     handleSpawnAgent(conn, data);
            // } else if ("action".equals(type)) {
            //     handleAction(conn, data);
            // } else if ("remove_agent".equals(type)) {
            //     handleRemoveAgent(conn, data);
            } else if ("heartbeat".equals(type)) {
                handleHeartbeat(conn, data);
            } else {
                plugin.getLogger().warning("[WebSocket] Unknown message type: " + type);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[WebSocket] Error processing message", e);
            conn.send(gson.toJson(createErrorResponse("Invalid message format")));
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        plugin.getLogger().log(Level.SEVERE, "[WebSocket] Error occurred", ex);
        if (conn != null) {
            authenticatedClients.remove(conn);
            clientBotNames.remove(conn);
        }
    }

    @Override
    public void onStart() {
        plugin.getLogger().info("[WebSocket] Server started successfully on port " + getPort());
        setConnectionLostTimeout(30); // 30 seconds timeout
    }

    /**
     * Handle client authentication
     */
    private void handleAuthentication(WebSocket conn, Map<String, Object> data) {
        String token = (String) data.get("token");

        if (authToken.equals(token)) {
            authenticatedClients.add(conn);

            Map<String, Object> response = new HashMap<>();
            response.put("type", "auth_success");
            response.put("message", "Authentication successful");
            conn.send(gson.toJson(response));

            plugin.getLogger().info("[WebSocket] Client authenticated: " + conn.getRemoteSocketAddress());
        } else {
            Map<String, Object> response = createErrorResponse("Invalid authentication token");
            conn.send(gson.toJson(response));
            conn.close(1008, "Authentication failed");
        }
    }

    /**
     * Handle bot registration (associates WebSocket with bot name)
     */
    private void handleBotRegistration(WebSocket conn, Map<String, Object> data) {
        if (!authenticatedClients.contains(conn)) {
            conn.send(gson.toJson(createErrorResponse("Not authenticated")));
            return;
        }

        String botName = (String) data.get("botName");
        if (botName == null || botName.isEmpty()) {
            conn.send(gson.toJson(createErrorResponse("Invalid bot name")));
            return;
        }

        clientBotNames.put(conn, botName);

        Map<String, Object> response = new HashMap<>();
        response.put("type", "registration_success");
        response.put("botName", botName);
        response.put("message", "Bot registered successfully");
        conn.send(gson.toJson(response));

        plugin.getLogger().info("[WebSocket] Bot registered: " + botName);
    }

    /**
     * Handle sensor data request from client
     */
    private void handleSensorRequest(WebSocket conn, Map<String, Object> data) {
        if (!authenticatedClients.contains(conn)) {
            conn.send(gson.toJson(createErrorResponse("Not authenticated")));
            return;
        }

        String botName = clientBotNames.get(conn);
        if (botName == null) {
            conn.send(gson.toJson(createErrorResponse("Bot not registered")));
            return;
        }

        // Get sensor data (will be implemented in broadcaster)
        // For now, send acknowledgment
        Map<String, Object> response = new HashMap<>();
        response.put("type", "sensor_request_received");
        response.put("botName", botName);
        conn.send(gson.toJson(response));
    }

    /**
     * Broadcast raw string to all authenticated clients
     */
    public void broadcast(String message) {
        for (WebSocket conn : authenticatedClients) {
            if (conn.isOpen()) {
                conn.send(message);
            }
        }
    }

    /**
     * Broadcast sensor data to all authenticated clients
     */
    public void broadcastSensorData(String botName, Map<String, Object> sensorData) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "sensor_update");
        message.put("botName", botName);
        message.put("timestamp", System.currentTimeMillis());
        message.put("data", sensorData);

        String json = gson.toJson(message);

        // Send to all clients registered for this bot
        for (Map.Entry<WebSocket, String> entry : clientBotNames.entrySet()) {
            if (entry.getValue().equals(botName)) {
                WebSocket conn = entry.getKey();
                if (conn.isOpen()) {
                    conn.send(json);
                }
            }
        }
    }

    /**
     * Broadcast sensor data to specific client
     */
    public void sendSensorData(WebSocket conn, String botName, Map<String, Object> sensorData) {
        if (!conn.isOpen()) {
            return;
        }

        Map<String, Object> message = new HashMap<>();
        message.put("type", "sensor_update");
        message.put("botName", botName);
        message.put("timestamp", System.currentTimeMillis());
        message.put("data", sensorData);

        conn.send(gson.toJson(message));
    }

    /**
     * Create error response
     */
    private Map<String, Object> createErrorResponse(String errorMessage) {
        Map<String, Object> response = new HashMap<>();
        response.put("type", "error");
        response.put("message", errorMessage);
        return response;
    }

    /**
     * PHASE 2 (Option 2): Handle spawn agent request from hub
     */
    /*
    private void handleSpawnAgent(WebSocket conn, Map<String, Object> data) {
        if (!authenticatedClients.contains(conn)) {
            conn.send(gson.toJson(createErrorResponse("Not authenticated")));
            return;
        }

        if (npcManager == null) {
            conn.send(gson.toJson(createErrorResponse("NPC Manager not initialized")));
            return;
        }

        try {
            String agentName = (String) data.get("agentName");
            String agentType = (String) data.get("agentType");
            Map<String, Object> locationData = (Map<String, Object>) data.get("location");

            // Parse location
            String worldName = (String) locationData.get("world");
            double x = ((Number) locationData.get("x")).doubleValue();
            double y = ((Number) locationData.get("y")).doubleValue();
            double z = ((Number) locationData.get("z")).doubleValue();

            org.bukkit.World world = Bukkit.getWorld(worldName);
            if (world == null) {
                world = Bukkit.getWorlds().get(0); // Default to main world
            }

            Location location = new Location(world, x, y, z);

            // Spawn agent on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getLogger().info("[PLUGIN BRIDGE] Spawning agent " + agentName + " (" + agentType + ") at " + location);

                NPCAgent agent = npcManager.spawnAgent(agentName, agentType, location);

                if (agent != null) {
                    // Send spawn confirmation to hub
                    sendSpawnConfirm(conn, agent);
                    plugin.getLogger().info("[PLUGIN BRIDGE] Successfully spawned " + agentName + " as " + agent.getPlayerName());
                } else {
                    conn.send(gson.toJson(createErrorResponse("Failed to spawn agent " + agentName)));
                }
            });

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[WebSocket] Error spawning agent", e);
            conn.send(gson.toJson(createErrorResponse("Error spawning agent: " + e.getMessage())));
        }
    }
    */

    /**
     * PHASE 2 (Option 2): Handle action command from hub
     */
    /*
    private void handleAction(WebSocket conn, Map<String, Object> data) {
        if (!authenticatedClients.contains(conn)) {
            conn.send(gson.toJson(createErrorResponse("Not authenticated")));
            return;
        }

        if (npcManager == null) {
            conn.send(gson.toJson(createErrorResponse("NPC Manager not initialized")));
            return;
        }

        try {
            String agentName = (String) data.get("agentName");
            String actionType = (String) data.get("action");
            Object params = data.get("params");

            NPCAgent agent = npcManager.getAgent(agentName);

            if (agent == null) {
                plugin.getLogger().warning("[WebSocket] Action for unknown agent: " + agentName);
                return;
            }

            // Execute action on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                agent.executeAction(actionType, params);
            });

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[WebSocket] Error handling action", e);
        }
    }
    */

    /**
     * PHASE 2 (Option 2): Handle remove agent request from hub
     */
    /*
    private void handleRemoveAgent(WebSocket conn, Map<String, Object> data) {
        if (!authenticatedClients.contains(conn)) {
            conn.send(gson.toJson(createErrorResponse("Not authenticated")));
            return;
        }

        if (npcManager == null) {
            return;
        }

        try {
            String agentName = (String) data.get("agentName");

            Bukkit.getScheduler().runTask(plugin, () -> {
                npcManager.removeAgent(agentName);
                plugin.getLogger().info("[PLUGIN BRIDGE] Removed agent " + agentName);
            });

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[WebSocket] Error removing agent", e);
        }
    }
    */

    /**
     * Handle heartbeat message
     */
    private void handleHeartbeat(WebSocket conn, Map<String, Object> data) {
        // Just echo back
        Map<String, Object> response = new HashMap<>();
        response.put("type", "heartbeat");
        response.put("timestamp", System.currentTimeMillis());
        conn.send(gson.toJson(response));
    }

    /**
     * PHASE 2 (Option 2): Send spawn confirmation to hub
     */
    /*
    private void sendSpawnConfirm(WebSocket conn, NPCAgent agent) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "spawn_confirm");
        message.put("agentName", agent.getAgentName());
        message.put("entityUUID", agent.getEntityUUID().toString());

        // Location
        Location loc = agent.getLocation();
        Map<String, Object> location = new HashMap<>();
        location.put("world", loc.getWorld().getName());
        location.put("x", loc.getX());
        location.put("y", loc.getY());
        location.put("z", loc.getZ());
        message.put("location", location);

        // Player info
        Map<String, Object> playerInfo = new HashMap<>();
        playerInfo.put("realName", agent.getPlayerName());
        playerInfo.put("uuid", agent.getUUID().toString());
        message.put("playerInfo", playerInfo);

        conn.send(gson.toJson(message));
        plugin.getLogger().info("[PLUGIN BRIDGE] Sent spawn confirmation for " + agent.getAgentName());
    }
    */

    /**
     * Get authenticated client count
     */
    public int getAuthenticatedClientCount() {
        return authenticatedClients.size();
    }

    /**
     * Get registered bot count
     */
    public int getRegisteredBotCount() {
        return clientBotNames.size();
    }

    /**
     * Get all registered bot names
     */
    public Set<String> getRegisteredBotNames() {
        return Set.copyOf(clientBotNames.values());
    }

    /**
     * Shutdown server gracefully
     */
    public void shutdown() {
        try {
            plugin.getLogger().info("[WebSocket] Shutting down server...");

            // Notify all clients
            Map<String, String> shutdownMessage = new HashMap<>();
            shutdownMessage.put("type", "server_shutdown");
            shutdownMessage.put("message", "Server is shutting down");
            String json = gson.toJson(shutdownMessage);

            for (WebSocket conn : getConnections()) {
                if (conn.isOpen()) {
                    conn.send(json);
                    conn.close(1001, "Server shutting down");
                }
            }

            // Stop server
            stop(1000);

            plugin.getLogger().info("[WebSocket] Server stopped");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[WebSocket] Error during shutdown", e);
        }
    }
}
