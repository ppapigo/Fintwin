package com.fintwin.fintwin.marketstress.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "fintwin.market-data")
public class MarketDataProperties {
    private boolean enabled;
    private String krxApiKey = "";
    private String bokEcosApiKey = "";
    @NotNull private URI krxBaseUrl = URI.create("https://data-dbg.krx.co.kr");
    @NotNull private URI bokEcosBaseUrl = URI.create("https://ecos.bok.or.kr");
    @NotNull private Duration connectTimeout = Duration.ofSeconds(3);
    @NotNull private Duration readTimeout = Duration.ofSeconds(5);
    @NotNull private Duration cacheTtl = Duration.ofMinutes(30);
    @Min(1_024) @Max(1_048_576) private int maxResponseBytes = 65_536;
    @Min(1) @Max(30) private int marketObservationStaleDays = 7;
    @Min(1) @Max(180) private int baseRateObservationStaleDays = 45;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getKrxApiKey() { return krxApiKey; }
    public void setKrxApiKey(String krxApiKey) { this.krxApiKey = krxApiKey; }
    public String getBokEcosApiKey() { return bokEcosApiKey; }
    public void setBokEcosApiKey(String bokEcosApiKey) { this.bokEcosApiKey = bokEcosApiKey; }
    public URI getKrxBaseUrl() { return krxBaseUrl; }
    public void setKrxBaseUrl(URI krxBaseUrl) { this.krxBaseUrl = krxBaseUrl; }
    public URI getBokEcosBaseUrl() { return bokEcosBaseUrl; }
    public void setBokEcosBaseUrl(URI bokEcosBaseUrl) { this.bokEcosBaseUrl = bokEcosBaseUrl; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
    public Duration getCacheTtl() { return cacheTtl; }
    public void setCacheTtl(Duration cacheTtl) { this.cacheTtl = cacheTtl; }
    public int getMaxResponseBytes() { return maxResponseBytes; }
    public void setMaxResponseBytes(int maxResponseBytes) { this.maxResponseBytes = maxResponseBytes; }
    public int getMarketObservationStaleDays() { return marketObservationStaleDays; }
    public void setMarketObservationStaleDays(int value) { this.marketObservationStaleDays = value; }
    public int getBaseRateObservationStaleDays() { return baseRateObservationStaleDays; }
    public void setBaseRateObservationStaleDays(int value) { this.baseRateObservationStaleDays = value; }

    @Override
    public String toString() {
        return "MarketDataProperties[enabled=" + enabled + ", krxApiKey=[REDACTED], bokEcosApiKey=[REDACTED], "
                + "krxBaseUrl=" + krxBaseUrl + ", bokEcosBaseUrl=" + bokEcosBaseUrl + "]";
    }
}
