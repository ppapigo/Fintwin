package com.fintwin.fintwin.pattern.parser;

import com.fintwin.fintwin.global.error.XlsxValidationException;
import com.fintwin.fintwin.pattern.domain.FinancialPatternRules;
import com.fintwin.fintwin.pattern.domain.NormalizedTransaction;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.openxml4j.opc.TargetMode;
import org.apache.poi.poifs.filesystem.FileMagic;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

public final class TransactionXlsxParser {
    private static final String SHEET_NAME = "transactions";
    private static final int MAXIMUM_ZIP_ENTRIES = 128;
    private static final long MAXIMUM_UNCOMPRESSED_BYTES = 20L * 1024L * 1024L;
    private static final long MAXIMUM_ZIP_ENTRY_BYTES = 10L * 1024L * 1024L;
    private static final long MINIMUM_COMPRESSION_RATIO_CHECK_BYTES = 1024L * 1024L;
    private static final long MAXIMUM_COMPRESSION_RATIO = 100L;
    private static final int MAXIMUM_PHYSICAL_CELLS_PER_ROW = 16;
    private static final int MAXIMUM_BLANK_ROWS = 100;
    private static final int MAXIMUM_CELL_CHARACTERS = 256;
    private static final Set<String> BLOCKED_PART_PREFIXES = Set.of(
            "/xl/embeddings/", "/xl/activeX/", "/xl/ctrlProps/", "/xl/drawings/", "/xl/media/",
            "/customXml/");

    private final FinancialPatternRules rules;
    private final TransactionRecordNormalizer recordNormalizer;

    public TransactionXlsxParser(FinancialPatternRules rules) {
        this.rules = Objects.requireNonNull(rules);
        this.recordNormalizer = new TransactionRecordNormalizer(rules);
    }

    public List<NormalizedTransaction> parse(InputStream input, long declaredSize, LocalDate today) {
        Objects.requireNonNull(input);
        Objects.requireNonNull(today);
        validateDeclaredSize(declaredSize);
        byte[] bytes = readInput(input);
        validateMagic(bytes);
        validateZipEnvelope(bytes);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            validatePackage(workbook.getPackage());
            validateWorkbook(workbook);
            return parseSheet(workbook.getSheetAt(0), today);
        } catch (XlsxValidationException exception) {
            throw exception;
        } catch (EncryptedDocumentException exception) {
            throw error("XLSX_ENCRYPTED_WORKBOOK", null, "file", "Encrypted workbooks are not supported");
        } catch (IOException | RuntimeException exception) {
            throw error("XLSX_INVALID_WORKBOOK", null, "file", "Workbook structure is invalid");
        }
    }

    private void validateDeclaredSize(long declaredSize) {
        if (declaredSize <= 0) {
            throw error("XLSX_EMPTY_FILE", null, "file", "XLSX file must not be empty");
        }
        if (declaredSize > rules.maximumFileBytes()) {
            throw error("XLSX_FILE_TOO_LARGE", null, "file", "XLSX file exceeds the 2MB limit");
        }
    }

    private byte[] readInput(InputStream input) {
        try {
            byte[] bytes = input.readNBytes(Math.toIntExact(rules.maximumFileBytes()) + 1);
            if (bytes.length == 0) {
                throw error("XLSX_EMPTY_FILE", null, "file", "XLSX file must not be empty");
            }
            if (bytes.length > rules.maximumFileBytes()) {
                throw error("XLSX_FILE_TOO_LARGE", null, "file", "XLSX file exceeds the 2MB limit");
            }
            return bytes;
        } catch (XlsxValidationException exception) {
            throw exception;
        } catch (IOException exception) {
            throw error("XLSX_READ_FAILED", null, "file", "XLSX file could not be read");
        }
    }

    private void validateMagic(byte[] bytes) {
        try {
            InputStream prepared = FileMagic.prepareToCheckMagic(new ByteArrayInputStream(bytes));
            FileMagic magic = FileMagic.valueOf(prepared);
            if (magic == FileMagic.OLE2) {
                if (containsEncryptedPackage(bytes)) {
                    throw error("XLSX_ENCRYPTED_WORKBOOK", null, "file",
                            "Encrypted workbooks are not supported");
                }
                throw error("XLSX_INVALID_WORKBOOK", null, "file", "Legacy XLS files are not supported");
            }
            if (magic != FileMagic.OOXML) {
                throw error("XLSX_INVALID_WORKBOOK", null, "file", "File is not an OOXML workbook");
            }
        } catch (XlsxValidationException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw error("XLSX_INVALID_WORKBOOK", null, "file", "File is not a valid OOXML workbook");
        }
    }

    private boolean containsEncryptedPackage(byte[] bytes) {
        try (POIFSFileSystem fileSystem = new POIFSFileSystem(new ByteArrayInputStream(bytes))) {
            return fileSystem.getRoot().hasEntry("EncryptedPackage");
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    private void validateZipEnvelope(byte[] bytes) {
        int entryCount = 0;
        long totalUncompressed = 0;
        boolean contentTypes = false;
        boolean workbookPart = false;
        byte[] buffer = new byte[8192];
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > MAXIMUM_ZIP_ENTRIES) {
                    throw error("XLSX_ARCHIVE_LIMIT_EXCEEDED", null, "file",
                            "Workbook contains too many archive entries");
                }
                String entryName = entry.getName();
                String lowerEntryName = entryName.toLowerCase(Locale.ROOT);
                if (entryName.contains("..") || entryName.indexOf('\\') >= 0) {
                    throw error("XLSX_INVALID_WORKBOOK", null, "file", "Workbook archive is invalid");
                }
                if (lowerEntryName.contains("vbaproject")) {
                    throw error("XLSX_MACRO_NOT_ALLOWED", null, "file",
                            "Macro-enabled workbooks are not supported");
                }
                if (lowerEntryName.endsWith(".bin")) {
                    throw error("XLSX_EMBEDDED_OBJECT_NOT_ALLOWED", null, "file",
                            "Embedded binary workbook parts are not supported");
                }
                contentTypes |= "[Content_Types].xml".equals(entryName);
                workbookPart |= "xl/workbook.xml".equals(entryName);
                long entryBytes = 0;
                int read;
                while ((read = zip.read(buffer)) >= 0) {
                    entryBytes += read;
                    totalUncompressed += read;
                    if (entryBytes > MAXIMUM_ZIP_ENTRY_BYTES
                            || totalUncompressed > MAXIMUM_UNCOMPRESSED_BYTES) {
                        throw error("XLSX_ARCHIVE_LIMIT_EXCEEDED", null, "file",
                                "Workbook expands beyond the safe processing limit");
                    }
                }
                long compressedBytes = entry.getCompressedSize();
                if (entryBytes >= MINIMUM_COMPRESSION_RATIO_CHECK_BYTES
                        && compressedBytes > 0
                        && entryBytes / compressedBytes > MAXIMUM_COMPRESSION_RATIO) {
                    throw error("XLSX_ARCHIVE_LIMIT_EXCEEDED", null, "file",
                            "Workbook compression ratio exceeds the safe processing limit");
                }
            }
        } catch (XlsxValidationException exception) {
            throw exception;
        } catch (ZipException exception) {
            throw error("XLSX_INVALID_WORKBOOK", null, "file", "Workbook ZIP structure is invalid");
        } catch (IOException exception) {
            throw error("XLSX_READ_FAILED", null, "file", "Workbook archive could not be read");
        }
        if (!contentTypes || !workbookPart) {
            throw error("XLSX_INVALID_WORKBOOK", null, "file", "Required OOXML workbook parts are missing");
        }
    }

    private void validatePackage(OPCPackage packageFile) {
        try {
            for (PackageRelationship relationship : packageFile.getRelationships()) {
                rejectExternalRelationship(relationship);
            }
            for (PackagePart part : packageFile.getParts()) {
                if (part.isRelationshipPart()) {
                    continue;
                }
                String name = part.getPartName().getName();
                String lowerName = name.toLowerCase(Locale.ROOT);
                String lowerType = part.getContentType().toLowerCase(Locale.ROOT);
                if (lowerName.contains("vbaproject") || lowerType.contains("macroenabled")
                        || lowerType.contains("vba")) {
                    throw error("XLSX_MACRO_NOT_ALLOWED", null, "file",
                            "Macro-enabled workbooks are not supported");
                }
                if (BLOCKED_PART_PREFIXES.stream().anyMatch(lowerName::startsWith)) {
                    throw error("XLSX_EMBEDDED_OBJECT_NOT_ALLOWED", null, "file",
                            "Embedded objects and drawings are not supported");
                }
                for (PackageRelationship relationship : part.getRelationships()) {
                    rejectExternalRelationship(relationship);
                }
            }
        } catch (XlsxValidationException exception) {
            throw exception;
        } catch (InvalidFormatException | RuntimeException exception) {
            throw error("XLSX_INVALID_WORKBOOK", null, "file", "Workbook package relationships are invalid");
        }
    }

    private void rejectExternalRelationship(PackageRelationship relationship) {
        if (relationship.getTargetMode() == TargetMode.EXTERNAL) {
            throw error("XLSX_EXTERNAL_LINK_NOT_ALLOWED", null, "file",
                    "External workbook relationships are not supported");
        }
    }

    private void validateWorkbook(XSSFWorkbook workbook) {
        if (workbook.getNumberOfSheets() != 1 || !SHEET_NAME.equals(workbook.getSheetName(0))) {
            throw error("XLSX_INVALID_SHEET", null, "sheet",
                    "Workbook must contain exactly one transactions sheet");
        }
        if (workbook.isSheetHidden(0) || workbook.isSheetVeryHidden(0)) {
            throw error("XLSX_HIDDEN_CONTENT", null, "sheet", "Hidden sheets are not supported");
        }
        if (!workbook.getExternalLinksTables().isEmpty()) {
            throw error("XLSX_EXTERNAL_LINK_NOT_ALLOWED", null, "file",
                    "External workbook links are not supported");
        }
    }

    private List<NormalizedTransaction> parseSheet(XSSFSheet sheet, LocalDate today) {
        if (sheet.getNumMergedRegions() > 0) {
            throw error("XLSX_MERGED_CELL_NOT_ALLOWED", null, "sheet", "Merged cells are not supported");
        }
        if (sheet.getLastRowNum() > rules.maximumTransactionRows() + MAXIMUM_BLANK_ROWS) {
            throw error("XLSX_TOO_MANY_ROWS", null, "row", "Workbook contains too many rows");
        }
        Row headerRow = sheet.getRow(0);
        if (headerRow == null || isBlankRow(headerRow)) {
            throw error("XLSX_EMPTY_FILE", null, "file", "Workbook must contain a header and transactions");
        }
        List<String> headers = readHeaders(headerRow);
        Map<String, Integer> headerIndexes = recordNormalizer.validateHeaders(headers, "XLSX_", this::error);
        for (int column = 0; column < headers.size(); column++) {
            if (sheet.isColumnHidden(column)) {
                throw error("XLSX_HIDDEN_CONTENT", 1, headers.get(column),
                        "Hidden columns are not supported");
            }
        }

        List<NormalizedTransaction> transactions = new ArrayList<>();
        Set<String> transactionIds = new HashSet<>();
        YearMonth earliest = null;
        YearMonth latest = null;
        int blankRows = 0;
        int physicalCells = headerRow.getPhysicalNumberOfCells();

        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            int rowNumber = rowIndex + 1;
            if (row.getZeroHeight()) {
                throw error("XLSX_HIDDEN_CONTENT", rowNumber, "row", "Hidden rows are not supported");
            }
            validateRowCells(row, rowNumber, headers.size());
            physicalCells += row.getPhysicalNumberOfCells();
            if (physicalCells > (rules.maximumTransactionRows() + MAXIMUM_BLANK_ROWS + 1)
                    * MAXIMUM_PHYSICAL_CELLS_PER_ROW) {
                throw error("XLSX_TOO_MANY_CELLS", rowNumber, "row", "Workbook contains too many cells");
            }
            if (isBlankRow(row)) {
                blankRows++;
                if (blankRows > MAXIMUM_BLANK_ROWS) {
                    throw error("XLSX_TOO_MANY_BLANK_ROWS", rowNumber, "row",
                            "Workbook contains too many blank rows");
                }
                continue;
            }
            if (transactions.size() >= rules.maximumTransactionRows()) {
                throw error("XLSX_TOO_MANY_ROWS", rowNumber, "row",
                        "Workbook contains more than 10000 transaction rows");
            }
            Map<String, String> fields = new HashMap<>();
            for (Map.Entry<String, Integer> header : headerIndexes.entrySet()) {
                fields.put(header.getKey(), readCell(row.getCell(header.getValue()), header.getKey(), rowNumber));
            }
            NormalizedTransaction transaction = recordNormalizer.normalize(fields, transactionIds,
                    rowNumber, today, "XLSX_", this::error);
            transactions.add(transaction);
            YearMonth month = YearMonth.from(transaction.transactionDate());
            earliest = earliest == null || month.isBefore(earliest) ? month : earliest;
            latest = latest == null || month.isAfter(latest) ? month : latest;
            if (ChronoUnit.MONTHS.between(earliest, latest) + 1L > rules.maximumAnalysisMonths()) {
                throw error("XLSX_ANALYSIS_PERIOD_TOO_LONG", rowNumber,
                        TransactionRecordNormalizer.TRANSACTION_DATE,
                        "XLSX analysis period exceeds 60 months");
            }
        }
        if (transactions.isEmpty()) {
            throw error("XLSX_NO_TRANSACTIONS", null, "file",
                    "Workbook must contain at least one transaction row");
        }
        return List.copyOf(transactions);
    }

    private List<String> readHeaders(Row row) {
        validateRowCells(row, 1, TransactionRecordNormalizer.ALLOWED_HEADERS.size());
        int lastCell = row.getLastCellNum();
        if (lastCell <= 0 || lastCell > TransactionRecordNormalizer.ALLOWED_HEADERS.size()) {
            throw error("XLSX_INVALID_HEADER", 1, "header", "Workbook header count is invalid");
        }
        List<String> headers = new ArrayList<>(lastCell);
        for (int column = 0; column < lastCell; column++) {
            Cell cell = row.getCell(column);
            if (cell == null || cell.getCellType() != CellType.STRING) {
                throw error("XLSX_INVALID_HEADER", 1, "header", "Headers must be text values");
            }
            headers.add(readCell(cell, "header", 1));
        }
        return headers;
    }

    private void validateRowCells(Row row, int rowNumber, int expectedColumns) {
        if (row.getPhysicalNumberOfCells() > MAXIMUM_PHYSICAL_CELLS_PER_ROW
                || row.getLastCellNum() > MAXIMUM_PHYSICAL_CELLS_PER_ROW) {
            throw error("XLSX_TOO_MANY_CELLS", rowNumber, "row", "Row contains too many cells");
        }
        for (Cell cell : row) {
            rejectProhibitedCellFeatures(cell, rowNumber);
            if (cell.getColumnIndex() >= expectedColumns && !isBlankCell(cell)) {
                throw error("XLSX_FIELD_COUNT_MISMATCH", rowNumber, "row",
                        "Row contains values outside the declared headers");
            }
        }
    }

    private String readCell(Cell cell, String column, int rowNumber) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return "";
        }
        rejectProhibitedCellFeatures(cell, rowNumber);
        String value;
        if (cell.getCellType() == CellType.STRING) {
            value = cell.getStringCellValue();
        } else if (cell.getCellType() == CellType.NUMERIC
                && TransactionRecordNormalizer.AMOUNT.equals(column) && cell instanceof XSSFCell xssfCell) {
            value = xssfCell.getRawValue();
        } else {
            throw error("XLSX_UNSUPPORTED_CELL_TYPE", rowNumber, column,
                    "Cell type is not supported for this column");
        }
        if (value == null || value.codePointCount(0, value.length()) > MAXIMUM_CELL_CHARACTERS) {
            throw error("XLSX_CELL_TOO_LONG", rowNumber, column, "Cell value exceeds the safe length limit");
        }
        return value;
    }

    private void rejectProhibitedCellFeatures(Cell cell, int rowNumber) {
        if (cell.getCellType() == CellType.FORMULA) {
            throw error("XLSX_FORMULA_NOT_ALLOWED", rowNumber, columnName(cell),
                    "Formula cells are not supported");
        }
        if (cell.getHyperlink() != null) {
            throw error("XLSX_EXTERNAL_LINK_NOT_ALLOWED", rowNumber, columnName(cell),
                    "Hyperlinks are not supported");
        }
        if (cell.getCellComment() != null) {
            throw error("XLSX_EMBEDDED_OBJECT_NOT_ALLOWED", rowNumber, columnName(cell),
                    "Cell comments are not supported");
        }
    }

    private boolean isBlankRow(Row row) {
        for (Cell cell : row) {
            if (!isBlankCell(cell)) {
                return false;
            }
        }
        return true;
    }

    private boolean isBlankCell(Cell cell) {
        return cell.getCellType() == CellType.BLANK
                || (cell.getCellType() == CellType.STRING && cell.getStringCellValue().isBlank());
    }

    private String columnName(Cell cell) {
        int index = cell.getColumnIndex();
        return index < TransactionRecordNormalizer.ALLOWED_HEADERS.size()
                ? "column" + (index + 1) : "row";
    }

    private XlsxValidationException error(String code, Integer row, String column, String message) {
        return new XlsxValidationException(code, row, column, message);
    }
}
