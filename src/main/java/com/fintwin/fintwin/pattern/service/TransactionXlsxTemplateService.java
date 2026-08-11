package com.fintwin.fintwin.pattern.service;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Service
public class TransactionXlsxTemplateService {
    private static final List<String> HEADERS = List.of(
            "transactionDate", "type", "amount", "category", "description", "transactionId");

    public byte[] createTemplate() {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("transactions");
            Row header = sheet.createRow(0);
            CellStyle style = workbook.createCellStyle();
            style.setFillForegroundColor(IndexedColors.DARK_GREEN.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font font = workbook.createFont();
            font.setBold(true);
            font.setColor(IndexedColors.WHITE.getIndex());
            style.setFont(font);
            for (int index = 0; index < HEADERS.size(); index++) {
                header.createCell(index).setCellValue(HEADERS.get(index));
                header.getCell(index).setCellStyle(style);
            }
            sheet.createFreezePane(0, 1);
            sheet.setColumnWidth(0, 14 * 256);
            sheet.setColumnWidth(1, 22 * 256);
            sheet.setColumnWidth(2, 16 * 256);
            sheet.setColumnWidth(3, 22 * 256);
            sheet.setColumnWidth(4, 34 * 256);
            sheet.setColumnWidth(5, 22 * 256);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("XLSX template generation failed");
        }
    }
}
