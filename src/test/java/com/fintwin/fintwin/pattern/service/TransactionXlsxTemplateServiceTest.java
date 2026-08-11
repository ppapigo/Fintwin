package com.fintwin.fintwin.pattern.service;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.CellType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionXlsxTemplateServiceTest {
    @Test
    void createsAFormulaFreeStandardTransactionsWorkbook() throws Exception {
        byte[] bytes = new TransactionXlsxTemplateService().createTemplate();

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getNumberOfSheets()).isOne();
            assertThat(workbook.getSheetName(0)).isEqualTo("transactions");
            var header = workbook.getSheetAt(0).getRow(0);
            assertThat(List.of(
                    header.getCell(0).getStringCellValue(),
                    header.getCell(1).getStringCellValue(),
                    header.getCell(2).getStringCellValue(),
                    header.getCell(3).getStringCellValue(),
                    header.getCell(4).getStringCellValue(),
                    header.getCell(5).getStringCellValue()))
                    .containsExactly("transactionDate", "type", "amount", "category",
                            "description", "transactionId");
            assertThat(workbook.getSheetAt(0)).allSatisfy(row ->
                    assertThat(row).allSatisfy(cell -> assertThat(cell.getCellType()).isNotEqualTo(CellType.FORMULA)));
        }
    }
}
