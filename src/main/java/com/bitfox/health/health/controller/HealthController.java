package com.bitfox.health.health.controller;

import com.bitfox.health.health.model.GroomingRecord;
import com.bitfox.health.health.model.HealthRecord;
import com.bitfox.health.health.model.Task;
import com.bitfox.health.health.model.WeeklyStats;
import com.bitfox.health.health.service.ExcelService;
import com.bitfox.health.health.service.GroomingService;
import com.bitfox.health.health.service.RewardService;
import com.bitfox.health.health.service.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class HealthController {

    // 日期边界时间：凌晨6点之前算前一天
    private static final int DAY_BOUNDARY_HOUR = 6;

    /**
     * 获取有效日期：如果当前时间在凌晨6点之前，返回昨天的日期，否则返回今天的日期
     */
    private LocalDate getEffectiveDate() {
        LocalTime now = LocalTime.now();
        LocalDate today = LocalDate.now();

        // 如果当前时间在凌晨6点之前，返回昨天的日期
        if (now.isBefore(LocalTime.of(DAY_BOUNDARY_HOUR, 0))) {
            return today.minusDays(1);
        }

        return today;
    }

    @Autowired
    private ExcelService excelService;

    @Autowired
    private RewardService rewardService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private GroomingService groomingService;

    @GetMapping("/")
    public String index(Model model) {
        try {
            // 生成重复任务（每次访问首页时检查）
            taskService.generateRecurringTasksForDate(getEffectiveDate());
            taskService.generateRecurringTasksForDate(getEffectiveDate().plusDays(1));

            List<HealthRecord> records = excelService.readAllRecords();

            // 反转记录顺序，最新的在前
            Collections.reverse(records);

            // 为每条记录计算体脂率积分（需要前一天的数据）
            enrichRecordsWithBodyFatPoints(records);

            model.addAttribute("records", records);
            model.addAttribute("today", getEffectiveDate());

            // 加载今天的记录（如果存在）
            HealthRecord todayRecord = excelService.getTodayRecord(getEffectiveDate());
            if (todayRecord != null) {
                model.addAttribute("todayRecord", todayRecord);
            }

            // 获取昨天的体脂率
            Double yesterdayBodyFat = getYesterdayBodyFat(records);
            model.addAttribute("yesterdayBodyFat", yesterdayBodyFat != null ? yesterdayBodyFat : 0.0);

            // 计算总积分和奖励进度
            double totalPoints = records.stream()
                    .mapToDouble(r -> {
                        try {
                            return Double.parseDouble(r.getDailyTotal());
                        } catch (Exception e) {
                            return 0.0;
                        }
                    })
                    .sum();

            int availablePoints = rewardService.getAvailablePoints(totalPoints);
            double progress = rewardService.getRewardProgress(totalPoints);
            int completedRewards = rewardService.getCompletedRewards(totalPoints);

            model.addAttribute("totalPoints", totalPoints);
            model.addAttribute("availablePoints", availablePoints);
            model.addAttribute("rewardProgress", progress);
            model.addAttribute("completedRewards", completedRewards);
            model.addAttribute("canConsumeReward", completedRewards > 0);

            // 计算上周统计
            WeeklyStats lastWeekStats = calculateLastWeekStats(records);
            model.addAttribute("lastWeekStats", lastWeekStats);

            // 加载今天的任务
            List<Task> todayTasks = taskService.getTasksByDate(getEffectiveDate());
            model.addAttribute("todayTasks", todayTasks);

            // 计算今天任务的积分
            double todayTaskPoints = taskService.calculateTaskPoints(getEffectiveDate());
            model.addAttribute("todayTaskPoints", todayTaskPoints);

            // 加载明天的任务
            List<Task> tomorrowTasks = taskService.getTasksByDate(getEffectiveDate().plusDays(1));
            model.addAttribute("tomorrowTasks", tomorrowTasks);

            // 加载今天的个人整洁记录
            GroomingRecord groomingRecord = groomingService.getRecord(getEffectiveDate());
            model.addAttribute("groomingRecord", groomingRecord);
            model.addAttribute("groomingParts", GroomingRecord.ALL_PARTS);
            double groomingPoints = groomingService.calculateGroomingPoints(getEffectiveDate());
            model.addAttribute("groomingPoints", groomingPoints);

            // 构建历史记录的个人整洁积分 map
            java.util.Map<LocalDate, Double> groomingPointsMap = new java.util.HashMap<>();
            java.util.Map<LocalDate, Integer> groomingCountMap = new java.util.HashMap<>();
            for (HealthRecord r : records) {
                if (r.getDate() != null) {
                    try {
                        GroomingRecord gr = groomingService.getRecord(r.getDate());
                        groomingPointsMap.put(r.getDate(), groomingService.calculateGroomingPoints(r.getDate()));
                        groomingCountMap.put(r.getDate(), gr != null ? gr.getCompletedParts().size() : 0);
                    } catch (IOException ignored) {}
                }
            }
            model.addAttribute("groomingPointsMap", groomingPointsMap);
            model.addAttribute("groomingCountMap", groomingCountMap);

        } catch (IOException e) {
            model.addAttribute("error", "读取Excel文件失败: " + e.getMessage());
        }
        return "index";
    }

    private WeeklyStats calculateLastWeekStats(List<HealthRecord> records) {
        WeekFields weekFields = WeekFields.of(Locale.CHINA);
        LocalDate now = getEffectiveDate();
        LocalDate lastWeekStart = now.minusWeeks(1).with(weekFields.dayOfWeek(), 1);
        LocalDate lastWeekEnd = lastWeekStart.plusDays(6);

        // 筛选上周的记录
        List<HealthRecord> lastWeekRecords = records.stream()
                .filter(r -> r.getDate() != null)
                .filter(r -> !r.getDate().isBefore(lastWeekStart) && !r.getDate().isAfter(lastWeekEnd))
                .collect(Collectors.toList());

        WeeklyStats stats = new WeeklyStats();
        stats.setWeekLabel("上周");
        stats.setRecordCount(lastWeekRecords.size());

        if (lastWeekRecords.isEmpty()) {
            stats.setTotalPoints(0);
            return stats;
        }

        // 计算总积分
        double totalPoints = lastWeekRecords.stream()
                .mapToDouble(r -> {
                    try {
                        return Double.parseDouble(r.getDailyTotal());
                    } catch (Exception e) {
                        return 0.0;
                    }
                })
                .sum();
        stats.setTotalPoints(totalPoints);

        // 计算各项平均值
        stats.addAverage("饮食1", calculateAverage(lastWeekRecords, HealthRecord::getDiet1));
        stats.addAverage("饮食2", calculateAverage(lastWeekRecords, HealthRecord::getDiet2));
        stats.addAverage("睡眠", calculateAverage(lastWeekRecords, HealthRecord::getSleep));
        stats.addAverage("清心寡欲", calculateAverage(lastWeekRecords, HealthRecord::getSelfControl));
        stats.addAverage("健身", calculateAverage(lastWeekRecords, HealthRecord::getFitness));
        stats.addAverage("工作积分", calculateAverage(lastWeekRecords, HealthRecord::getWorkPoints));
        stats.addAverage("学习积分", calculateAverage(lastWeekRecords, HealthRecord::getStudyPoints));
        stats.addAverage("体脂率积分", calculateAverageDouble(lastWeekRecords, HealthRecord::getBodyFatPoints));

        // 计算个人整洁积分平均值
        double groomingTotal = 0;
        int groomingDays = 0;
        for (HealthRecord r : lastWeekRecords) {
            if (r.getDate() != null) {
                try {
                    double gp = groomingService.calculateGroomingPoints(r.getDate());
                    groomingTotal += gp;
                    groomingDays++;
                } catch (IOException ignored) {}
            }
        }
        stats.addAverage("个人整洁", groomingDays > 0 ? groomingTotal / groomingDays : 0.0);

        return stats;
    }

    private double calculateAverage(List<HealthRecord> records, java.util.function.Function<HealthRecord, String> getter) {
        return records.stream()
                .map(getter)
                .filter(s -> s != null && !s.isEmpty())
                .mapToDouble(s -> {
                    try {
                        return Double.parseDouble(s);
                    } catch (Exception e) {
                        return 0.0;
                    }
                })
                .average()
                .orElse(0.0);
    }

    private double calculateAverageDouble(List<HealthRecord> records, java.util.function.Function<HealthRecord, Double> getter) {
        return records.stream()
                .map(getter)
                .filter(d -> d != null)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }

    private Double getYesterdayBodyFat(List<HealthRecord> records) {
        LocalDate yesterday = getEffectiveDate().minusDays(1);
        return records.stream()
                .filter(r -> r.getDate() != null && r.getDate().equals(yesterday))
                .findFirst()
                .map(HealthRecord::getBodyFatPercentage)
                .orElse(null);
    }

    private void enrichRecordsWithBodyFatPoints(List<HealthRecord> records) {
        // 创建日期到记录的映射
        Map<LocalDate, HealthRecord> recordMap = records.stream()
                .filter(r -> r.getDate() != null)
                .collect(Collectors.toMap(HealthRecord::getDate, r -> r, (a, b) -> a));

        // 为每条记录计算体脂率积分
        for (HealthRecord record : records) {
            if (record.getDate() != null && record.getBodyFatPercentage() != null) {
                LocalDate previousDay = record.getDate().minusDays(1);
                HealthRecord previousRecord = recordMap.get(previousDay);

                if (previousRecord != null && previousRecord.getBodyFatPercentage() != null) {
                    double points = (previousRecord.getBodyFatPercentage() - record.getBodyFatPercentage()) * 10;
                    record.setCalculatedBodyFatPoints(points);
                }
            }
        }
    }

    @PostMapping("/save")
    public String saveRecord(
            @RequestParam(required = false) String diet1,
            @RequestParam(required = false) String diet2,
            @RequestParam(required = false) String sleep,
            @RequestParam(required = false) String selfControl,
            @RequestParam(required = false) String fitness,
            @RequestParam(required = false) String workPoints,
            @RequestParam(required = false) String studyPoints,
            @RequestParam(required = false) String bodyFat,
            @RequestParam(required = false) String dailyTotal,
            Model model) {

        HealthRecord record = new HealthRecord();
        record.setDate(getEffectiveDate());
        record.setDiet1(diet1 != null ? diet1 : "");
        record.setDiet2(diet2 != null ? diet2 : "");
        record.setSleep(sleep != null ? sleep : "");
        record.setSelfControl(selfControl != null ? selfControl : "");
        record.setFitness(fitness != null ? fitness : "");
        record.setWorkPoints(workPoints != null ? workPoints : "");
        record.setStudyPoints(studyPoints != null ? studyPoints : "");
        record.setBodyFat(bodyFat != null ? bodyFat : "");
        record.setDailyTotal(dailyTotal != null ? dailyTotal : "");

        try {
            excelService.saveRecord(record);
            model.addAttribute("success", "保存成功！");
        } catch (IOException e) {
            model.addAttribute("error", "保存失败: " + e.getMessage());
        }

        return "redirect:/";
    }

    @PostMapping("/consume-reward")
    public String consumeReward(Model model) {
        try {
            rewardService.consumeReward();
            model.addAttribute("success", "奖励已消耗！60积分已扣除。");
        } catch (IOException e) {
            model.addAttribute("error", "消耗奖励失败: " + e.getMessage());
        }
        return "redirect:/";
    }

    @PostMapping("/tasks/add")
    @org.springframework.web.bind.annotation.ResponseBody
    public java.util.Map<String, Object> addTask(
            @RequestParam String description,
            @RequestParam(required = false, defaultValue = "tomorrow") String targetDate,
            @RequestParam(required = false) Double points) {
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        try {
            LocalDate date;
            if ("today".equals(targetDate)) {
                date = getEffectiveDate();
            } else {
                date = getEffectiveDate().plusDays(1);  // 默认明天
            }
            Task newTask = taskService.addTask(date, description, points);
            response.put("success", true);
            response.put("message", "任务已添加！");
            response.put("taskId", newTask.getId());
            response.put("points", newTask.getPoints());
        } catch (IOException e) {
            response.put("success", false);
            response.put("message", "添加任务失败: " + e.getMessage());
        }
        return response;
    }

    @PostMapping("/tasks/delete")
    @org.springframework.web.bind.annotation.ResponseBody
    public java.util.Map<String, Object> deleteTask(@RequestParam String taskId) {
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        try {
            taskService.deleteTask(taskId);
            response.put("success", true);
            response.put("message", "任务已删除！");
        } catch (IOException e) {
            response.put("success", false);
            response.put("message", "删除任务失败: " + e.getMessage());
        }
        return response;
    }

    @PostMapping("/tasks/toggle")
    public String toggleTask(@RequestParam String taskId, Model model) {
        try {
            taskService.toggleTaskCompletion(taskId);
            model.addAttribute("success", "任务状态已更新！");
        } catch (IOException e) {
            model.addAttribute("error", "更新任务状态失败: " + e.getMessage());
        }
        return "redirect:/";
    }

    @PostMapping("/grooming/toggle")
    @org.springframework.web.bind.annotation.ResponseBody
    public java.util.Map<String, Object> toggleGrooming(@RequestParam String part) {
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        try {
            GroomingRecord record = groomingService.togglePart(getEffectiveDate(), part);
            response.put("success", true);
            response.put("completedParts", record.getCompletedParts());
            response.put("allCompleted", record.isAllCompleted());
            response.put("points", record.getPoints());
        } catch (IOException e) {
            response.put("success", false);
            response.put("message", "更新失败: " + e.getMessage());
        }
        return response;
    }

    // ========== 重复任务管理 ==========

    @GetMapping("/recurring-tasks")
    public String recurringTasks(Model model) {
        try {
            List<Task> recurringTasks = taskService.getRecurringTasks();
            model.addAttribute("recurringTasks", recurringTasks);
        } catch (IOException e) {
            model.addAttribute("error", "读取重复任务失败: " + e.getMessage());
        }
        return "recurring-tasks";
    }

    @PostMapping("/recurring-tasks/add")
    @org.springframework.web.bind.annotation.ResponseBody
    public java.util.Map<String, Object> addRecurringTask(
            @RequestParam String description,
            @RequestParam String recurrenceType,
            @RequestParam Integer recurrenceDay,
            @RequestParam(required = false) Double points) {
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        try {
            Task.RecurrenceType type = Task.RecurrenceType.valueOf(recurrenceType);
            Task newTask = taskService.addRecurringTask(description, type, recurrenceDay, points);
            response.put("success", true);
            response.put("message", "重复任务已添加！");
            response.put("taskId", newTask.getId());
        } catch (IOException e) {
            response.put("success", false);
            response.put("message", "添加重复任务失败: " + e.getMessage());
        }
        return response;
    }

    @PostMapping("/recurring-tasks/delete")
    @org.springframework.web.bind.annotation.ResponseBody
    public java.util.Map<String, Object> deleteRecurringTask(@RequestParam String taskId) {
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        try {
            taskService.deleteRecurringTask(taskId);
            response.put("success", true);
            response.put("message", "重复任务已删除！");
        } catch (IOException e) {
            response.put("success", false);
            response.put("message", "删除重复任务失败: " + e.getMessage());
        }
        return response;
    }

    // ========== 历史任务管理 ==========

    @GetMapping("/tasks/uncompleted-history")
    @org.springframework.web.bind.annotation.ResponseBody
    public java.util.List<Task> getUncompletedHistoryTasks() {
        try {
            LocalDate today = getEffectiveDate();
            return taskService.getUncompletedHistoryTasks(today);
        } catch (IOException e) {
            return new java.util.ArrayList<>();
        }
    }

    @PostMapping("/tasks/copy-to-date")
    @org.springframework.web.bind.annotation.ResponseBody
    public java.util.Map<String, Object> copyTaskToDate(
            @RequestParam String taskId,
            @RequestParam String targetDate) {
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        try {
            LocalDate date = LocalDate.parse(targetDate);
            Task newTask = taskService.copyTaskToDate(taskId, date);
            if (newTask != null) {
                response.put("success", true);
                response.put("message", "任务已添加到 " + targetDate);
                response.put("taskId", newTask.getId());
            } else {
                response.put("success", false);
                response.put("message", "找不到原任务");
            }
        } catch (IOException e) {
            response.put("success", false);
            response.put("message", "复制任务失败: " + e.getMessage());
        }
        return response;
    }
}
