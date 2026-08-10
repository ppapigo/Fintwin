function checkpointRows(comparison) {
  return comparison.checkpointComparisons.map((delta) => ({
    monthNumber: delta.monthNumber,
    yearMonth: delta.yearMonth,
    baseline: comparison.baseline.checkpoints.find((item) => item.monthNumber === delta.monthNumber) ?? null,
    whatIf: comparison.whatIf.checkpoints.find((item) => item.monthNumber === delta.monthNumber) ?? null,
    delta,
  }));
}

function monthlyRows(comparison) {
  return comparison.baseline.monthlyResults.map((baseline, index) => ({
    monthNumber: baseline.monthNumber,
    yearMonth: baseline.yearMonth,
    baseline,
    whatIf: comparison.whatIf.monthlyResults[index] ?? null,
    netWorthDelta: null,
  }));
}

export function directComparisonViewModel(comparison) {
  return {
    mode: "direct",
    status: "COMPLETED",
    fullComparisonAvailable: true,
    financialProfileVersion: comparison.financialProfileVersion,
    scenarioName: comparison.scenarioName,
    startYearMonth: comparison.startYearMonth,
    horizonMonths: comparison.horizonMonths,
    assumptions: comparison.assumptions,
    normalizedEvents: comparison.normalizedEvents,
    baseline: comparison.baseline,
    whatIf: comparison.whatIf,
    checkpoints: checkpointRows(comparison),
    finalComparison: comparison.finalComparison,
    impactSummary: comparison.impactSummary,
    monthlyRows: monthlyRows(comparison),
    risks: [],
    serviceWarnings: comparison.warnings,
    explanation: null,
    metadata: { aiUsed: false, provider: null, model: null, privacyMode: "STRICT", financialValuesTokenized: false, toolCallCount: 0 },
    trace: [],
    privacyNotice: "직접 입력 이벤트는 외부 AI를 호출하지 않습니다.",
    disclaimer: comparison.baseline.calculationBasis.disclaimer,
  };
}

export function naturalComparisonViewModel(response) {
  const result = response.typedResult;
  if (result?.comparisonDetails) {
    const comparison = directComparisonViewModel(result.comparisonDetails);
    return {
      ...comparison,
      mode: "natural",
      scenarioName: "자연어 What-if",
      risks: response.risks,
      serviceWarnings: result.serviceWarnings.length > 0
        ? result.serviceWarnings
        : comparison.serviceWarnings,
      explanation: response.explanation,
      metadata: response.metadata,
      trace: response.trace,
      privacyNotice: response.privacyNotice,
      disclaimer: comparison.disclaimer || response.disclaimer,
    };
  }
  return {
    mode: "natural",
    status: response.status,
    fullComparisonAvailable: false,
    financialProfileVersion: null,
    scenarioName: "자연어 What-if",
    startYearMonth: result?.startYearMonth ?? "",
    horizonMonths: result?.horizonMonths ?? 0,
    assumptions: null,
    normalizedEvents: [],
    baseline: result ? { finalNetWorth: result.baselineFinalNetWorth } : null,
    whatIf: result ? { finalNetWorth: result.whatIfFinalNetWorth } : null,
    checkpoints: [],
    finalComparison: result ? {
      netWorthDelta: result.netWorthDelta,
      liquidAssetsDelta: result.liquidAssetsDelta,
      debtDelta: result.debtDelta,
      cumulativeIncomeDelta: result.cumulativeIncomeDelta,
      cumulativeConsumptionDelta: result.cumulativeConsumptionDelta,
    } : null,
    impactSummary: result ? {
      incomeDelta: result.cumulativeIncomeDelta,
      consumptionDelta: result.cumulativeConsumptionDelta,
      liquidAssetsDelta: result.liquidAssetsDelta,
      debtDelta: result.debtDelta,
      netWorthDelta: result.netWorthDelta,
    } : null,
    monthlyRows: [],
    risks: response.risks,
    serviceWarnings: result?.serviceWarnings ?? [],
    explanation: response.explanation,
    metadata: response.metadata,
    trace: response.trace,
    privacyNotice: response.privacyNotice,
    disclaimer: response.disclaimer,
  };
}
