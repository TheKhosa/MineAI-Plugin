package com.mineagents.sensors.api.sensors;

import com.mineagents.sensors.api.SensorAPI;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;

import java.util.ArrayList;
import java.util.List;

/**
 * Entity Sensor - Track all entities with detailed state
 */
public class EntitySensor {

    public List<SensorAPI.EntityData> getEntitiesNearby(Location center, int radius) {
        List<SensorAPI.EntityData> entities = new ArrayList<>();

        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            SensorAPI.EntityData data = new SensorAPI.EntityData();

            data.uuid = entity.getUniqueId().toString();

            // SAFETY FIX: Ensure type is never null (fixes "Cannot read properties of undefined" error)
            data.type = entity.getType() != null ? entity.getType().name() : "UNKNOWN";

            // SAFETY FIX: Ensure name is never null
            String customName = entity.getCustomName();
            data.name = customName != null ? customName : entity.getType().name().toLowerCase();

            data.x = entity.getLocation().getX();
            data.y = entity.getLocation().getY();
            data.z = entity.getLocation().getZ();
            data.yaw = entity.getLocation().getYaw();
            data.pitch = entity.getLocation().getPitch();

            if (entity instanceof LivingEntity) {
                LivingEntity living = (LivingEntity) entity;
                data.health = living.getHealth();
                data.isHostile = entity instanceof Monster;
                data.aiState = getAIState(living);
            } else {
                data.health = 0;
                data.isHostile = false;
                data.aiState = "PASSIVE";
            }

            entities.add(data);
        }

        return entities;
    }

    private String getAIState(LivingEntity entity) {
        try {
            // Try to get target (may not exist in all Spigot versions)
            if (entity instanceof org.bukkit.entity.Mob) {
                org.bukkit.entity.Mob mob = (org.bukkit.entity.Mob) entity;
                if (mob.getTarget() != null) {
                    return "ATTACKING";
                }
            }
        } catch (Exception e) {
            // Fallback if getTarget() not available
        }

        if (entity.getVelocity().length() > 0.1) {
            return "MOVING";
        } else {
            return "IDLE";
        }
    }
}
