package com.bitfox.health.health.service;

import com.bitfox.health.health.model.Journal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class JournalService {

    @Value("${journal.directory}")
    private String journalDirectory;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * 获取所有日记
     */
    public List<Journal> getAllJournals() throws IOException {
        Path dir = Paths.get(journalDirectory);
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
            return new ArrayList<>();
        }

        try (Stream<Path> paths = Files.list(dir)) {
            return paths
                    .filter(path -> path.toString().endsWith(".md"))
                    .map(this::readJournal)
                    .filter(Objects::nonNull)
                    .sorted((a, b) -> b.getDate().compareTo(a.getDate()))  // 最新的在前
                    .collect(Collectors.toList());
        }
    }

    /**
     * 根据ID获取日记
     */
    public Journal getJournalById(String id) {
        Path filePath = Paths.get(journalDirectory, id + ".md");
        if (!Files.exists(filePath)) {
            return null;
        }
        return readJournal(filePath);
    }

    /**
     * 根据日期获取日记
     */
    public Journal getJournalByDate(LocalDate date) throws IOException {
        List<Journal> journals = getAllJournals();
        return journals.stream()
                .filter(j -> j.getDate().equals(date))
                .findFirst()
                .orElse(null);
    }

    /**
     * 根据标签搜索日记
     */
    public List<Journal> searchByTag(String tag) throws IOException {
        return getAllJournals().stream()
                .filter(j -> j.getTags().contains(tag))
                .collect(Collectors.toList());
    }

    /**
     * 搜索日记（标题或内容）
     */
    public List<Journal> search(String keyword) throws IOException {
        String lowerKeyword = keyword.toLowerCase();
        return getAllJournals().stream()
                .filter(j -> j.getTitle().toLowerCase().contains(lowerKeyword) ||
                        j.getContent().toLowerCase().contains(lowerKeyword))
                .collect(Collectors.toList());
    }

    /**
     * 保存日记
     */
    public void saveJournal(Journal journal) throws IOException {
        Path dir = Paths.get(journalDirectory);
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }

        // 如果是新日记，生成ID
        if (journal.getId() == null || journal.getId().isEmpty()) {
            journal.setId(generateId(journal.getDate(), journal.getTitle()));
            journal.setCreatedAt(LocalDateTime.now());
        }
        journal.setUpdatedAt(LocalDateTime.now());

        Path filePath = Paths.get(journalDirectory, journal.getId() + ".md");
        String content = formatJournal(journal);
        Files.writeString(filePath, content);
    }

    /**
     * 删除日记
     */
    public void deleteJournal(String id) throws IOException {
        Path filePath = Paths.get(journalDirectory, id + ".md");
        Files.deleteIfExists(filePath);
    }

    /**
     * 获取所有标签
     */
    public List<String> getAllTags() throws IOException {
        return getAllJournals().stream()
                .flatMap(j -> j.getTags().stream())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * 读取日记文件
     */
    private Journal readJournal(Path filePath) {
        try {
            String content = Files.readString(filePath);
            return parseJournal(filePath.getFileName().toString().replace(".md", ""), content);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 解析日记内容
     */
    private Journal parseJournal(String id, String content) {
        Journal journal = new Journal();
        journal.setId(id);

        // 解析frontmatter
        if (content.startsWith("---")) {
            int endIndex = content.indexOf("---", 3);
            if (endIndex > 0) {
                String frontmatter = content.substring(3, endIndex).trim();
                String body = content.substring(endIndex + 3).trim();

                // 解析frontmatter字段
                String[] lines = frontmatter.split("\n");
                for (String line : lines) {
                    String[] parts = line.split(":", 2);
                    if (parts.length == 2) {
                        String key = parts[0].trim();
                        String value = parts[1].trim();

                        switch (key) {
                            case "title" -> journal.setTitle(value);
                            case "date" -> journal.setDate(LocalDate.parse(value, DATE_FORMATTER));
                            case "tags" -> {
                                if (!value.isEmpty()) {
                                    List<String> tags = Arrays.stream(value.split(","))
                                            .map(String::trim)
                                            .filter(s -> !s.isEmpty())
                                            .collect(Collectors.toList());
                                    journal.setTags(tags);
                                }
                            }
                            case "created" -> journal.setCreatedAt(LocalDateTime.parse(value, DATETIME_FORMATTER));
                            case "updated" -> journal.setUpdatedAt(LocalDateTime.parse(value, DATETIME_FORMATTER));
                        }
                    }
                }

                journal.setContent(body);
            }
        } else {
            // 没有frontmatter，整个内容作为正文
            journal.setContent(content);
            // 从文件名推断日期
            if (id.matches("\\d{4}-\\d{2}-\\d{2}.*")) {
                journal.setDate(LocalDate.parse(id.substring(0, 10), DATE_FORMATTER));
            }
        }

        return journal;
    }

    /**
     * 格式化日记为文件内容
     */
    private String formatJournal(Journal journal) {
        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        sb.append("title: ").append(journal.getTitle()).append("\n");
        sb.append("date: ").append(journal.getDate().format(DATE_FORMATTER)).append("\n");
        sb.append("tags: ").append(String.join(", ", journal.getTags())).append("\n");
        sb.append("created: ").append(journal.getCreatedAt().format(DATETIME_FORMATTER)).append("\n");
        sb.append("updated: ").append(journal.getUpdatedAt().format(DATETIME_FORMATTER)).append("\n");
        sb.append("---\n\n");
        sb.append(journal.getContent());
        return sb.toString();
    }

    /**
     * 生成日记ID
     */
    private String generateId(LocalDate date, String title) {
        String dateStr = date.format(DATE_FORMATTER);
        String titleSlug = title.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5]+", "-")
                .replaceAll("^-|-$", "")
                .toLowerCase();
        if (titleSlug.length() > 50) {
            titleSlug = titleSlug.substring(0, 50);
        }
        return dateStr + "-" + titleSlug;
    }
}
