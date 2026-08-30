package com.aicommerce.platform.delivery.infrastructure.provider;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

import com.aicommerce.platform.delivery.application.port.PlatformDeliveryReadPort;
import com.aicommerce.platform.delivery.application.port.PlatformMetricsReadPort;
import com.aicommerce.platform.delivery.domain.FreshnessStatus;
import com.aicommerce.platform.delivery.domain.PlatformEntityType;
import com.aicommerce.platform.delivery.domain.PlatformObservedState;
import com.aicommerce.platform.delivery.domain.ProviderKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

@Component
@Primary
@Profile("(local | test) & !production")
@ConditionalOnProperty(name = "platform.stage8.insights.live", havingValue = "true")
public class LiveMetaInsightsReadAdapter implements PlatformDeliveryReadPort, PlatformMetricsReadPort {
    static final String GRAPH_ORIGIN = "https://graph.facebook.com";
    static final String GRAPH_VERSION = "v22.0";
    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final int ATTEMPTS = 2;

    private final RestClient client;
    private final String accessToken;
    private final Clock clock;

    @Autowired
    public LiveMetaInsightsReadAdapter(
            @Value("${META_TEST_ACCESS_TOKEN:}") String accessToken, Clock clock) {
        this(fixedClient(), accessToken, clock);
    }

    static LiveMetaInsightsReadAdapter create(RestClient client, String accessToken, Clock clock) {
        return new LiveMetaInsightsReadAdapter(client, accessToken, clock);
    }

    private LiveMetaInsightsReadAdapter(RestClient client, String accessToken, Clock clock) {
        this.client = Objects.requireNonNull(client);
        this.accessToken = accessToken == null ? "" : accessToken;
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public ProviderKey providerKey() {
        return ProviderKey.META;
    }

    @Override
    public DeliveryObservation readObservedState(DeliveryReadCommand command) {
        Objects.requireNonNull(command);
        rejectIfTransactional();
        JsonNode body = get("/" + GRAPH_VERSION + "/" + command.durableExternalId(), "fields=effective_status");
        return new DeliveryObservation(mapStatus(text(body, "effective_status")), Optional.empty());
    }

    @Override
    public MetricObservation readWindow(MetricReadCommand command) {
        Objects.requireNonNull(command);
        rejectIfTransactional();
        String day = command.windowStart().atZone(TAIPEI).toLocalDate().format(DAY);
        String query = "fields=impressions,reach,clicks,spend,actions,action_values,account_currency"
                + "&time_range={\"since\":\"" + day + "\",\"until\":\"" + day + "\"}"
                + "&time_increment=1"
                + "&action_attribution_windows=['7d_click','1d_view']"
                + "&level=" + level(command.entityType());
        JsonNode body = get("/" + GRAPH_VERSION + "/" + command.durableExternalId() + "/insights", query);
        JsonNode data = body.path("data");
        if (!data.isArray() || data.size() == 0) {
            return empty(FreshnessStatus.FRESH);
        }
        if (data.size() != 1) {
            throw contractInvalid();
        }
        return mapRow(data.get(0));
    }

    private JsonNode get(String path, String rawQuery) {
        String token = token();
        RestClientResponseException lastResponse = null;
        RestClientException lastTransport = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                JsonNode body = client.get()
                        .uri(graphUri(path, rawQuery))
                        .headers(headers -> headers.setBearerAuth(token))
                        .retrieve()
                        .body(JsonNode.class);
                return body == null ? throwUnavailable() : body;
            } catch (RestClientResponseException exception) {
                lastResponse = exception;
                if (attempt < ATTEMPTS && retryable(exception.getStatusCode())) {
                    continue;
                }
                throw sanitized(exception);
            } catch (RestClientException exception) {
                lastTransport = exception;
                if (attempt < ATTEMPTS) {
                    continue;
                }
                throw new IllegalStateException("Meta Graph is temporarily unavailable", exception);
            }
        }
        if (lastResponse != null) {
            throw sanitized(lastResponse);
        }
        throw new IllegalStateException("Meta Graph is temporarily unavailable", lastTransport);
    }

    private MetricObservation mapRow(JsonNode row) {
        String currency = text(row, "account_currency");
        if (currency != null && !"TWD".equals(currency)) {
            throw contractInvalid();
        }
        Optional<BigDecimal> spend = money(row, "spend");
        Optional<String> conversionType = actionType(row.path("actions"));
        return new MetricObservation(
                count(row, "impressions"),
                count(row, "reach"),
                count(row, "clicks"),
                actionValue(row.path("actions"), conversionType),
                spend,
                actionMoney(row.path("action_values"), conversionType),
                FreshnessStatus.FRESH,
                Instant.now(clock).truncatedTo(ChronoUnit.SECONDS),
                Optional.empty());
    }

    private MetricObservation empty(FreshnessStatus freshness) {
        return new MetricObservation(
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), freshness,
                Instant.now(clock).truncatedTo(ChronoUnit.SECONDS), Optional.empty());
    }

    private static Optional<String> actionType(JsonNode actions) {
        if (!actions.isArray()) {
            return Optional.empty();
        }
        Optional<String> purchase = Optional.empty();
        for (JsonNode action : actions) {
            String type = text(action, "action_type");
            if ("omni_purchase".equals(type)) {
                return Optional.of("omni_purchase");
            }
            if ("purchase".equals(type)) {
                purchase = Optional.of("purchase");
            }
        }
        return purchase;
    }

    private static Optional<Long> actionValue(JsonNode actions, Optional<String> type) {
        return type.flatMap(wanted -> firstMatching(actions, wanted)).flatMap(LiveMetaInsightsReadAdapter::asCount);
    }

    private static Optional<BigDecimal> actionMoney(JsonNode actions, Optional<String> type) {
        return type.flatMap(wanted -> firstMatching(actions, wanted)).flatMap(LiveMetaInsightsReadAdapter::asMoney);
    }

    private static Optional<String> firstMatching(JsonNode actions, String type) {
        if (!actions.isArray()) {
            return Optional.empty();
        }
        for (JsonNode action : actions) {
            if (type.equals(text(action, "action_type"))) {
                String value = text(action, "value");
                return value == null ? Optional.empty() : Optional.of(value);
            }
        }
        return Optional.empty();
    }

    private static Optional<Long> count(JsonNode row, String field) {
        return asCount(text(row, field));
    }

    private static Optional<BigDecimal> money(JsonNode row, String field) {
        return asMoney(text(row, field));
    }

    private static Optional<Long> asCount(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            long value = new BigDecimal(raw).longValueExact();
            if (value < 0) {
                throw contractInvalid();
            }
            return Optional.of(value);
        } catch (ArithmeticException | NumberFormatException exception) {
            throw contractInvalid();
        }
    }

    private static Optional<BigDecimal> asMoney(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            BigDecimal value = new BigDecimal(raw);
            if (value.signum() < 0 || value.scale() > 6) {
                throw contractInvalid();
            }
            return Optional.of(value);
        } catch (NumberFormatException exception) {
            throw contractInvalid();
        }
    }

    private static PlatformObservedState mapStatus(String status) {
        if ("ACTIVE".equals(status)) {
            return PlatformObservedState.ACTIVE;
        }
        if ("PAUSED".equals(status) || "CAMPAIGN_PAUSED".equals(status) || "ADSET_PAUSED".equals(status)) {
            return PlatformObservedState.PAUSED;
        }
        return PlatformObservedState.UNKNOWN;
    }

    private static String level(PlatformEntityType type) {
        return switch (type) {
            case CAMPAIGN -> "campaign";
            case AD_SET -> "adset";
            case AD -> "ad";
        };
    }

    private String token() {
        if (accessToken.isBlank()) {
            throw new IllegalStateException("Meta test access token is not configured");
        }
        return accessToken;
    }

    private static void rejectIfTransactional() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("adapter invoked inside transaction");
        }
    }

    private static boolean retryable(HttpStatusCode status) {
        return status.value() == 429 || status.is5xxServerError();
    }

    private static IllegalStateException sanitized(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        if (status == 401 || status == 403) {
            return new IllegalStateException("Meta Graph access was denied");
        }
        if (status == 429) {
            return new IllegalStateException("Meta Graph rate limit was reached");
        }
        return new IllegalStateException("Meta Graph is temporarily unavailable");
    }

    private static URI graphUri(String path, String rawQuery) {
        StringBuilder query = new StringBuilder();
        for (String pair : rawQuery.split("&")) {
            int split = pair.indexOf('=');
            if (!query.isEmpty()) {
                query.append('&');
            }
            query.append(URLEncoder.encode(pair.substring(0, split), StandardCharsets.UTF_8).replace("+", "%20"))
                    .append('=')
                    .append(URLEncoder.encode(pair.substring(split + 1), StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return URI.create(GRAPH_ORIGIN + path + "?" + query);
    }

    private static JsonNode throwUnavailable() {
        throw new IllegalStateException("Meta Graph is temporarily unavailable");
    }

    private static IllegalArgumentException contractInvalid() {
        return new IllegalArgumentException("PLATFORM_CONTRACT_INVALID");
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText(null);
        return text == null || text.isBlank() ? null : text;
    }

    private static RestClient fixedClient() {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(15));
        return RestClient.builder().baseUrl(GRAPH_ORIGIN).requestFactory(factory).build();
    }
}
