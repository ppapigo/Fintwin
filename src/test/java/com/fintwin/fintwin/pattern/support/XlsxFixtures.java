package com.fintwin.fintwin.pattern.support;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class XlsxFixtures {
    public static final List<String> HEADERS = List.of(
            "transactionDate", "type", "amount", "category", "description", "transactionId");

    private XlsxFixtures() {
    }

    public static byte[] workbook(List<List<String>> rows) {
        return workbook(book -> {
            var sheet = book.createSheet("transactions");
            writeRow(sheet.createRow(0), HEADERS);
            for (int index = 0; index < rows.size(); index++) {
                writeRow(sheet.createRow(index + 1), rows.get(index));
            }
        });
    }

    public static byte[] workbook(Consumer<XSSFWorkbook> customizer) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            customizer.accept(workbook);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Synthetic workbook creation failed", exception);
        }
    }

    public static void writeRow(org.apache.poi.ss.usermodel.Row row, List<String> values) {
        for (int index = 0; index < values.size(); index++) {
            row.createCell(index).setCellValue(values.get(index));
        }
    }

    public static byte[] withArchiveEntry(byte[] source, String name, byte[] content) {
        try (ZipInputStream input = new ZipInputStream(new java.io.ByteArrayInputStream(source));
             ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ZipOutputStream output = new ZipOutputStream(bytes)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                output.putNextEntry(new ZipEntry(entry.getName()));
                input.transferTo(output);
                output.closeEntry();
            }
            output.putNextEntry(new ZipEntry(name));
            output.write(content);
            output.closeEntry();
            output.finish();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Synthetic workbook archive creation failed", exception);
        }
    }
}
