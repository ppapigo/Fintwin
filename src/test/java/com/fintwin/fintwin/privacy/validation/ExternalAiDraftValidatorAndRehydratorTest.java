package com.fintwin.fintwin.privacy.validation;

import com.fintwin.fintwin.global.error.InvalidRequestException;
import com.fintwin.fintwin.privacy.domain.ExternalAiScenarioDraft;
import com.fintwin.fintwin.privacy.token.FinancialReferenceVault;
import com.fintwin.fintwin.privacy.token.FinancialTokenizationResult;
import com.fintwin.fintwin.privacy.token.FinancialValueTokenizer;
import com.fintwin.fintwin.privacy.token.KoreanMoneyParser;
import com.fintwin.fintwin.privacy.token.ReferenceRehydrator;
import com.fintwin.fintwin.scenario.domain.FinancialEvent;
import com.fintwin.fintwin.scenario.domain.ScenarioDefinition;
import com.fintwin.fintwin.scenario.service.FinancialEventMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExternalAiDraftValidatorAndRehydratorTest {
    private static final YearMonth CURRENT = YearMonth.of(2026, 8);
    private final FinancialValueTokenizer tokenizer = new FinancialValueTokenizer(new KoreanMoneyParser());
    private final ExternalAiDraftValidator validator = new ExternalAiDraftValidator(
            new PersonalIdentifierDetector(), tokenizer);
    private final ReferenceRehydrator rehydrator = new ReferenceRehydrator(validator,
            new FinancialEventMapper());

    @Test
    void rehydratesValidOneTimeExpenseAndReusesFinancialEventMapper() {
        FinancialReferenceVault vault = tokenizer.tokenize("3천만원").referenceVault();
        ExternalAiScenarioDraft draft = draft("PURCHASE", oneTime("event-1", "내년", null, "MONEY_1"));

        ScenarioDefinition scenario = rehydrator.rehydrate("purchase", draft, vault, CURRENT, CURRENT, 24);

        FinancialEvent.OneTimeExpense event = (FinancialEvent.OneTimeExpense) scenario.events().getFirst();
        assertThat(event.effectiveYearMonth()).isEqualTo(YearMonth.of(2027, 8));
        assertThat(event.amount()).isEqualByComparingTo("30000000.00");
    }

    @Test
    void rehydratesAbsoluteDateReference() {
        FinancialReferenceVault vault = tokenizer.tokenize("3천만원 2027-03").referenceVault();
        ExternalAiScenarioDraft draft = draft("PURCHASE",
                oneTime("event-1", null, "DATE_1", "MONEY_1"));

        ScenarioDefinition scenario = rehydrator.rehydrate("purchase", draft, vault, CURRENT, CURRENT, 24);

        FinancialEvent.OneTimeExpense event = (FinancialEvent.OneTimeExpense) scenario.events().getFirst();
        assertThat(event.effectiveYearMonth()).isEqualTo(YearMonth.of(2027, 3));
    }

    @Test
    void rehydratesDurationAndAppliesDecreaseDirectionServerSide() {
        FinancialReferenceVault vault = tokenizer.tokenize("20만원 6개월").referenceVault();
        ExternalAiScenarioDraft.EventDraft event = new ExternalAiScenarioDraft.EventDraft(
                "event-1", "RECURRING_EXPENSE_CHANGE", "DECREASE",
                null, null, "다음 달", null, null, null,
                "DURATION_1", null, "MONEY_1", "생활비 조정");

        ScenarioDefinition scenario = rehydrator.rehydrate("expense", draft("EXPENSE_CHANGE", event),
                vault, CURRENT, CURRENT, 24);

        FinancialEvent.RecurringExpenseChange rehydrated =
                (FinancialEvent.RecurringExpenseChange) scenario.events().getFirst();
        assertThat(rehydrated.startYearMonth()).isEqualTo(YearMonth.of(2026, 9));
        assertThat(rehydrated.endYearMonth()).isEqualTo(YearMonth.of(2027, 2));
        assertThat(rehydrated.monthlyDelta()).isEqualByComparingTo("-200000.00");
    }

    @Test
    void rejectsUnknownReferenceAndReferenceTypeMismatch() {
        FinancialReferenceVault moneyVault = tokenizer.tokenize("3천만원").referenceVault();
        FinancialReferenceVault percentVault = tokenizer.tokenize("2%").referenceVault();

        assertThatThrownBy(() -> validator.validate(
                draft("PURCHASE", oneTime("event-1", "내년", null, "MONEY_99")), moneyVault))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("External AI draft contains an unknown reference ID");
        assertThatThrownBy(() -> validator.validate(
                draft("PURCHASE", oneTime("event-1", "내년", null, "PERCENT_1")), percentVault))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("External AI draft reference type does not match the target field");
    }

    @Test
    void rejectsUnsupportedEventTypeIntentAndDuplicateEventId() {
        FinancialReferenceVault vault = tokenizer.tokenize("3천만원").referenceVault();
        ExternalAiScenarioDraft.EventDraft unsupported = new ExternalAiScenarioDraft.EventDraft(
                "event-1", "NEW_LOAN", null, "내년", null,
                null, null, null, null, null, "MONEY_1", null, "대출");

        assertThatThrownBy(() -> validator.validate(draft("PURCHASE", unsupported), vault))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("External AI draft event type is not supported");
        assertThatThrownBy(() -> validator.validate(draft("UNSUPPORTED",
                oneTime("event-1", "내년", null, "MONEY_1")), vault))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("External AI draft intent is not supported");
        assertThatThrownBy(() -> validator.validate(new ExternalAiScenarioDraft("PURCHASE", List.of(
                oneTime("same", "내년", null, "MONEY_1"),
                oneTime("same", "다음 달", null, "MONEY_1")), List.of()), vault))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("External AI draft contains a duplicate event ID");
    }

    @Test
    void rejectsDirectFinancialValueInAiDescription() {
        FinancialReferenceVault vault = tokenizer.tokenize("3천만원").referenceVault();
        ExternalAiScenarioDraft.EventDraft event = new ExternalAiScenarioDraft.EventDraft(
                "event-1", "ONE_TIME_EXPENSE", null, "내년", null,
                null, null, null, null, null, "MONEY_1", null, "3000만원 자동차 구매");

        assertThatThrownBy(() -> validator.validate(draft("PURCHASE", event), vault))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("External AI draft description contains a direct financial value");
    }

    @Test
    void rejectsWholeDraftWhenOnlyOneEventIsInvalid() {
        FinancialReferenceVault vault = tokenizer.tokenize("3천만원").referenceVault();
        ExternalAiScenarioDraft draft = new ExternalAiScenarioDraft("MULTI_EVENT", List.of(
                oneTime("valid", "내년", null, "MONEY_1"),
                oneTime("invalid", "다음 달", null, "MONEY_2")), List.of());

        assertThatThrownBy(() -> rehydrator.rehydrate("partial", draft, vault, CURRENT, CURRENT, 24))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("External AI draft contains an unknown reference ID");
    }

    @Test
    void existingScenarioValidationStillRejectsResolvedDateOutsideHorizon() {
        FinancialTokenizationResult tokenized = tokenizer.tokenize("3천만원 2030-01");
        ExternalAiScenarioDraft draft = draft("PURCHASE",
                oneTime("event-1", null, "DATE_1", "MONEY_1"));

        assertThatThrownBy(() -> rehydrator.rehydrate("outside", draft, tokenized.referenceVault(),
                CURRENT, CURRENT, 12))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("outside the simulation range");
    }

    @Test
    void aiDraftTypesCannotCarryDirectNumericFinancialFields() {
        assertThat(ExternalAiScenarioDraft.class.getRecordComponents())
                .extracting(component -> component.getType())
                .noneMatch(type -> type == BigDecimal.class || type == Long.class || type == long.class
                        || type == Integer.class || type == int.class);
        assertThat(ExternalAiScenarioDraft.EventDraft.class.getRecordComponents())
                .extracting(component -> component.getType())
                .noneMatch(type -> type == BigDecimal.class || type == Long.class || type == long.class
                        || type == Integer.class || type == int.class);
    }

    private ExternalAiScenarioDraft draft(String intent, ExternalAiScenarioDraft.EventDraft event) {
        return new ExternalAiScenarioDraft(intent, List.of(event), List.of());
    }

    private ExternalAiScenarioDraft.EventDraft oneTime(String eventId, String expression,
                                                       String dateReference, String amountReference) {
        return new ExternalAiScenarioDraft.EventDraft(eventId, "ONE_TIME_EXPENSE", null,
                expression, dateReference, null, null, null, null, null,
                amountReference, null, "자동차 구매");
    }
}
