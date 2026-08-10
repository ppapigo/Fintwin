package com.fintwin.fintwin.pattern.service;

import com.fintwin.fintwin.financialprofile.dto.FinancialProfileResponse;
import com.fintwin.fintwin.financialprofile.service.FinancialProfileService;
import com.fintwin.fintwin.global.error.CsvValidationException;
import com.fintwin.fintwin.pattern.domain.FinancialPatternReport;
import com.fintwin.fintwin.pattern.domain.FinancialPatternRules;
import com.fintwin.fintwin.pattern.domain.NormalizedTransaction;
import com.fintwin.fintwin.pattern.dto.FinancialPatternAnalysisResponse;
import com.fintwin.fintwin.pattern.engine.FinancialPatternEngine;
import com.fintwin.fintwin.pattern.parser.TransactionCsvParser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class FinancialPatternAnalysisService {
    private final FinancialProfileService financialProfileService;
    private final TransactionCsvParser csvParser;
    private final FinancialPatternEngine patternEngine;
    private final FinancialPatternRules rules;
    private final Clock clock;

    public FinancialPatternAnalysisService(FinancialProfileService financialProfileService,
                                           TransactionCsvParser csvParser,
                                           FinancialPatternEngine patternEngine,
                                           FinancialPatternRules rules,
                                           Clock clock) {
        this.financialProfileService = financialProfileService;
        this.csvParser = csvParser;
        this.patternEngine = patternEngine;
        this.rules = rules;
        this.clock = clock;
    }

    public FinancialPatternAnalysisResponse analyze(Long userId, MultipartFile file) {
        validateFile(file);
        try (InputStream input = file.getInputStream()) {
            List<NormalizedTransaction> transactions = csvParser.parse(input, file.getSize(), LocalDate.now(clock));
            FinancialPatternReport report = patternEngine.analyze(transactions);
            FinancialProfileResponse currentProfile = financialProfileService.getCurrentIfPresent(userId)
                    .orElse(null);
            return FinancialPatternAnalysisResponse.from(report, currentProfile);
        } catch (CsvValidationException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new CsvValidationException("CSV_READ_FAILED", null, "file",
                    "CSV file could not be read");
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() == 0) {
            throw new CsvValidationException("CSV_EMPTY_FILE", null, "file", "CSV file must not be empty");
        }
        if (file.getSize() > rules.maximumFileBytes()) {
            throw new CsvValidationException("CSV_FILE_TOO_LARGE", null, "file",
                    "CSV file exceeds the 2MB limit");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(java.util.Locale.ROOT).endsWith(".csv")) {
            throw new CsvValidationException("CSV_INVALID_EXTENSION", null, "file",
                    "Only .csv files are supported");
        }
    }
}
