package com.bitfox.health.health.service;

import com.bitfox.health.health.model.Task;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TaskService {

    @Value("${health.tasks.file:src/main/resources/tasks.json}")
    private String tasksFile;

    private final ObjectMapper objectMapper;

    public TaskService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        // 配置日期序列化为ISO字符串格式而不是数组
        this.objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public List<Task> getAllTasks() throws IOException {
        File file = new File(tasksFile);
        if (!file.exists()) {
            return new ArrayList<>();
        }
        return objectMapper.readValue(file, new TypeReference<List<Task>>() {});
    }

    public List<Task> getTasksByDate(LocalDate date) throws IOException {
        return getAllTasks().stream()
                .filter(task -> task.getDate() != null && task.getDate().equals(date))
                .collect(Collectors.toList());
    }

    public void saveTasks(List<Task> tasks) throws IOException {
        File file = new File(tasksFile);
        file.getParentFile().mkdirs();
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, tasks);
    }

    public Task addTask(LocalDate date, String description) throws IOException {
        return addTask(date, description, null);
    }

    public Task addTask(LocalDate date, String description, Double points) throws IOException {
        List<Task> tasks = getAllTasks();
        Task newTask = new Task(date, description);
        if (points != null && points > 0) {
            newTask.setPoints(points);
        }
        tasks.add(newTask);
        saveTasks(tasks);
        return newTask;
    }

    public void deleteTask(String taskId) throws IOException {
        List<Task> tasks = getAllTasks();
        Task task = tasks.stream()
                .filter(t -> t.getId().equals(taskId))
                .findFirst()
                .orElse(null);

        if (task != null && task.getOriginalDate() != null) {
            // 如果有原始日期，恢复到原始日期而不是删除
            task.setDate(task.getOriginalDate());
            task.setOriginalDate(null);  // 清除原始日期标记
            task.setCompleted(false);  // 重置完成状态
        } else {
            // 没有原始日期，正常删除
            tasks.removeIf(t -> t.getId().equals(taskId));
        }

        saveTasks(tasks);
    }

    public void toggleTaskCompletion(String taskId) throws IOException {
        List<Task> tasks = getAllTasks();
        tasks.stream()
                .filter(task -> task.getId().equals(taskId))
                .findFirst()
                .ifPresent(task -> task.setCompleted(!task.isCompleted()));
        saveTasks(tasks);
    }

    public double calculateTaskPoints(LocalDate date) throws IOException {
        List<Task> tasks = getTasksByDate(date);
        return tasks.stream()
                .filter(Task::isCompleted)
                .mapToDouble(task -> task.getPoints() != null ? task.getPoints() : 0.1)
                .sum();
    }

    public int getCompletedTaskCount(LocalDate date) throws IOException {
        List<Task> tasks = getTasksByDate(date);
        return (int) tasks.stream().filter(Task::isCompleted).count();
    }

    // ========== 重复任务管理 ==========

    /**
     * 创建重复任务
     */
    public Task addRecurringTask(String description, Task.RecurrenceType recurrenceType, Integer recurrenceDay, Double points) throws IOException {
        List<Task> tasks = getAllTasks();
        Task newTask = new Task();
        newTask.setId(java.util.UUID.randomUUID().toString());
        newTask.setDescription(description);
        newTask.setRecurrenceType(recurrenceType);
        newTask.setRecurrenceDay(recurrenceDay);
        newTask.setCompleted(false);
        newTask.setPoints(points != null ? points : 0.1);  // 设置自定义积分，默认0.1
        // 重复任务模板不设置具体日期
        newTask.setDate(null);
        tasks.add(newTask);
        saveTasks(tasks);
        return newTask;
    }

    /**
     * 获取所有重复任务模板
     */
    public List<Task> getRecurringTasks() throws IOException {
        return getAllTasks().stream()
                .filter(Task::isRecurring)
                .filter(task -> task.getDate() == null)  // 模板任务没有具体日期
                .collect(Collectors.toList());
    }

    /**
     * 生成重复任务实例
     * 提前一天生成，所以targetDate是明天
     */
    public void generateRecurringTasksForDate(LocalDate targetDate) throws IOException {
        List<Task> allTasks = getAllTasks();
        List<Task> recurringTemplates = getRecurringTasks();

        for (Task template : recurringTemplates) {
            // 检查是否应该在targetDate生成任务
            if (shouldGenerateTask(template, targetDate)) {
                // 检查是否已经生成过
                boolean alreadyExists = allTasks.stream()
                        .anyMatch(t -> t.getDate() != null &&
                                      t.getDate().equals(targetDate) &&
                                      template.getId().equals(t.getParentTaskId()));

                if (!alreadyExists) {
                    // 生成新任务
                    Task newTask = new Task();
                    newTask.setId(java.util.UUID.randomUUID().toString());
                    newTask.setDate(targetDate);
                    newTask.setDescription(template.getDescription());
                    newTask.setCompleted(false);
                    newTask.setRecurrenceType(Task.RecurrenceType.ONCE);
                    newTask.setParentTaskId(template.getId());
                    newTask.setPoints(template.getPoints() != null ? template.getPoints() : 0.1);  // 继承模板的积分
                    allTasks.add(newTask);
                }
            }
        }

        saveTasks(allTasks);
    }

    /**
     * 判断是否应该在指定日期生成任务
     */
    private boolean shouldGenerateTask(Task template, LocalDate targetDate) {
        if (template.getRecurrenceType() == Task.RecurrenceType.WEEKLY) {
            // 每周重复：检查是周几
            int dayOfWeek = targetDate.getDayOfWeek().getValue();  // 1=Monday, 7=Sunday
            return dayOfWeek == template.getRecurrenceDay();
        } else if (template.getRecurrenceType() == Task.RecurrenceType.MONTHLY) {
            // 每月重复：检查是几号
            int dayOfMonth = targetDate.getDayOfMonth();
            return dayOfMonth == template.getRecurrenceDay();
        }
        return false;
    }

    /**
     * 删除重复任务模板
     */
    public void deleteRecurringTask(String taskId) throws IOException {
        List<Task> tasks = getAllTasks();
        // 删除模板
        tasks.removeIf(task -> task.getId().equals(taskId));
        // 删除所有从这个模板生成的未完成任务
        tasks.removeIf(task -> taskId.equals(task.getParentTaskId()) && !task.isCompleted());
        saveTasks(tasks);
    }

    // ========== 历史任务管理 ==========

    /**
     * 获取历史未完成任务（排除重复任务）
     * 只返回过去日期的未完成任务，不包括从重复任务模板生成的任务
     */
    public List<Task> getUncompletedHistoryTasks(LocalDate beforeDate) throws IOException {
        return getAllTasks().stream()
                .filter(task -> task.getDate() != null)  // 有具体日期
                .filter(task -> task.getDate().isBefore(beforeDate))  // 过去的日期
                .filter(task -> !task.isCompleted())  // 未完成
                .filter(task -> task.getParentTaskId() == null)  // 不是从重复任务生成的
                .sorted((t1, t2) -> t2.getDate().compareTo(t1.getDate()))  // 按日期倒序
                .collect(Collectors.toList());
    }

    /**
     * 将任务移动到指定日期（复用任务而非复制）
     */
    public Task copyTaskToDate(String taskId, LocalDate targetDate) throws IOException {
        List<Task> tasks = getAllTasks();
        Task task = tasks.stream()
                .filter(t -> t.getId().equals(taskId))
                .findFirst()
                .orElse(null);

        if (task == null) {
            return null;
        }

        // 保存原始日期（如果还没有保存过）
        if (task.getOriginalDate() == null) {
            task.setOriginalDate(task.getDate());
        }

        // 直接修改任务的日期，复用原任务
        task.setDate(targetDate);
        task.setCompleted(false);  // 重置完成状态

        saveTasks(tasks);
        return task;
    }
}
