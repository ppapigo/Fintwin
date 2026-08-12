package com.fintwin.fintwin.marketstress.marketdata;

public enum MarketDataIssueCode {
    NONE,
    FEATURE_DISABLED,
    CREDENTIAL_MISSING,
    PROVIDER_TIMEOUT,
    PROVIDER_UNAVAILABLE,
    INVALID_PROVIDER_RESPONSE,
    DATA_NOT_FOUND,
    OBSERVATION_TOO_OLD,
    STALE_CACHE_FALLBACK
}
