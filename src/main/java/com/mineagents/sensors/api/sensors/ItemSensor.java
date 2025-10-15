package com.mineagents.sensors.api.sensors;

import com.mineagents.sensors.api.SensorAPI;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;

import java.util.ArrayList;
import java.util.List;

/**
 * Item Sensor - Track dropped items
 */
public class ItemSensor {

    public List<SensorAPI.ItemData> getNearbyItems(Location center, int radius) {
        List<SensorAPI.ItemData> items = new ArrayList<>();

        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (entity instanceof Item) {
                Item item = (Item) entity;

                SensorAPI.ItemData data = new SensorAPI.ItemData();
                data.uuid = item.getUniqueId().toString();
                data.itemType = item.getItemStack().getType().name();
                data.amount = item.getItemStack().getAmount();
                data.x = item.getLocation().getX();
                data.y = item.getLocation().getY();
                data.z = item.getLocation().getZ();
                data.ticksLived = item.getTicksLived();

                items.add(data);
            }
        }

        return items;
    }
}
