package com.fintwin.fintwin.scenario.service;

import com.fintwin.fintwin.global.error.InvalidRequestException;
import com.fintwin.fintwin.scenario.domain.FinancialEvent;
import com.fintwin.fintwin.scenario.domain.FinancialEventType;
import com.fintwin.fintwin.scenario.domain.ScenarioDefinition;
import com.fintwin.fintwin.scenario.dto.FinancialEventRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class FinancialEventMapper {
    private static final int MAX_SCENARIO_NAME_LENGTH = 100;
    private static final int MAX_EVENT_ID_LENGTH = 100;
    private static final int MAX_DESCRIPTION_LENGTH = 200;

    public ScenarioDefinition map(String scenarioName, List<FinancialEventRequest> requests,
                                  YearMonth simulationStart, int horizonMonths) {
        return map(scenarioName, requests, simulationStart, horizonMonths, false);
    }

    public ScenarioDefinition mapAllowingEmpty(String scenarioName, List<FinancialEventRequest> requests,
                                               YearMonth simulationStart, int horizonMonths) {
        return map(scenarioName, requests, simulationStart, horizonMonths, true);
    }

    private ScenarioDefinition map(String scenarioName, List<FinancialEventRequest> requests,
                                   YearMonth simulationStart, int horizonMonths, boolean allowEmpty) {
        requireText(scenarioName, "scenarioName", MAX_SCENARIO_NAME_LENGTH);
        if (requests == null || (!allowEmpty && requests.isEmpty()) || requests.size() > 20) {
            throw new InvalidRequestException(allowEmpty
                    ? "events must contain at most 20 items"
                    : "events must contain between 1 and 20 items");
        }
        YearMonth simulationEnd = simulationStart.plusMonths(horizonMonths - 1L);
        Set<String> eventIds = new HashSet<>();
        List<String> warnings = new ArrayList<>();
        List<FinancialEvent> events = new ArrayList<>(requests.size());

        for (FinancialEventRequest request : requests) {
            if (request == null) {
                throw new InvalidRequestException("events must not contain null");
            }
            requireText(request.eventId(), "eventId", MAX_EVENT_ID_LENGTH);
            requireText(request.description(), "description", MAX_DESCRIPTION_LENGTH);
            if (!eventIds.add(request.eventId())) {
                throw new InvalidRequestException("duplicate eventId: " + request.eventId());
            }
            FinancialEventType eventType = parseType(request.eventType());
            events.add(mapEvent(request, eventType, simulationStart, simulationEnd, warnings));
        }

        events.sort(Comparator.comparing(FinancialEvent::eventId));
        return new ScenarioDefinition(scenarioName, events, warnings);
    }

    private FinancialEvent mapEvent(FinancialEventRequest request, FinancialEventType type,
                                    YearMonth simulationStart, YearMonth simulationEnd, List<String> warnings) {
        return switch (type) {
            case ONE_TIME_EXPENSE -> new FinancialEvent.OneTimeExpense(request.eventId(), request.description(),
                    requireWithinRange(request.effectiveYearMonth(), request.eventId(), simulationStart, simulationEnd),
                    requirePositive(request.amount(), "amount", request.eventId()));
            case EXTRA_DEBT_REPAYMENT -> new FinancialEvent.ExtraDebtRepayment(request.eventId(),
                    request.description(), requireWithinRange(request.effectiveYearMonth(), request.eventId(),
                    simulationStart, simulationEnd), requirePositive(request.amount(), "amount", request.eventId()));
            case RECURRING_EXPENSE_CHANGE -> {
                Period period = normalizePeriod(request, simulationStart, simulationEnd, warnings);
                yield new FinancialEvent.RecurringExpenseChange(request.eventId(), request.description(),
                        period.start(), period.end(), requireDelta(request.monthlyDelta(), request.eventId()));
            }
            case INCOME_CHANGE -> {
                Period period = normalizePeriod(request, simulationStart, simulationEnd, warnings);
                yield new FinancialEvent.IncomeChange(request.eventId(), request.description(), period.start(),
                        period.end(), requireDelta(request.monthlyDelta(), request.eventId()));
            }
            case INCOME_PAUSE -> {
                Period period = normalizePeriod(request, simulationStart, simulationEnd, warnings);
                yield new FinancialEvent.IncomePause(request.eventId(), request.description(), period.start(),
                        period.end());
            }
            case INVESTMENT_CONTRIBUTION_CHANGE -> {
                Period period = normalizePeriod(request, simulationStart, simulationEnd, warnings);
                yield new FinancialEvent.InvestmentContributionChange(request.eventId(), request.description(),
                        period.start(), period.end(), requireDelta(request.monthlyDelta(), request.eventId()));
            }
        };
    }

    private Period normalizePeriod(FinancialEventRequest request, YearMonth simulationStart,
                                   YearMonth simulationEnd, List<String> warnings) {
        YearMonth requestedStart = requireDate(request.startYearMonth(), "startYearMonth", request.eventId());
        YearMonth requestedEnd = requireDate(request.endYearMonth(), "endYearMonth", request.eventId());
        if (requestedStart.isAfter(requestedEnd)) {
            throw new InvalidRequestException("startYearMonth must not be after endYearMonth: " + request.eventId());
        }
        if (requestedEnd.isBefore(simulationStart) || requestedStart.isAfter(simulationEnd)) {
            throw new InvalidRequestException("event period is outside the simulation range: " + request.eventId());
        }
        YearMonth effectiveStart = requestedStart.isBefore(simulationStart) ? simulationStart : requestedStart;
        YearMonth effectiveEnd = requestedEnd.isAfter(simulationEnd) ? simulationEnd : requestedEnd;
        if (!effectiveStart.equals(requestedStart) || !effectiveEnd.equals(requestedEnd)) {
            warnings.add("Event " + request.eventId() + " was clipped to " + effectiveStart + " through "
                    + effectiveEnd + ".");
        }
        return new Period(effectiveStart, effectiveEnd);
    }

    private FinancialEventType parseType(String rawType) {
        requireText(rawType, "eventType", 100);
        try {
            return FinancialEventType.valueOf(rawType.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("unsupported eventType: " + rawType);
        }
    }

    private YearMonth requireWithinRange(YearMonth value, String eventId, YearMonth start, YearMonth end) {
        YearMonth effective = requireDate(value, "effectiveYearMonth", eventId);
        if (effective.isBefore(start) || effective.isAfter(end)) {
            throw new InvalidRequestException("one-time event is outside the simulation range: " + eventId);
        }
        return effective;
    }

    private YearMonth requireDate(YearMonth value, String field, String eventId) {
        if (value == null) {
            throw new InvalidRequestException(field + " is required for event: " + eventId);
        }
        return value;
    }

    private BigDecimal requirePositive(BigDecimal value, String field, String eventId) {
        if (value == null || value.signum() <= 0) {
            throw new InvalidRequestException(field + " must be positive for event: " + eventId);
        }
        return value;
    }

    private BigDecimal requireDelta(BigDecimal value, String eventId) {
        if (value == null) {
            throw new InvalidRequestException("monthlyDelta is required for event: " + eventId);
        }
        return value;
    }

    private void requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException(field + " is required");
        }
        if (value.length() > maxLength) {
            throw new InvalidRequestException(field + " must be at most " + maxLength + " characters");
        }
    }

    private record Period(YearMonth start, YearMonth end) {
    }
}
