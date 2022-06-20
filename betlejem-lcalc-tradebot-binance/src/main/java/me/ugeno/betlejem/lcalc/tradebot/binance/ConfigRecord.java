package me.ugeno.betlejem.lcalc.tradebot.binance;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.springframework.lang.NonNull;

/**
 * Created by alwi on 24/01/2022.
 * All rights reserved.
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class ConfigRecord {
    @NonNull
    private final TradebotConfig config;

    @NonNull
    private final String startTime;

    @NonNull
    private final String endTime;

    @NonNull
    private final int simRecentMinutes;

    @JsonCreator
    public ConfigRecord(TradebotConfig config, int simRecentMinutes, String startTime, String endTime) {
        this.config = config;
        this.simRecentMinutes = simRecentMinutes;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    @NonNull
    public TradebotConfig getConfig() {
        return config;
    }

    @NonNull
    public String getStartTime() {
        return startTime;
    }

    @NonNull
    public String getEndTime() {
        return endTime;
    }

    @NonNull
    public int getSimRecentMinutes() {
        return simRecentMinutes;
    }
}
