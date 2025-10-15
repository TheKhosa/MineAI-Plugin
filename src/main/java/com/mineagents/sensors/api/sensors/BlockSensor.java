package com.mineagents.sensors.api.sensors;

import com.mineagents.sensors.api.SensorAPI;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Block Sensor - Enhanced block data beyond basic type
 */
public class BlockSensor {

    public List<SensorAPI.BlockData> getBlockData(Location center, int radius) {
        List<SensorAPI.BlockData> blocks = new ArrayList<>();

        int centerX = center.getBlockX();
        int centerY = center.getBlockY();
        int centerZ = center.getBlockZ();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = center.getWorld().getBlockAt(centerX + x, centerY + y, centerZ + z);

                    SensorAPI.BlockData data = new SensorAPI.BlockData();
                    data.x = block.getX();
                    data.y = block.getY();
                    data.z = block.getZ();
                    data.type = block.getType().name();
                    data.lightLevel = block.getLightLevel();
                    data.isPassable = block.isPassable();
                    data.metadata = getBlockMetadata(block);

                    blocks.add(data);
                }
            }
        }

        return blocks;
    }

    private Map<String, Object> getBlockMetadata(Block block) {
        Map<String, Object> metadata = new HashMap<>();

        // Add block-specific metadata
        metadata.put("hardness", block.getType().getHardness());
        metadata.put("isOccluding", block.getType().isOccluding());
        metadata.put("isSolid", block.getType().isSolid());
        metadata.put("isFlammable", block.getType().isFlammable());
        metadata.put("isBurnable", block.getType().isBurnable());

        // Block state data (for chests, furnaces, etc.)
        if (block.getState() != null) {
            metadata.put("hasBlockState", true);
            metadata.put("blockStateType", block.getState().getClass().getSimpleName());
        }

        return metadata;
    }
}
