package com.bitfox.health.health.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroomingRecord {
    private LocalDate date;
    private Set<String> completedParts = new HashSet<>();
    private boolean houseClean = false;

    public static final List<String> ALL_PARTS = Arrays.asList(
            "HAIR", "FACE", "MOUTH", "BODY", "TOP", "PANTS", "SOCKS", "SHOES", "NAILS"
    );

    @JsonIgnore
    public boolean isAllCompleted() {
        return completedParts.containsAll(ALL_PARTS);
    }

    @JsonIgnore
    public double getGroomingPoints() {
        return isAllCompleted() ? 0.5 : 0.0;
    }

    @JsonIgnore
    public double getHouseCleanPoints() {
        return houseClean ? 0.2 : 0.0;
    }
}
