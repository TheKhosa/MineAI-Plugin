package com.mineagents.sensors.npc;

import com.mineagents.sensors.AgentSensorPlugin;
import com.mineagents.sensors.uuid.UUIDFetcher;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_21_R1.CraftServer;
import org.bukkit.craftbukkit.v1_21_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_21_R1.entity.CraftPlayer;
import org.bukkit.entity.EntityType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * NPC Manager
 *
 * Spawns and manages vanilla player NPCs (fake players)
 * NPCs appear as real players in the server but are controlled by the hub
 */
public class NPCManager {

    private static final Logger logger = Logger.getLogger("NPCManager");

    private final AgentSensorPlugin plugin;
    private final UUIDFetcher uuidFetcher;
    private final Map<String, NPCAgent> agents = new ConcurrentHashMap<>();

    public NPCManager(AgentSensorPlugin plugin) {
        this.plugin = plugin;
        this.uuidFetcher = new UUIDFetcher(plugin.getDataFolder());
    }

    /**
     * Spawn an NPC agent
     *
     * @param agentName Agent identifier from hub
     * @param agentType Agent type (MINING, LUMBERJACK, etc.)
     * @param location Spawn location
     * @return Spawned NPC agent, or null if failed
     */
    public NPCAgent spawnAgent(String agentName, String agentType, Location location) {
        logger.info("Spawning NPC agent: " + agentName + " (" + agentType + ")");

        try {
            // Get valid UUID and player name
            UUIDFetcher.UUIDResult uuidResult = uuidFetcher.getNextValidUUID(100);

            if (uuidResult == null) {
                logger.severe("Failed to fetch valid UUID for " + agentName);
                return null;
            }

            String playerName = uuidResult.getPlayerName();
            UUID uuid = UUID.fromString(uuidResult.getUUID());

            logger.info("Using player identity: " + playerName + " (UUID: " + uuid + ")");

            // Create NPC using NMS (Net Minecraft Server)
            ServerPlayer npc = createNPC(uuid, playerName, location);

            if (npc == null) {
                logger.severe("Failed to create NPC for " + agentName);
                return null;
            }

            // Create agent wrapper
            NPCAgent agent = new NPCAgent(agentName, agentType, npc, uuid, playerName);
            agents.put(agentName, agent);

            logger.info("Successfully spawned NPC: " + agentName + " as " + playerName);

            return agent;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error spawning NPC " + agentName, e);
            return null;
        }
    }

    /**
     * Create an NPC using NMS (Net Minecraft Server)
     */
    private ServerPlayer createNPC(UUID uuid, String playerName, Location location) {
        try {
            MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
            ServerLevel world = ((CraftWorld) location.getWorld()).getHandle();

            // Create game profile
            GameProfile profile = new GameProfile(uuid, playerName);

            // Fetch skin from Mojang (optional - uses default skin if fails)
            try {
                // TODO: Fetch skin textures from Mojang session server
                // For now, NPCs will have default/Steve skin
            } catch (Exception e) {
                logger.warning("Could not fetch skin for " + playerName);
            }

            // Create fake player entity
            ServerPlayer npc = new ServerPlayer(
                server,
                world,
                profile,
                ClientInformation.createDefault()
            );

            // Set position
            npc.setPos(location.getX(), location.getY(), location.getZ());
            npc.setRot(location.getYaw(), location.getPitch());

            // Set game mode to survival
            npc.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);

            // Add to world
            world.addFreshEntity(npc);

            // Show to all players
            for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
                ServerPlayer serverPlayer = ((CraftPlayer) player).getHandle();
                ServerGamePacketListenerImpl connection = serverPlayer.connection;

                // Send spawn packet
                connection.send(new ClientboundPlayerInfoUpdatePacket(
                    ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                    npc
                ));

                connection.send(new ClientboundAddPlayerPacket(npc));

                // Send equipment packets
                connection.send(new ClientboundSetEntityDataPacket(
                    npc.getId(),
                    npc.getEntityData().getNonDefaultValues()
                ));
            }

            logger.info("Created NPC entity: " + playerName + " at " + location);

            return npc;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to create NPC entity", e);
            return null;
        }
    }

    /**
     * Remove an NPC agent
     */
    public void removeAgent(String agentName) {
        NPCAgent agent = agents.remove(agentName);

        if (agent == null) {
            logger.warning("Agent not found: " + agentName);
            return;
        }

        try {
            ServerPlayer npc = agent.getNPC();

            // Remove from all players' view
            for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
                ServerPlayer serverPlayer = ((CraftPlayer) player).getHandle();
                ServerGamePacketListenerImpl connection = serverPlayer.connection;

                // Send remove packet
                connection.send(new ClientboundPlayerInfoRemovePacket(
                    List.of(npc.getUUID())
                ));

                connection.send(new ClientboundRemoveEntitiesPacket(
                    npc.getId()
                ));
            }

            // Remove from world
            npc.remove(org.bukkit.event.entity.EntityRemoveEvent.Cause.PLUGIN);

            logger.info("Removed NPC agent: " + agentName);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error removing NPC " + agentName, e);
        }
    }

    /**
     * Get an agent by name
     */
    public NPCAgent getAgent(String agentName) {
        return agents.get(agentName);
    }

    /**
     * Get all agents
     */
    public Collection<NPCAgent> getAllAgents() {
        return agents.values();
    }

    /**
     * Get agent count
     */
    public int getAgentCount() {
        return agents.size();
    }

    /**
     * Get UUID fetcher statistics
     */
    public Map<String, Integer> getUUIDStats() {
        return uuidFetcher.getStats();
    }

    /**
     * Shutdown - remove all NPCs
     */
    public void shutdown() {
        logger.info("Shutting down NPC Manager...");

        for (String agentName : new ArrayList<>(agents.keySet())) {
            removeAgent(agentName);
        }

        logger.info("NPC Manager shutdown complete");
    }
}
