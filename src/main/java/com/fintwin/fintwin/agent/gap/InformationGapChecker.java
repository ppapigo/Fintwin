package com.fintwin.fintwin.agent.gap;

import com.fintwin.fintwin.agent.domain.AgentCommand;
import com.fintwin.fintwin.agent.domain.AgentIntent;
import com.fintwin.fintwin.agent.domain.MissingInformation;
import com.fintwin.fintwin.agent.domain.MissingInformationCode;
import com.fintwin.fintwin.financialprofile.service.FinancialProfileService;
import com.fintwin.fintwin.global.error.InvalidRequestException;
import com.fintwin.fintwin.scenario.dto.FinancialEventRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public final class InformationGapChecker {
    private final FinancialProfileService financialProfileService;

    public InformationGapChecker(FinancialProfileService financialProfileService) {
        this.financialProfileService = financialProfileService;
    }

    public List<MissingInformation> check(Long currentUserId, AgentCommand command) {
        validateIntentFieldConflicts(command);
        List<MissingInformation> missing = new ArrayList<>();
        addCommonMissing(command, missing);

        switch (command.intent()) {
            case BASELINE_SIMULATION -> { }
            case WHAT_IF_SIMULATION -> addWhatIfMissing(command, missing);
            case GOAL_REVERSE_SIMULATION -> addGoalMissing(command, missing);
        }
        addProfileDependentMissing(currentUserId, command, missing);
        return List.copyOf(missing);
    }

    private void validateIntentFieldConflicts(AgentCommand command) {
        switch (command.intent()) {
            case BASELINE_SIMULATION -> {
                if (command.events() != null || command.goalType() != null || command.targetAmount() != null) {
                    throw new InvalidRequestException("Baseline intent does not allow events or goal fields");
                }
            }
            case WHAT_IF_SIMULATION -> {
                if (command.goalType() != null || command.targetAmount() != null) {
                    throw new InvalidRequestException("What-if intent does not allow goal fields");
                }
            }
            case GOAL_REVERSE_SIMULATION -> {
                if (command.events() != null) {
                    throw new InvalidRequestException("Goal intent does not allow events");
                }
            }
        }
    }

    private void addCommonMissing(AgentCommand command, List<MissingInformation> missing) {
        AgentIntent intent = command.intent();
        if (command.startYearMonth() == null) {
            missing.add(missing(MissingInformationCode.START_YEAR_MONTH_REQUIRED, "startYearMonth",
                    "시뮬레이션 시작 연월을 입력해주세요.", intent));
        }
        if (command.horizonMonths() == null) {
            missing.add(missing(MissingInformationCode.HORIZON_REQUIRED, "horizonMonths",
                    "시뮬레이션 기간을 월 단위로 입력해주세요.", intent));
        }
        if (command.assumptions() == null) {
            missing.add(missing(MissingInformationCode.ASSUMPTIONS_REQUIRED, "assumptions",
                    "시뮬레이션에 사용할 가정을 입력해주세요.", intent));
        }
    }

    private void addWhatIfMissing(AgentCommand command, List<MissingInformation> missing) {
        if (command.events() == null || command.events().isEmpty()) {
            missing.add(missing(MissingInformationCode.EVENTS_REQUIRED, "events",
                    "비교할 금융 이벤트를 하나 이상 입력해주세요.", command.intent()));
            return;
        }
        for (int index = 0; index < command.events().size(); index++) {
            FinancialEventRequest event = command.events().get(index);
            String prefix = "events[" + index + "]";
            switch (event.eventType()) {
                case "ONE_TIME_EXPENSE", "EXTRA_DEBT_REPAYMENT" -> {
                    if (event.amount() == null) {
                        missing.add(missing(MissingInformationCode.EVENT_AMOUNT_REQUIRED, prefix + ".amount",
                                "해당 금융 이벤트의 금액을 입력해주세요.", command.intent()));
                    }
                    if (event.effectiveYearMonth() == null) {
                        missing.add(missing(MissingInformationCode.EVENT_DATE_REQUIRED,
                                prefix + ".effectiveYearMonth", "해당 금융 이벤트의 발생 연월을 입력해주세요.",
                                command.intent()));
                    }
                }
                case "RECURRING_EXPENSE_CHANGE", "INCOME_CHANGE", "INVESTMENT_CONTRIBUTION_CHANGE" -> {
                    if (event.monthlyDelta() == null) {
                        missing.add(missing(MissingInformationCode.EVENT_AMOUNT_REQUIRED,
                                prefix + ".monthlyDelta", "해당 금융 이벤트의 월 증감 금액을 입력해주세요.",
                                command.intent()));
                    }
                    addPeriodMissing(event, prefix, command.intent(), missing);
                }
                case "INCOME_PAUSE" -> addPeriodMissing(event, prefix, command.intent(), missing);
                default -> { }
            }
        }
    }

    private void addPeriodMissing(FinancialEventRequest event, String prefix, AgentIntent intent,
                                  List<MissingInformation> missing) {
        if (event.startYearMonth() == null) {
            missing.add(missing(MissingInformationCode.EVENT_PERIOD_REQUIRED, prefix + ".startYearMonth",
                    "해당 금융 이벤트의 시작 연월을 입력해주세요.", intent));
        }
        if (event.endYearMonth() == null) {
            missing.add(missing(MissingInformationCode.EVENT_PERIOD_REQUIRED, prefix + ".endYearMonth",
                    "해당 금융 이벤트의 종료 연월을 입력해주세요.", intent));
        }
    }

    private void addGoalMissing(AgentCommand command, List<MissingInformation> missing) {
        if (command.goalType() == null || command.goalType().isBlank()) {
            missing.add(missing(MissingInformationCode.GOAL_TYPE_REQUIRED, "goalType",
                    "역산할 목표 유형을 입력해주세요.", command.intent()));
        }
        if (command.targetAmount() == null) {
            missing.add(missing(MissingInformationCode.TARGET_AMOUNT_REQUIRED, "targetAmount",
                    "목표 금액을 입력해주세요.", command.intent()));
        }
    }

    private void addProfileDependentMissing(Long currentUserId, AgentCommand command,
                                            List<MissingInformation> missing) {
        if (command.assumptions() == null || command.assumptions().monthlyDebtPayment() != null) {
            return;
        }
        financialProfileService.getCurrentIfPresent(currentUserId)
                .filter(profile -> profile.totalLoanBalance().signum() > 0)
                .ifPresent(profile -> missing.add(missing(
                        MissingInformationCode.MONTHLY_DEBT_PAYMENT_REQUIRED,
                        "assumptions.monthlyDebtPayment",
                        "현재 부채에 적용할 월 상환액을 입력해주세요.", command.intent())));
    }

    private MissingInformation missing(MissingInformationCode code, String field, String question,
                                       AgentIntent intent) {
        return new MissingInformation(code, field, question, intent);
    }
}
