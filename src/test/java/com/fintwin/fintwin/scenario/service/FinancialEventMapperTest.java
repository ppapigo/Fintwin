package com.fintwin.fintwin.scenario.service;

import com.fintwin.fintwin.global.error.InvalidRequestException;
import com.fintwin.fintwin.scenario.domain.FinancialEvent;
import com.fintwin.fintwin.scenario.domain.ScenarioDefinition;
import com.fintwin.fintwin.scenario.dto.FinancialEventRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FinancialEventMapperTest {
    private static final YearMonth START = YearMonth.of(2026, 8);
    private final FinancialEventMapper mapper = new FinancialEventMapper();

    @Test
    void normalizesEventOrderAndClipsPartiallyOverlappingPeriod() {
        FinancialEventRequest laterId = period("z-event", "INCOME_PAUSE", START.minusMonths(2),
                START.plusMonths(2), null);
        FinancialEventRequest earlierId = oneTime("a-event", "ONE_TIME_EXPENSE", START.plusMonths(1), "1000");

        ScenarioDefinition scenario = mapper.map("scenario", List.of(laterId, earlierId), START, 12);

        assertThat(scenario.events()).extracting(FinancialEvent::eventId).containsExactly("a-event", "z-event");
        FinancialEvent.IncomePause pause = (FinancialEvent.IncomePause) scenario.events().get(1);
        assertThat(pause.startYearMonth()).isEqualTo(START);
        assertThat(pause.endYearMonth()).isEqualTo(START.plusMonths(2));
        assertThat(scenario.warnings()).hasSize(1);
    }

    @Test
    void rejectsDuplicateEventId() {
        FinancialEventRequest first = oneTime("duplicate", "ONE_TIME_EXPENSE", START, "1000");
        FinancialEventRequest second = period("duplicate", "INCOME_CHANGE", START, START, BigDecimal.ONE);

        assertThatThrownBy(() -> mapper.map("scenario", List.of(first, second), START, 12))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("duplicate eventId: duplicate");
    }

    @Test
    void rejectsMoreThanTwentyEvents() {
        List<FinancialEventRequest> events = new ArrayList<>();
        for (int index = 0; index < 21; index++) {
            events.add(oneTime("event-" + index, "ONE_TIME_EXPENSE", START, "1"));
        }

        assertThatThrownBy(() -> mapper.map("scenario", events, START, 12))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("events must contain between 1 and 20 items");
    }

    @Test
    void rejectsInvalidAndCompletelyOutsidePeriods() {
        FinancialEventRequest reversed = period("reversed", "INCOME_PAUSE", START.plusMonths(2), START, null);
        FinancialEventRequest outside = period("outside", "INCOME_CHANGE", START.minusMonths(5),
                START.minusMonths(1), BigDecimal.ONE);

        assertThatThrownBy(() -> mapper.map("scenario", List.of(reversed), START, 12))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("startYearMonth");
        assertThatThrownBy(() -> mapper.map("scenario", List.of(outside), START, 12))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("outside the simulation range");
    }

    @Test
    void rejectsOneTimeOutsideRangeAndUnsupportedType() {
        FinancialEventRequest outside = oneTime("outside", "ONE_TIME_EXPENSE", START.minusMonths(1), "1000");
        FinancialEventRequest unsupported = oneTime("unsupported", "NEW_LOAN", START, "1000");

        assertThatThrownBy(() -> mapper.map("scenario", List.of(outside), START, 12))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("outside the simulation range");
        assertThatThrownBy(() -> mapper.map("scenario", List.of(unsupported), START, 12))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("unsupported eventType: NEW_LOAN");
    }

    @Test
    void rejectsMissingTypeSpecificFieldsAndNonPositiveOneTimeAmount() {
        FinancialEventRequest missingDelta = period("missing", "INCOME_CHANGE", START, START, null);
        FinancialEventRequest zeroAmount = oneTime("zero", "EXTRA_DEBT_REPAYMENT", START, "0");

        assertThatThrownBy(() -> mapper.map("scenario", List.of(missingDelta), START, 12))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("monthlyDelta is required");
        assertThatThrownBy(() -> mapper.map("scenario", List.of(zeroAmount), START, 12))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("amount must be positive");
    }

    private FinancialEventRequest oneTime(String id, String type, YearMonth effectiveMonth, String amount) {
        return new FinancialEventRequest(id, type, effectiveMonth, null, null, new BigDecimal(amount), null,
                "description");
    }

    private FinancialEventRequest period(String id, String type, YearMonth start, YearMonth end,
                                         BigDecimal monthlyDelta) {
        return new FinancialEventRequest(id, type, null, start, end, null, monthlyDelta, "description");
    }
}
