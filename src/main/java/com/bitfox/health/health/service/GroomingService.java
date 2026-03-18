package com.bitfox.health.health.service;

import com.bitfox.health.health.model.GroomingRecord;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Service
public class GroomingService {

    @Value("${health.grooming.path:#{null}}")
    private String groomingPath;

    @Value("${health.excel.path}")
    private String excelPath;

    private final ObjectMapper objectMapper;

    public GroomingService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @PostConstruct
    public void init() {
        if (groomingPath == null || groomingPath.isEmpty()) {
            File excelFile = new File(excelPath);
            groomingPath = excelFile.getParent() + File.separator + "grooming.json";
        }
    }

    private List<GroomingRecord> readAll() throws IOException {
        File file = new File(groomingPath);
        if (!file.exists()) {
            return new ArrayList<>();
        }
        return objectMapper.readValue(file, new TypeReference<List<GroomingRecord>>() {});
    }

    private void saveAll(List<GroomingRecord> records) throws IOException {
        objectMapper.writeValue(new File(groomingPath), records);
    }

    public GroomingRecord getRecord(LocalDate date) throws IOException {
        return readAll().stream()
                .filter(r -> date.equals(r.getDate()))
                .findFirst()
                .orElse(null);
    }

    public GroomingRecord togglePart(LocalDate date, String part) throws IOException {
        if (!GroomingRecord.ALL_PARTS.contains(part)) {
            throw new IllegalArgumentException("Invalid part: " + part);
        }

        List<GroomingRecord> records = readAll();
        GroomingRecord record = records.stream()
                .filter(r -> date.equals(r.getDate()))
                .findFirst()
                .orElse(null);

        if (record == null) {
            record = new GroomingRecord();
            record.setDate(date);
            record.setCompletedParts(new HashSet<>());
            records.add(record);
        }

        if (record.getCompletedParts().contains(part)) {
            record.getCompletedParts().remove(part);
        } else {
            record.getCompletedParts().add(part);
        }

        saveAll(records);
        return record;
    }

    public double calculateGroomingPoints(LocalDate date) throws IOException {
        GroomingRecord record = getRecord(date);
        return record != null ? record.getPoints() : 0.0;
    }
}
