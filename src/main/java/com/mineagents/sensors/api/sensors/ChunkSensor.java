package com.mineagents.sensors.api.sensors;

import com.mineagents.sensors.api.SensorAPI;
import org.bukkit.Chunk;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

/**
 * Chunk Sensor - Track chunk loading states
 */
public class ChunkSensor {

    public List<SensorAPI.ChunkData> getChunkData(Location center, int radius) {
        List<SensorAPI.ChunkData> chunks = new ArrayList<>();

        int chunkX = center.getBlockX() >> 4;
        int chunkZ = center.getBlockZ() >> 4;
        int chunkRadius = (radius >> 4) + 1;

        for (int x = -chunkRadius; x <= chunkRadius; x++) {
            for (int z = -chunkRadius; z <= chunkRadius; z++) {
                Chunk chunk = center.getWorld().getChunkAt(chunkX + x, chunkZ + z);

                SensorAPI.ChunkData data = new SensorAPI.ChunkData();
                data.chunkX = chunk.getX();
                data.chunkZ = chunk.getZ();
                data.isLoaded = chunk.isLoaded();
                data.entityCount = chunk.getEntities().length;
                data.tileEntityCount = chunk.getTileEntities().length;

                chunks.add(data);
            }
        }

        return chunks;
    }
}
