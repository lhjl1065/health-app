package com.bitfox.health.health.model;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class WeeklyStats {
    private String weekLabel;
    private double totalPoints;
    private int recordCount;
    private Map<String, Double> averages = new HashMap<>();

    public void addAverage(String itemName, double average) {
        averages.put(itemName, average);
    }

    public double getDailyAverage() {
        return recordCount > 0 ? totalPoints / recordCount : 0;
    }
}
