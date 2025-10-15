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
            data.type = entity.getType().name();
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
        if (entity.getTarget() != null) {
            return "ATTACKING";
        } else if (entity.getVelocity().length() > 0.1) {
            return "MOVING";
        } else {
            return "IDLE";
        }
    }
}
