package com.mineagents.sensors.updater;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mineagents.sensors.AgentSensorPlugin;
import okhttp3.*;
import org.bukkit.Bukkit;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.logging.Level;

/**
 * TeamCity CI Integration - Auto-update plugin from successful builds
 *
 * Features:
 * - Checks TeamCity for latest successful build
 * - Downloads new JAR if version is newer
 * - Replaces old plugin file
 * - Triggers server reload
 */
public class TeamCityUpdater {

    private final AgentSensorPlugin plugin;
    private final String teamcityUrl;
    private final String username;
    private final String password;
    private final String buildTypeId;
    private final OkHttpClient httpClient;
    private final Gson gson;

    public TeamCityUpdater(AgentSensorPlugin plugin, String teamcityUrl, String username, String password, String buildTypeId) {
        this.plugin = plugin;
        this.teamcityUrl = teamcityUrl;
        this.username = username;
        this.password = password;
        this.buildTypeId = buildTypeId;
        this.httpClient = new OkHttpClient();
        this.gson = new Gson();
    }

    /**
     * Check for updates and download if available
     * @return true if update was downloaded and installed
     */
    public boolean checkAndUpdate() {
        try {
            // Get latest successful build info
            BuildInfo latestBuild = getLatestSuccessfulBuild();

            if (latestBuild == null) {
                plugin.getLogger().info("[Update] No successful builds found in TeamCity");
                return false;
            }

            plugin.getLogger().info("[Update] Latest build: #" + latestBuild.number + " (" + latestBuild.id + ")");

            // Compare versions
            if (!isNewerVersion(latestBuild.number)) {
                plugin.getLogger().info("[Update] Current version is up to date");
                return false;
            }

            plugin.getLogger().info("[Update] New version available! Downloading...");

            // Download artifact
            File downloadedJar = downloadArtifact(latestBuild.id);

            if (downloadedJar == null) {
                plugin.getLogger().warning("[Update] Failed to download artifact");
                return false;
            }

            plugin.getLogger().info("[Update] Download complete! Installing...");

            // Install update
            installUpdate(downloadedJar);

            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[Update] Error during update check", e);
            return false;
        }
    }

    /**
     * Get latest successful build from TeamCity
     */
    private BuildInfo getLatestSuccessfulBuild() throws IOException {
        String url = teamcityUrl + "/app/rest/builds" +
                    "?locator=buildType:" + buildTypeId + ",status:SUCCESS,count:1" +
                    "&fields=build(id,number,status)";

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", getAuthHeader())
                .header("Accept", "application/json")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                plugin.getLogger().warning("[Update] TeamCity returned: " + response.code());
                return null;
            }

            String responseBody = response.body().string();
            JsonObject json = gson.fromJson(responseBody, JsonObject.class);

            if (!json.has("build") || json.getAsJsonArray("build").size() == 0) {
                return null;
            }

            JsonObject build = json.getAsJsonArray("build").get(0).getAsJsonObject();

            return new BuildInfo(
                build.get("id").getAsString(),
                build.get("number").getAsString(),
                build.get("status").getAsString()
            );
        }
    }

    /**
     * Download artifact from TeamCity build
     */
    private File downloadArtifact(String buildId) throws IOException {
        String url = teamcityUrl + "/app/rest/builds/id:" + buildId + "/artifacts/content/AgentSensorPlugin.jar";

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", getAuthHeader())
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                plugin.getLogger().warning("[Update] Failed to download artifact: " + response.code());
                return null;
            }

            // Save to temp file
            File tempFile = File.createTempFile("AgentSensorPlugin-", ".jar");
            try (InputStream is = response.body().byteStream();
                 FileOutputStream fos = new FileOutputStream(tempFile)) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }

            plugin.getLogger().info("[Update] Downloaded to: " + tempFile.getAbsolutePath());
            return tempFile;
        }
    }

    /**
     * Install update by replacing current plugin JAR
     */
    private void installUpdate(File newJar) {
        try {
            File pluginsFolder = plugin.getDataFolder().getParentFile();

            // Generate new filename with timestamp
            String newFileName = "AgentSensorPlugin-" + System.currentTimeMillis() + ".jar";
            File targetFile = new File(pluginsFolder, newFileName);

            // Copy new JAR to plugins folder
            Files.copy(newJar.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            plugin.getLogger().info("[Update] Installed new version: " + targetFile.getName());

            // Mark old versions for deletion (will be cleaned up on next restart)
            // We can't delete the current running JAR, so we mark it for deletion on exit

            // Schedule plugin reload
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getLogger().info("[Update] Reloading plugins...");

                // Reload server plugins
                try {
                    Bukkit.getServer().reload();
                    plugin.getLogger().info("[Update] Plugin reload complete!");
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "[Update] Failed to reload plugins", e);
                }
            });

            // Clean up temp file
            newJar.delete();

        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "[Update] Failed to install update", e);
        }
    }

    /**
     * Check if new version is newer than current
     */
    private boolean isNewerVersion(String newVersion) {
        String currentVersion = plugin.getDescription().getVersion();

        // Simple string comparison for now
        // TODO: Implement semantic versioning comparison
        return !currentVersion.equals(newVersion);
    }

    /**
     * Generate Basic Auth header
     */
    private String getAuthHeader() {
        String credentials = username + ":" + password;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());
        return "Basic " + encoded;
    }

    /**
     * Build info data class
     */
    private static class BuildInfo {
        final String id;
        final String number;
        final String status;

        BuildInfo(String id, String number, String status) {
            this.id = id;
            this.number = number;
            this.status = status;
        }
    }
}
