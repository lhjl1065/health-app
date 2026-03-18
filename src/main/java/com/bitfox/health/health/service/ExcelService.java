package com.bitfox.health.health.service;

import com.bitfox.health.health.model.HealthRecord;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class ExcelService {

    @Value("${health.excel.path}")
    private String excelPath;

    private static final DateTimeFormatter CHINESE_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy年MM月dd日");

    public List<HealthRecord> readAllRecords() throws IOException {
        List<HealthRecord> records = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(excelPath);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                HealthRecord record = new HealthRecord();

                Cell dateCell = row.getCell(0);
                if (dateCell != null) {
                    if (dateCell.getCellType() == CellType.NUMERIC) {
                        Date date = dateCell.getDateCellValue();
                        record.setDate(date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
                    } else if (dateCell.getCellType() == CellType.STRING) {
                        String dateStr = dateCell.getStringCellValue();
                        record.setDate(LocalDate.parse(dateStr, CHINESE_DATE_FORMATTER));
                    }
                }

                record.setDiet1(getCellValueAsString(row.getCell(1)));
                record.setDiet2(getCellValueAsString(row.getCell(2)));
                record.setSleep(getCellValueAsString(row.getCell(3)));
                record.setSelfControl(getCellValueAsString(row.getCell(4)));
                record.setFitness(getCellValueAsString(row.getCell(5)));
                record.setWorkPoints(getCellValueAsString(row.getCell(6)));
                record.setStudyPoints(getCellValueAsString(row.getCell(7)));
                record.setBodyFat(getCellValueAsString(row.getCell(8)));
                record.setDailyTotal(getCellValueAsString(row.getCell(9)));

                records.add(record);
            }
        }

        return records;
    }

    public HealthRecord getTodayRecord(LocalDate targetDate) throws IOException {
        List<HealthRecord> records = readAllRecords();

        for (HealthRecord record : records) {
            if (record.getDate() != null && record.getDate().equals(targetDate)) {
                return record;
            }
        }

        return null;
    }

    public void saveRecord(HealthRecord record) throws IOException {
        try (FileInputStream fis = new FileInputStream(excelPath);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            Row targetRow = null;
            int targetRowIndex = -1;

            // 查找今天的记录
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                Cell dateCell = row.getCell(0);
                LocalDate rowDate = null;

                if (dateCell != null) {
                    if (dateCell.getCellType() == CellType.NUMERIC) {
                        Date date = dateCell.getDateCellValue();
                        rowDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                    } else if (dateCell.getCellType() == CellType.STRING) {
                        String dateStr = dateCell.getStringCellValue();
                        rowDate = LocalDate.parse(dateStr, CHINESE_DATE_FORMATTER);
                    }
                }

                if (rowDate != null && rowDate.equals(record.getDate())) {
                    targetRow = row;
                    targetRowIndex = i;
                    break;
                }
            }

            // 如果没找到今天的记录，创建新行
            if (targetRow == null) {
                targetRowIndex = sheet.getLastRowNum() + 1;
                targetRow = sheet.createRow(targetRowIndex);
            }

            // 设置日期为中文格式
            Cell dateCell = targetRow.getCell(0);
            if (dateCell == null) {
                dateCell = targetRow.createCell(0);
            }
            dateCell.setCellValue(record.getDate().format(CHINESE_DATE_FORMATTER));

            // 设置其他字段
            setCellValue(targetRow, 1, record.getDiet1());
            setCellValue(targetRow, 2, record.getDiet2());
            setCellValue(targetRow, 3, record.getSleep());
            setCellValue(targetRow, 4, record.getSelfControl());
            setCellValue(targetRow, 5, record.getFitness());
            setCellValue(targetRow, 6, record.getWorkPoints());
            setCellValue(targetRow, 7, record.getStudyPoints());
            setCellValue(targetRow, 8, record.getBodyFat());
            setCellValue(targetRow, 9, record.getDailyTotal());

            try (FileOutputStream fos = new FileOutputStream(excelPath)) {
                workbook.write(fos);
            }
        }
    }

    private void setCellValue(Row row, int columnIndex, String value) {
        Cell cell = row.getCell(columnIndex);
        if (cell == null) {
            cell = row.createCell(columnIndex);
        }
        cell.setCellValue(value != null ? value : "");
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }
}
