package com.bitfox.health.health.controller;

import com.bitfox.health.health.model.Journal;
import com.bitfox.health.health.service.JournalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/journal")
public class JournalController {

    // 日期边界时间：凌晨6点之前算前一天
    private static final int DAY_BOUNDARY_HOUR = 6;

    @Autowired
    private JournalService journalService;

    /**
     * 获取有效日期：如果当前时间在凌晨6点之前，返回昨天的日期，否则返回今天的日期
     */
    private LocalDate getEffectiveDate() {
        LocalTime now = LocalTime.now();
        LocalDate today = LocalDate.now();

        if (now.isBefore(LocalTime.of(DAY_BOUNDARY_HOUR, 0))) {
            return today.minusDays(1);
        }

        return today;
    }

    /**
     * 日记列表页面
     */
    @GetMapping("")
    public String index(
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String keyword,
            Model model) {
        try {
            List<Journal> journals;

            if (tag != null && !tag.isEmpty()) {
                journals = journalService.searchByTag(tag);
                model.addAttribute("currentTag", tag);
            } else if (keyword != null && !keyword.isEmpty()) {
                journals = journalService.search(keyword);
                model.addAttribute("keyword", keyword);
            } else {
                journals = journalService.getAllJournals();
            }

            model.addAttribute("journals", journals);
            model.addAttribute("allTags", journalService.getAllTags());
            model.addAttribute("today", getEffectiveDate());

        } catch (IOException e) {
            model.addAttribute("error", "读取日记失败: " + e.getMessage());
        }

        return "journal";
    }

    /**
     * 新建日记页面
     */
    @GetMapping("/new")
    public String newJournal(Model model) {
        Journal journal = new Journal();
        journal.setDate(getEffectiveDate());
        journal.setTitle("");
        journal.setContent("");

        model.addAttribute("journal", journal);
        model.addAttribute("isNew", true);

        try {
            model.addAttribute("allTags", journalService.getAllTags());
        } catch (IOException e) {
            model.addAttribute("error", "读取标签失败: " + e.getMessage());
        }

        return "journal-edit";
    }

    /**
     * 编辑日记页面
     */
    @GetMapping("/edit/{id}")
    public String editJournal(@PathVariable String id, Model model) {
        Journal journal = journalService.getJournalById(id);

        if (journal == null) {
            model.addAttribute("error", "日记不存在");
            return "redirect:/journal";
        }

        model.addAttribute("journal", journal);
        model.addAttribute("isNew", false);

        try {
            model.addAttribute("allTags", journalService.getAllTags());
        } catch (IOException e) {
            model.addAttribute("error", "读取标签失败: " + e.getMessage());
        }

        return "journal-edit";
    }

    /**
     * 保存日记
     */
    @PostMapping("/save")
    public String saveJournal(
            @RequestParam(required = false) String id,
            @RequestParam String title,
            @RequestParam String content,
            @RequestParam String date,
            @RequestParam(required = false) String tags,
            Model model) {

        try {
            Journal journal;

            if (id != null && !id.isEmpty()) {
                // 更新现有日记
                journal = journalService.getJournalById(id);
                if (journal == null) {
                    model.addAttribute("error", "日记不存在");
                    return "redirect:/journal";
                }
            } else {
                // 创建新日记
                journal = new Journal();
            }

            journal.setTitle(title);
            journal.setContent(content);
            journal.setDate(LocalDate.parse(date));

            // 解析标签
            if (tags != null && !tags.isEmpty()) {
                List<String> tagList = Arrays.stream(tags.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
                journal.setTags(tagList);
            }

            journalService.saveJournal(journal);
            model.addAttribute("success", "保存成功！");

        } catch (IOException e) {
            model.addAttribute("error", "保存失败: " + e.getMessage());
        }

        return "redirect:/journal";
    }

    /**
     * 删除日记
     */
    @PostMapping("/delete/{id}")
    public String deleteJournal(@PathVariable String id, Model model) {
        try {
            journalService.deleteJournal(id);
            model.addAttribute("success", "删除成功！");
        } catch (IOException e) {
            model.addAttribute("error", "删除失败: " + e.getMessage());
        }

        return "redirect:/journal";
    }

    /**
     * 查看日记详情
     */
    @GetMapping("/view/{id}")
    public String viewJournal(@PathVariable String id, Model model) {
        Journal journal = journalService.getJournalById(id);

        if (journal == null) {
            model.addAttribute("error", "日记不存在");
            return "redirect:/journal";
        }

        model.addAttribute("journal", journal);
        return "journal-view";
    }
}
