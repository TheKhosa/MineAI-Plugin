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
                    Material type = block.getType();

                    // PERFORMANCE FIX: Skip air and common terrain blocks to reduce data size
                    // This prevents stack overflow when processing 33k+ blocks
                    if (shouldSkipBlock(type)) {
                        continue;
                    }

                    SensorAPI.BlockData data = new SensorAPI.BlockData();
                    data.x = block.getX();
                    data.y = block.getY();
                    data.z = block.getZ();
                    data.type = type.name();
                    data.lightLevel = block.getLightLevel();
                    data.isPassable = block.isPassable();
                    data.metadata = getBlockMetadata(block);

                    blocks.add(data);
                }
            }
        }

        return blocks;
    }

    /**
     * Determine if a block should be skipped to reduce data size
     * Skips air, cave air, void air, stone, dirt, grass (common terrain)
     * Keeps ores, structures, fluids, and interactive blocks
     */
    private boolean shouldSkipBlock(Material type) {
        switch (type) {
            // Skip all air variants
            case AIR:
            case CAVE_AIR:
            case VOID_AIR:
                return true;

            // Skip extremely common terrain blocks (reduces data by ~90%)
            case STONE:
            case DIRT:
            case GRASS_BLOCK:
            case GRAVEL:
            case SAND:
            case SANDSTONE:
            case ANDESITE:
            case DIORITE:
            case GRANITE:
                return true;

            // Keep everything else (ores, structures, fluids, interactive blocks)
            default:
                return false;
        }
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
