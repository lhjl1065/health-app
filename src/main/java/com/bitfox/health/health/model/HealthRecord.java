package com.bitfox.health.health.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HealthRecord {
    private LocalDate date;
    private String diet1;        // 饮食1
    private String diet2;        // 饮食2
    private String sleep;        // 睡眠
    private String selfControl;  // 清心寡欲
    private String fitness;      // 健身
    private String workPoints;   // 工作积分
    private String studyPoints;  // 学习积分
    private String bodyFat;      // 体脂率
    private String dailyTotal;   // 今日累计

    // 用于显示的体脂率积分（基于前一天对比）
    private transient Double calculatedBodyFatPoints;

    // 获取体脂率百分比（直接返回存储的值）
    public Double getBodyFatPercentage() {
        try {
            return Double.parseDouble(bodyFat);
        } catch (Exception e) {
            return null;
        }
    }

    // 获取体脂率积分（如果已计算则返回，否则返回null）
    public Double getBodyFatPoints() {
        return calculatedBodyFatPoints;
    }

    // 设置体脂率积分
    public void setCalculatedBodyFatPoints(Double points) {
        this.calculatedBodyFatPoints = points;
    }
}
