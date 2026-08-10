package com.fintwin.fintwin.privacy.config;

import com.fintwin.fintwin.privacy.token.FinancialValueTokenizer;
import com.fintwin.fintwin.privacy.token.KoreanMoneyParser;
import com.fintwin.fintwin.privacy.token.ReferenceRehydrator;
import com.fintwin.fintwin.privacy.validation.ExternalAiDraftValidator;
import com.fintwin.fintwin.privacy.validation.OutboundPayloadGuard;
import com.fintwin.fintwin.privacy.validation.PersonalIdentifierDetector;
import com.fintwin.fintwin.scenario.service.FinancialEventMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PrivacyConfig {
    @Bean
    KoreanMoneyParser koreanMoneyParser() {
        return new KoreanMoneyParser();
    }

    @Bean
    FinancialValueTokenizer financialValueTokenizer(KoreanMoneyParser moneyParser) {
        return new FinancialValueTokenizer(moneyParser);
    }

    @Bean
    PersonalIdentifierDetector personalIdentifierDetector() {
        return new PersonalIdentifierDetector();
    }

    @Bean
    OutboundPayloadGuard outboundPayloadGuard(PersonalIdentifierDetector identifierDetector,
                                              FinancialValueTokenizer tokenizer) {
        return new OutboundPayloadGuard(identifierDetector, tokenizer);
    }

    @Bean
    ExternalAiDraftValidator externalAiDraftValidator(PersonalIdentifierDetector identifierDetector,
                                                      FinancialValueTokenizer tokenizer) {
        return new ExternalAiDraftValidator(identifierDetector, tokenizer);
    }

    @Bean
    ReferenceRehydrator referenceRehydrator(ExternalAiDraftValidator draftValidator,
                                            FinancialEventMapper financialEventMapper) {
        return new ReferenceRehydrator(draftValidator, financialEventMapper);
    }
}
