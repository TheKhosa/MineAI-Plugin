package com.mineagents.sensors.api.sensors;

import com.mineagents.sensors.api.SensorAPI;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;

import java.util.ArrayList;
import java.util.List;

/**
 * Mob AI Sensor - Track mob AI goals and pathfinding
 */
public class MobAISensor {

    public List<SensorAPI.MobAIData> getMobAIStates(Location center, int radius) {
        List<SensorAPI.MobAIData> mobAI = new ArrayList<>();

        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (entity instanceof Mob) {
                Mob mob = (Mob) entity;

                SensorAPI.MobAIData data = new SensorAPI.MobAIData();
                data.entityUUID = mob.getUniqueId().toString();

                // Get current AI goal
                if (mob.getTarget() != null) {
                    data.currentGoal = "ATTACK_TARGET";
                    data.targetUUID = mob.getTarget().getUniqueId().toString();
                    data.isAggressive = true;
                } else {
                    data.currentGoal = "IDLE";
                    data.targetUUID = null;
                    data.isAggressive = false;
                }

                // Pathfinding data (simplified)
                data.pathfindingNodes = new ArrayList<>();
                if (mob.getVelocity().length() > 0) {
                    data.pathfindingNodes.add("MOVING");
                }

                mobAI.add(data);
            }
        }

        return mobAI;
    }
}
