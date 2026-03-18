package com.bitfox.health.health.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Task {
    private String id;
    private LocalDate date;
    private String description;
    private boolean completed;
    private Double points;  // 任务积分，默认0.1
    private LocalDate originalDate;  // 原始日期，用于从历史任务移动后可以恢复

    // 重复任务相关字段
    private RecurrenceType recurrenceType;  // 重复类型：ONCE, WEEKLY, MONTHLY
    private Integer recurrenceDay;  // 重复日期：周几(1-7)或每月几号(1-31)
    private String parentTaskId;  // 如果是从重复任务生成的，记录父任务ID

    public Task(LocalDate date, String description) {
        this.id = java.util.UUID.randomUUID().toString();
        this.date = date;
        this.description = description;
        this.completed = false;
        this.recurrenceType = RecurrenceType.ONCE;
        this.points = 0.1;  // 默认0.1分
    }

    public boolean isRecurring() {
        return recurrenceType != null && recurrenceType != RecurrenceType.ONCE;
    }

    public enum RecurrenceType {
        ONCE,      // 一次性任务
        WEEKLY,    // 每周重复
        MONTHLY    // 每月重复
    }
}
