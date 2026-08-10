package com.fintwin.fintwin.privacy.validation;

import com.fintwin.fintwin.global.error.InvalidRequestException;
import com.fintwin.fintwin.privacy.domain.ExternalAiScenarioDraft;
import com.fintwin.fintwin.privacy.domain.ReferenceType;
import com.fintwin.fintwin.privacy.token.FinancialReferenceVault;
import com.fintwin.fintwin.privacy.token.FinancialValueTokenizer;
import com.fintwin.fintwin.scenario.domain.FinancialEventType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class ExternalAiDraftValidator {
    private static final int MAX_EVENTS = 20;
    private static final int MAX_EVENT_ID_LENGTH = 100;
    private static final int MAX_DESCRIPTION_LENGTH = 200;
    private static final Pattern REFERENCE_ID = Pattern.compile(
            "^(MONEY|PERCENT|DURATION|DATE)_[1-9][0-9]*$");
    private static final Pattern EVENT_ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{0,99}$");
    private static final Pattern RELATIVE_DATE_EXPRESSION = Pattern.compile(
            "^(?:이번\\s*달|다음\\s*달|내년(?:\\s*(?:1[0-2]|[1-9])월)?"
                    + "|[1-9][0-9]{0,2}\\s*개월\\s*(?:뒤|후)"
                    + "|[1-9][0-9]?\\s*년\\s*(?:뒤|후))$");
    private static final Set<String> ALLOWED_INTENTS = Set.of(
            "PURCHASE", "EXPENSE_CHANGE", "INCOME_CHANGE", "INCOME_PAUSE",
            "INVESTMENT_CHANGE", "DEBT_REPAYMENT", "MULTI_EVENT", "UNKNOWN");
    private static final Set<String> ALLOWED_MISSING_FIELDS = Set.of(
            "EVENT_TYPE", "EFFECTIVE_DATE", "START_DATE", "END_DATE", "DURATION",
            "AMOUNT", "MONTHLY_DELTA", "DESCRIPTION");

    private final PersonalIdentifierDetector identifierDetector;
    private final FinancialValueTokenizer tokenizer;

    public ExternalAiDraftValidator(PersonalIdentifierDetector identifierDetector,
                                    FinancialValueTokenizer tokenizer) {
        this.identifierDetector = identifierDetector;
        this.tokenizer = tokenizer;
    }

    public void validate(ExternalAiScenarioDraft draft, FinancialReferenceVault vault) {
        if (draft == null || vault == null) {
            throw invalid("External AI draft is required");
        }
        String intent = requireText(draft.intent(), "intent", 50);
        if (!ALLOWED_INTENTS.contains(intent)) {
            throw invalid("External AI draft intent is not supported");
        }
        if (draft.events() == null || draft.events().isEmpty() || draft.events().size() > MAX_EVENTS) {
            throw invalid("External AI draft events must contain between 1 and 20 items");
        }
        validateMissingFields(draft.missingFields());

        Set<String> eventIds = new HashSet<>();
        for (ExternalAiScenarioDraft.EventDraft event : draft.events()) {
            if (event == null) {
                throw invalid("External AI draft contains an invalid event");
            }
            String eventId = requireText(event.eventId(), "eventId", MAX_EVENT_ID_LENGTH);
            if (!EVENT_ID.matcher(eventId).matches()) {
                throw invalid("External AI draft event ID format is invalid");
            }
            if (!eventIds.add(eventId)) {
                throw invalid("External AI draft contains a duplicate event ID");
            }
            validateDescription(event.description());
            FinancialEventType eventType = parseEventType(event.eventType());
            switch (eventType) {
                case ONE_TIME_EXPENSE, EXTRA_DEBT_REPAYMENT -> validateOneTime(event, vault);
                case INCOME_PAUSE -> validateIncomePause(event, vault);
                case RECURRING_EXPENSE_CHANGE, INCOME_CHANGE, INVESTMENT_CONTRIBUTION_CHANGE ->
                        validateMonthlyChange(event, vault);
            }
        }
    }

    public void validateProviderDraft(ExternalAiScenarioDraft draft, FinancialReferenceVault vault) {
        if (draft == null || vault == null) {
            throw invalid("External AI draft is required");
        }
        String intent = requireText(draft.intent(), "intent", 50);
        if (!ALLOWED_INTENTS.contains(intent)) {
            throw invalid("External AI draft intent is not supported");
        }
        validateMissingFields(draft.missingFields());
        if (draft.missingFields().isEmpty()) {
            validate(draft, vault);
            return;
        }
        if (draft.events() == null || draft.events().size() > MAX_EVENTS) {
            throw invalid("External AI draft events must contain at most 20 items");
        }
        Set<String> missingFields = Set.copyOf(draft.missingFields());
        if (draft.events().isEmpty()) {
            requireMissing(missingFields, "EVENT_TYPE");
            return;
        }

        Set<String> eventIds = new HashSet<>();
        for (ExternalAiScenarioDraft.EventDraft event : draft.events()) {
            if (event == null) {
                throw invalid("External AI draft contains an invalid event");
            }
            String eventId = requireText(event.eventId(), "eventId", MAX_EVENT_ID_LENGTH);
            if (!EVENT_ID.matcher(eventId).matches() || !eventIds.add(eventId)) {
                throw invalid("External AI draft event ID is invalid or duplicated");
            }
            if (hasText(event.description())) {
                validateDescription(event.description());
            } else {
                requireMissing(missingFields, "DESCRIPTION");
            }
            if (!hasText(event.eventType())) {
                requireMissing(missingFields, "EVENT_TYPE");
                requireAbsent(event.changeDirection(), event.effectiveDateExpression(),
                        event.effectiveDateReference(), event.startDateExpression(), event.startDateReference(),
                        event.endDateExpression(), event.endDateReference(), event.durationReference(),
                        event.amountReference(), event.monthlyDeltaReference());
                continue;
            }
            FinancialEventType eventType = parseEventType(event.eventType());
            switch (eventType) {
                case ONE_TIME_EXPENSE, EXTRA_DEBT_REPAYMENT ->
                        validateIncompleteOneTime(event, vault, missingFields);
                case INCOME_PAUSE -> validateIncompleteIncomePause(event, vault, missingFields);
                case RECURRING_EXPENSE_CHANGE, INCOME_CHANGE, INVESTMENT_CONTRIBUTION_CHANGE ->
                        validateIncompleteMonthlyChange(event, vault, missingFields);
            }
        }
    }

    private void validateIncompleteOneTime(ExternalAiScenarioDraft.EventDraft event,
                                           FinancialReferenceVault vault,
                                           Set<String> missingFields) {
        validateOptionalDateSelector(event.effectiveDateExpression(), event.effectiveDateReference(),
                "EFFECTIVE_DATE", vault, missingFields);
        validateOptionalReference(event.amountReference(), ReferenceType.MONEY, "AMOUNT", vault, missingFields);
        requireAbsent(event.changeDirection(), event.startDateExpression(), event.startDateReference(),
                event.endDateExpression(), event.endDateReference(), event.durationReference(),
                event.monthlyDeltaReference());
    }

    private void validateIncompleteIncomePause(ExternalAiScenarioDraft.EventDraft event,
                                               FinancialReferenceVault vault,
                                               Set<String> missingFields) {
        validateIncompletePeriod(event, vault, missingFields);
        requireAbsent(event.changeDirection(), event.effectiveDateExpression(), event.effectiveDateReference(),
                event.amountReference(), event.monthlyDeltaReference());
    }

    private void validateIncompleteMonthlyChange(ExternalAiScenarioDraft.EventDraft event,
                                                 FinancialReferenceVault vault,
                                                 Set<String> missingFields) {
        validateIncompletePeriod(event, vault, missingFields);
        if (hasText(event.changeDirection())) {
            if (!Set.of("INCREASE", "DECREASE").contains(event.changeDirection())) {
                throw invalid("External AI draft change direction is not supported");
            }
        } else {
            requireMissing(missingFields, "MONTHLY_DELTA");
        }
        validateOptionalReference(event.monthlyDeltaReference(), ReferenceType.MONEY,
                "MONTHLY_DELTA", vault, missingFields);
        requireAbsent(event.effectiveDateExpression(), event.effectiveDateReference(), event.amountReference());
    }

    private void validateIncompletePeriod(ExternalAiScenarioDraft.EventDraft event,
                                          FinancialReferenceVault vault,
                                          Set<String> missingFields) {
        validateOptionalDateSelector(event.startDateExpression(), event.startDateReference(),
                "START_DATE", vault, missingFields);
        boolean hasEnd = hasText(event.endDateExpression()) || hasText(event.endDateReference());
        boolean hasDuration = hasText(event.durationReference());
        if (hasEnd && hasDuration) {
            throw invalid("External AI draft period contains conflicting end selectors");
        }
        if (!hasEnd && !hasDuration) {
            if (!missingFields.contains("END_DATE") && !missingFields.contains("DURATION")) {
                throw invalid("External AI draft period missing field is not declared");
            }
        } else if (hasEnd) {
            requireDateSelector(event.endDateExpression(), event.endDateReference(), vault);
        } else {
            requireReference(event.durationReference(), ReferenceType.DURATION, vault);
        }
    }

    private void validateOptionalDateSelector(String expression, String reference,
                                              String missingCode,
                                              FinancialReferenceVault vault,
                                              Set<String> missingFields) {
        if (!hasText(expression) && !hasText(reference)) {
            requireMissing(missingFields, missingCode);
            return;
        }
        requireDateSelector(expression, reference, vault);
    }

    private void validateOptionalReference(String referenceId, ReferenceType expectedType,
                                           String missingCode,
                                           FinancialReferenceVault vault,
                                           Set<String> missingFields) {
        if (!hasText(referenceId)) {
            requireMissing(missingFields, missingCode);
            return;
        }
        requireReference(referenceId, expectedType, vault);
    }

    private void requireMissing(Set<String> missingFields, String code) {
        if (!missingFields.contains(code)) {
            throw invalid("External AI draft omitted a field without declaring it missing");
        }
    }

    private void validateOneTime(ExternalAiScenarioDraft.EventDraft event, FinancialReferenceVault vault) {
        requireDateSelector(event.effectiveDateExpression(), event.effectiveDateReference(), vault);
        requireReference(event.amountReference(), ReferenceType.MONEY, vault);
        requireAbsent(event.changeDirection(), event.startDateExpression(), event.startDateReference(),
                event.endDateExpression(), event.endDateReference(), event.durationReference(),
                event.monthlyDeltaReference());
    }

    private void validateIncomePause(ExternalAiScenarioDraft.EventDraft event, FinancialReferenceVault vault) {
        validatePeriod(event, vault);
        requireAbsent(event.changeDirection(), event.effectiveDateExpression(), event.effectiveDateReference(),
                event.amountReference(), event.monthlyDeltaReference());
    }

    private void validateMonthlyChange(ExternalAiScenarioDraft.EventDraft event,
                                       FinancialReferenceVault vault) {
        validatePeriod(event, vault);
        String direction = requireText(event.changeDirection(), "changeDirection", 20);
        if (!Set.of("INCREASE", "DECREASE").contains(direction)) {
            throw invalid("External AI draft change direction is not supported");
        }
        requireReference(event.monthlyDeltaReference(), ReferenceType.MONEY, vault);
        requireAbsent(event.effectiveDateExpression(), event.effectiveDateReference(), event.amountReference());
    }

    private void validatePeriod(ExternalAiScenarioDraft.EventDraft event, FinancialReferenceVault vault) {
        requireDateSelector(event.startDateExpression(), event.startDateReference(), vault);
        if (event.durationReference() != null && !hasText(event.durationReference())) {
            throw invalid("External AI draft duration reference contains a blank value");
        }
        boolean hasEnd = hasText(event.endDateExpression()) || hasText(event.endDateReference());
        boolean hasDuration = hasText(event.durationReference());
        if (hasEnd == hasDuration) {
            throw invalid("External AI draft period requires either an end date or duration");
        }
        if (hasEnd) {
            requireDateSelector(event.endDateExpression(), event.endDateReference(), vault);
        } else {
            requireReference(event.durationReference(), ReferenceType.DURATION, vault);
        }
    }

    private void requireDateSelector(String expression, String reference,
                                     FinancialReferenceVault vault) {
        if ((expression != null && !hasText(expression)) || (reference != null && !hasText(reference))) {
            throw invalid("External AI draft date selector contains a blank value");
        }
        boolean hasExpression = hasText(expression);
        boolean hasReference = hasText(reference);
        if (hasExpression == hasReference) {
            throw invalid("External AI draft date requires exactly one expression or reference");
        }
        if (hasReference) {
            requireReference(reference, ReferenceType.DATE, vault);
        } else if (!RELATIVE_DATE_EXPRESSION.matcher(expression).matches()) {
            throw invalid("External AI draft relative date expression is not supported");
        }
    }

    private void requireReference(String referenceId, ReferenceType expectedType,
                                  FinancialReferenceVault vault) {
        if (!hasText(referenceId) || !REFERENCE_ID.matcher(referenceId).matches()) {
            throw invalid("External AI draft reference ID is invalid");
        }
        if (!vault.contains(referenceId)) {
            throw invalid("External AI draft contains an unknown reference ID");
        }
        if (vault.typeOf(referenceId) != expectedType) {
            throw invalid("External AI draft reference type does not match the target field");
        }
    }

    private void validateDescription(String description) {
        String normalized = requireText(description, "description", MAX_DESCRIPTION_LENGTH);
        if (!identifierDetector.detect(normalized).isEmpty()) {
            throw invalid("External AI draft description contains a blocked identifier type");
        }
        try {
            if (!tokenizer.tokenize(normalized).references().isEmpty()) {
                throw invalid("External AI draft description contains a direct financial value");
            }
        } catch (IllegalArgumentException | ArithmeticException exception) {
            throw invalid("External AI draft description contains an invalid financial expression");
        }
    }

    private void validateMissingFields(List<String> missingFields) {
        if (missingFields == null || missingFields.size() > 20) {
            throw invalid("External AI draft missing-field list is invalid");
        }
        Set<String> unique = new HashSet<>();
        for (String missingField : missingFields) {
            if (!ALLOWED_MISSING_FIELDS.contains(missingField) || !unique.add(missingField)) {
                throw invalid("External AI draft contains an unsupported missing-field code");
            }
        }
    }

    private FinancialEventType parseEventType(String rawType) {
        String exactType = requireText(rawType, "eventType", 100);
        try {
            return FinancialEventType.valueOf(exactType);
        } catch (IllegalArgumentException exception) {
            throw invalid("External AI draft event type is not supported");
        }
    }

    private String requireText(String value, String field, int maxLength) {
        if (!hasText(value)) {
            throw invalid("External AI draft " + field + " is required");
        }
        if (!value.equals(value.strip()) || value.codePointCount(0, value.length()) > maxLength) {
            throw invalid("External AI draft " + field + " is invalid");
        }
        return value;
    }

    private void requireAbsent(String... values) {
        for (String value : values) {
            if (value != null) {
                throw invalid("External AI draft contains a field that is not allowed for the event type");
            }
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private InvalidRequestException invalid(String message) {
        return new InvalidRequestException(message);
    }
}
