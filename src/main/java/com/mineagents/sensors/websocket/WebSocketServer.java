package com.mineagents.sensors.websocket;

import com.google.gson.Gson;
import com.mineagents.sensors.AgentSensorPlugin;
import org.bukkit.Bukkit;
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

    public SensorWebSocketServer(AgentSensorPlugin plugin, int port, String authToken) {
        super(new InetSocketAddress(port));
        this.plugin = plugin;
        this.gson = new Gson();
        this.authenticatedClients = ConcurrentHashMap.newKeySet();
        this.clientBotNames = new ConcurrentHashMap<>();
        this.authToken = authToken;

        plugin.getLogger().info("[WebSocket] Initializing server on port " + port);
    }

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
