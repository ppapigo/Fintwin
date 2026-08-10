package com.fintwin.fintwin.privacy.token;

import com.fintwin.fintwin.global.error.InvalidRequestException;
import com.fintwin.fintwin.privacy.domain.ExternalAiScenarioDraft;
import com.fintwin.fintwin.privacy.validation.ExternalAiDraftValidator;
import com.fintwin.fintwin.scenario.domain.FinancialEventType;
import com.fintwin.fintwin.scenario.domain.ScenarioDefinition;
import com.fintwin.fintwin.scenario.dto.FinancialEventRequest;
import com.fintwin.fintwin.scenario.service.FinancialEventMapper;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ReferenceRehydrator {
    private static final Pattern NEXT_YEAR_MONTH = Pattern.compile("^내년\\s*(?<month>1[0-2]|[1-9])월$");
    private static final Pattern MONTH_OFFSET = Pattern.compile("^(?<count>[1-9][0-9]{0,2})\\s*개월\\s*(?:뒤|후)$");
    private static final Pattern YEAR_OFFSET = Pattern.compile("^(?<count>[1-9][0-9]?)\\s*년\\s*(?:뒤|후)$");

    private final ExternalAiDraftValidator draftValidator;
    private final FinancialEventMapper financialEventMapper;

    public ReferenceRehydrator(ExternalAiDraftValidator draftValidator,
                               FinancialEventMapper financialEventMapper) {
        this.draftValidator = draftValidator;
        this.financialEventMapper = financialEventMapper;
    }

    public ScenarioDefinition rehydrate(String scenarioName, ExternalAiScenarioDraft draft,
                                        FinancialReferenceVault vault, YearMonth currentYearMonth,
                                        YearMonth simulationStart, int horizonMonths) {
        draftValidator.validate(draft, vault);
        List<FinancialEventRequest> requests = new ArrayList<>(draft.events().size());
        for (ExternalAiScenarioDraft.EventDraft event : draft.events()) {
            FinancialEventType eventType = FinancialEventType.valueOf(event.eventType().toUpperCase(Locale.ROOT));
            requests.add(switch (eventType) {
                case ONE_TIME_EXPENSE, EXTRA_DEBT_REPAYMENT -> new FinancialEventRequest(
                        event.eventId(), eventType.name(),
                        resolveDate(event.effectiveDateExpression(), event.effectiveDateReference(),
                                vault, currentYearMonth),
                        null, null, vault.requireMoney(event.amountReference()), null, event.description());
                case INCOME_PAUSE -> {
                    Period period = resolvePeriod(event, vault, currentYearMonth);
                    yield new FinancialEventRequest(event.eventId(), eventType.name(), null,
                            period.start(), period.end(), null, null, event.description());
                }
                case RECURRING_EXPENSE_CHANGE, INCOME_CHANGE, INVESTMENT_CONTRIBUTION_CHANGE -> {
                    Period period = resolvePeriod(event, vault, currentYearMonth);
                    BigDecimal delta = vault.requireMoney(event.monthlyDeltaReference());
                    if ("DECREASE".equals(event.changeDirection())) {
                        delta = delta.negate();
                    }
                    yield new FinancialEventRequest(event.eventId(), eventType.name(), null,
                            period.start(), period.end(), null, delta, event.description());
                }
            });
        }
        return financialEventMapper.map(scenarioName, requests, simulationStart, horizonMonths);
    }

    private Period resolvePeriod(ExternalAiScenarioDraft.EventDraft event,
                                 FinancialReferenceVault vault, YearMonth currentYearMonth) {
        YearMonth start = resolveDate(event.startDateExpression(), event.startDateReference(),
                vault, currentYearMonth);
        YearMonth end;
        if (event.durationReference() != null) {
            int durationMonths = vault.requireDurationMonths(event.durationReference());
            if (durationMonths <= 0) {
                throw new InvalidRequestException("Duration reference must be positive");
            }
            end = start.plusMonths(durationMonths - 1L);
        } else {
            end = resolveDate(event.endDateExpression(), event.endDateReference(), vault, currentYearMonth);
        }
        return new Period(start, end);
    }

    private YearMonth resolveDate(String expression, String referenceId,
                                  FinancialReferenceVault vault, YearMonth currentYearMonth) {
        if (referenceId != null) {
            return vault.requireDate(referenceId);
        }
        String normalized = expression.replaceAll("\\s+", " ").strip();
        if (normalized.matches("이번\\s*달")) {
            return currentYearMonth;
        }
        if (normalized.matches("다음\\s*달")) {
            return currentYearMonth.plusMonths(1);
        }
        if (normalized.equals("내년")) {
            return currentYearMonth.plusYears(1);
        }
        Matcher nextYear = NEXT_YEAR_MONTH.matcher(normalized);
        if (nextYear.matches()) {
            return YearMonth.of(currentYearMonth.getYear() + 1,
                    Integer.parseInt(nextYear.group("month")));
        }
        Matcher monthOffset = MONTH_OFFSET.matcher(normalized);
        if (monthOffset.matches()) {
            return currentYearMonth.plusMonths(Integer.parseInt(monthOffset.group("count")));
        }
        Matcher yearOffset = YEAR_OFFSET.matcher(normalized);
        if (yearOffset.matches()) {
            return currentYearMonth.plusYears(Integer.parseInt(yearOffset.group("count")));
        }
        throw new InvalidRequestException("Relative date expression could not be resolved");
    }

    private record Period(YearMonth start, YearMonth end) {
    }
}
