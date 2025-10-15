package com.mineagents.sensors.api.sensors;

import com.mineagents.sensors.api.SensorAPI;
import org.bukkit.World;

/**
 * Weather Sensor - Track weather patterns and time
 */
public class WeatherSensor {

    public SensorAPI.WeatherData getWeatherData(World world) {
        SensorAPI.WeatherData data = new SensorAPI.WeatherData();

        data.isRaining = world.hasStorm();
        data.isThundering = world.isThundering();
        data.weatherDuration = world.getWeatherDuration();
        data.worldTime = world.getTime();
        data.timeOfDay = getTimeOfDay(world.getTime());

        return data;
    }

    private String getTimeOfDay(long time) {
        long normalizedTime = time % 24000;

        if (normalizedTime < 6000) {
            return "MORNING";
        } else if (normalizedTime < 12000) {
            return "DAY";
        } else if (normalizedTime < 18000) {
            return "EVENING";
        } else {
            return "NIGHT";
        }
    }
}
