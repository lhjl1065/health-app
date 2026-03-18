package com.bitfox.health.health.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDate;

/**
 * 重复任务生成器
 * 在应用启动时自动生成重复任务
 */
@Component
public class RecurringTaskGenerator {

    @Autowired
    private TaskService taskService;

    /**
     * 应用启动时执行
     */
    @EventListener(ApplicationReadyEvent.class)
    public void generateTasksOnStartup() {
        try {
            // 生成今天和明天的重复任务
            LocalDate today = LocalDate.now();
            LocalDate tomorrow = today.plusDays(1);

            taskService.generateRecurringTasksForDate(today);
            taskService.generateRecurringTasksForDate(tomorrow);

            System.out.println("重复任务生成完成：今天和明天");
        } catch (IOException e) {
            System.err.println("生成重复任务失败: " + e.getMessage());
        }
    }

    /**
     * 每天生成明天的重复任务
     * 可以通过定时任务调用，或者在每次访问首页时调用
     */
    public void generateDailyTasks() {
        try {
            LocalDate tomorrow = LocalDate.now().plusDays(1);
            taskService.generateRecurringTasksForDate(tomorrow);
        } catch (IOException e) {
            System.err.println("生成每日重复任务失败: " + e.getMessage());
        }
    }
}
