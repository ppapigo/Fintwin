package com.fintwin.fintwin.pattern.config;

import com.fintwin.fintwin.pattern.domain.FinancialPatternRules;
import com.fintwin.fintwin.pattern.engine.FinancialPatternEngine;
import com.fintwin.fintwin.pattern.parser.TransactionCsvParser;
import com.fintwin.fintwin.pattern.parser.TransactionXlsxParser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class PatternConfig {
    @Bean
    FinancialPatternRules financialPatternRules() {
        return FinancialPatternRules.standard();
    }

    @Bean
    TransactionCsvParser transactionCsvParser(FinancialPatternRules rules) {
        return new TransactionCsvParser(rules);
    }

    @Bean
    TransactionXlsxParser transactionXlsxParser(FinancialPatternRules rules) {
        return new TransactionXlsxParser(rules);
    }

    @Bean
    FinancialPatternEngine financialPatternEngine(FinancialPatternRules rules) {
        return new FinancialPatternEngine(rules);
    }

    @Bean
    Clock patternAnalysisClock() {
        return Clock.systemDefaultZone();
    }
}
