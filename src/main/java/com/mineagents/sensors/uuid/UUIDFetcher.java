package com.mineagents.sensors.uuid;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * UUID Fetcher System
 *
 * Fetches real Minecraft UUIDs from TheKhosa/MC-UUID repository
 * Validates them via Mojang API
 * Caches valid/invalid UUIDs to avoid repeated API calls
 */
public class UUIDFetcher {

    private static final Logger logger = Logger.getLogger("UUIDFetcher");

    // GitHub repository configuration
    private static final String GITHUB_BASE_URL = "https://raw.githubusercontent.com/TheKhosa/MC-UUID/main/chunks/";
    private static final int MIN_CHUNK = 1;
    private static final int MAX_CHUNK = 675;

    // Mojang API configuration
    private static final String MOJANG_API_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";

    // Cache configuration
    private final File validUUIDFile;
    private final File invalidUUIDFile;
    private final Set<String> validUUIDs = Collections.synchronizedSet(new HashSet<>());
    private final Set<String> invalidUUIDs = Collections.synchronizedSet(new HashSet<>());
    private final Map<String, String> uuidToName = Collections.synchronizedMap(new HashMap<>());
    private final Queue<String> uuidCache = new LinkedList<>();

    // HTTP client
    private final OkHttpClient httpClient;

    // Statistics
    private int chunksLoaded = 0;
    private int mojangQueries = 0;

    public UUIDFetcher(File dataFolder) {
        this.validUUIDFile = new File(dataFolder, "valid_uuids.txt");
        this.invalidUUIDFile = new File(dataFolder, "invalid_uuids.txt");

        // Configure HTTP client with timeouts
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

        // Load caches
        loadCaches();
    }

    /**
     * Load valid and invalid UUID caches from files
     */
    private void loadCaches() {
        // Load valid UUIDs
        if (validUUIDFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(validUUIDFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts.length == 2) {
                        String uuid = parts[0].trim();
                        String name = parts[1].trim();
                        validUUIDs.add(uuid);
                        uuidToName.put(uuid, name);
                    }
                }
                logger.info("Loaded " + validUUIDs.size() + " valid UUIDs from cache");
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to load valid UUID cache", e);
            }
        }

        // Load invalid UUIDs
        if (invalidUUIDFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(invalidUUIDFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    invalidUUIDs.add(line.trim());
                }
                logger.info("Loaded " + invalidUUIDs.size() + " invalid UUIDs from cache");
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to load invalid UUID cache", e);
            }
        }
    }

    /**
     * Fetch a chunk of UUIDs from GitHub
     */
    public boolean fetchChunk() {
        int chunkNum = new Random().nextInt(MAX_CHUNK) + MIN_CHUNK;
        String chunkName = String.format("chunk_%04d.txt", chunkNum);
        String url = GITHUB_BASE_URL + chunkName;

        logger.info("Fetching UUIDs from " + chunkName + "...");

        Request request = new Request.Builder()
            .url(url)
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                logger.warning("Failed to fetch " + chunkName + ": HTTP " + response.code());
                return false;
            }

            String body = response.body().string();
            String[] uuids = body.split("\n");

            int added = 0;
            for (String uuid : uuids) {
                uuid = uuid.trim();
                if (!uuid.isEmpty() && !invalidUUIDs.contains(uuid)) {
                    uuidCache.offer(uuid);
                    added++;
                }
            }

            chunksLoaded++;
            logger.info("Loaded " + added + " UUIDs from " + chunkName + " (total chunks: " + chunksLoaded + ")");
            return true;

        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to fetch " + chunkName, e);
            return false;
        }
    }

    /**
     * Query Mojang API for player name
     * Returns null if UUID is invalid (404)
     */
    private String queryMojangAPI(String uuid) {
        // Remove dashes for Mojang API
        String cleanUUID = uuid.replace("-", "");
        String url = MOJANG_API_URL + cleanUUID;

        Request request = new Request.Builder()
            .url(url)
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            mojangQueries++;

            if (response.code() == 404) {
                // UUID doesn't exist
                return null;
            }

            if (!response.isSuccessful()) {
                logger.warning("Mojang API error: HTTP " + response.code());
                return null;
            }

            String body = response.body().string();
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();

            if (json.has("name")) {
                return json.get("name").getAsString();
            }

            return null;

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error querying Mojang API for " + uuid, e);
            return null;
        }
    }

    /**
     * Get next valid UUID with player name
     * Fetches chunks and validates until a valid UUID is found
     */
    public UUIDResult getNextValidUUID(int maxAttempts) {
        int attempts = 0;
        int chunksFetched = 0;

        while (attempts < maxAttempts) {
            // Fetch chunk if cache is empty
            if (uuidCache.isEmpty()) {
                if (chunksFetched >= 3) {
                    logger.warning("Exhausted 3 chunks without finding valid UUID");
                    return null;
                }

                if (!fetchChunk()) {
                    logger.warning("Failed to fetch UUID chunk");
                    return null;
                }

                chunksFetched++;
            }

            // Get next UUID from cache
            String uuid = uuidCache.poll();
            if (uuid == null) continue;

            attempts++;

            // Check if already validated (valid)
            if (validUUIDs.contains(uuid)) {
                String playerName = uuidToName.get(uuid);
                if (playerName != null) {
                    logger.info("Found cached valid UUID: " + playerName + " (" + uuid + ")");
                    return new UUIDResult(uuid, playerName);
                }
            }

            // Check if already validated (invalid)
            if (invalidUUIDs.contains(uuid)) {
                continue; // Try next UUID
            }

            // Query Mojang API
            logger.info("Validating UUID " + uuid + " via Mojang API...");
            String playerName = queryMojangAPI(uuid);

            if (playerName != null) {
                // Valid UUID - cache it
                addValidUUID(uuid, playerName);
                logger.info("Found valid player: " + playerName + " (" + uuid + ")");
                return new UUIDResult(uuid, playerName);
            } else {
                // Invalid UUID - cache it
                addInvalidUUID(uuid);
                logger.info("UUID " + uuid + " is invalid, trying next...");
            }

            // Rate limit: wait 100ms between Mojang API calls
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        logger.warning("Failed to find valid UUID after " + attempts + " attempts");
        return null;
    }

    /**
     * Add valid UUID to cache and file
     */
    private void addValidUUID(String uuid, String playerName) {
        validUUIDs.add(uuid);
        uuidToName.put(uuid, playerName);

        try (FileWriter writer = new FileWriter(validUUIDFile, true)) {
            writer.write(uuid + "," + playerName + "\n");
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to save valid UUID", e);
        }
    }

    /**
     * Add invalid UUID to cache and file
     */
    private void addInvalidUUID(String uuid) {
        invalidUUIDs.add(uuid);

        try (FileWriter writer = new FileWriter(invalidUUIDFile, true)) {
            writer.write(uuid + "\n");
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to save invalid UUID", e);
        }
    }

    /**
     * Get statistics
     */
    public Map<String, Integer> getStats() {
        Map<String, Integer> stats = new HashMap<>();
        stats.put("validUUIDs", validUUIDs.size());
        stats.put("invalidUUIDs", invalidUUIDs.size());
        stats.put("chunksLoaded", chunksLoaded);
        stats.put("mojangQueries", mojangQueries);
        stats.put("cacheSize", uuidCache.size());
        return stats;
    }

    /**
     * UUID Result class
     */
    public static class UUIDResult {
        private final String uuid;
        private final String playerName;

        public UUIDResult(String uuid, String playerName) {
            this.uuid = uuid;
            this.playerName = playerName;
        }

        public String getUUID() {
            return uuid;
        }

        public String getPlayerName() {
            return playerName;
        }
    }
}
