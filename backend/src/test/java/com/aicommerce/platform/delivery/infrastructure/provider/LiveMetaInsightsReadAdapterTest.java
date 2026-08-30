package com.aicommerce.platform.delivery.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.aicommerce.platform.delivery.application.port.PlatformDeliveryReadPort;
import com.aicommerce.platform.delivery.application.port.PlatformMetricsReadPort;
import com.aicommerce.platform.delivery.domain.FreshnessStatus;
import com.aicommerce.platform.delivery.domain.PlatformDesiredState;
import com.aicommerce.platform.delivery.domain.PlatformEntityType;
import com.aicommerce.platform.delivery.domain.PlatformObservedState;
import com.aicommerce.platform.delivery.domain.ProviderKey;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;

class LiveMetaInsightsReadAdapterTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-22T16:00:00Z"), ZoneOffset.UTC);
    private static final String TOKEN = "secret-token-value";
    private static final UUID ACCOUNT = UUID.fromString("00000000-0000-4000-8000-00000000008d");
    private static final UUID CAMPAIGN = UUID.fromString("00000000-0000-4000-8000-00000000018d");

    @Test
    void mapsPausedActiveAndUnknownDeliveryFromPinnedGraphOrigin() {
        RestClient.Builder builder = RestClient.builder().baseUrl(LiveMetaInsightsReadAdapter.GRAPH_ORIGIN);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        expectDelivery(server, "camp_1", "PAUSED");
        expectDelivery(server, "camp_2", "ACTIVE");
        expectDelivery(server, "camp_3", "CAMPAIGN_PAUSED");
        expectDelivery(server, "camp_4", "WITH_ISSUES");
        LiveMetaInsightsReadAdapter adapter = adapter(builder);

        assertThat(adapter.providerKey()).isEqualTo(ProviderKey.META);
        assertThat(adapter.readObservedState(delivery("camp_1")).observedState())
                .isEqualTo(PlatformObservedState.PAUSED);
        assertThat(adapter.readObservedState(delivery("camp_2")).observedState())
                .isEqualTo(PlatformObservedState.ACTIVE);
        assertThat(adapter.readObservedState(delivery("camp_3")).observedState())
                .isEqualTo(PlatformObservedState.PAUSED);
        assertThat(adapter.readObservedState(delivery("camp_4")).observedState())
                .isEqualTo(PlatformObservedState.UNKNOWN);
        server.verify();
    }

    @Test
    void mapsOmniPurchaseOverPurchaseAndLeavesMissingMetricsNull() {
        RestClient.Builder successBuilder = RestClient.builder().baseUrl(LiveMetaInsightsReadAdapter.GRAPH_ORIGIN);
        MockRestServiceServer successServer = MockRestServiceServer.bindTo(successBuilder).build();
        expectInsights(successServer, """
                {"data":[{"impressions":"10000","reach":"8000","clicks":"100","spend":"25.50",
                "account_currency":"TWD",
                "actions":[{"action_type":"purchase","value":"99"},{"action_type":"omni_purchase","value":"4"}],
                "action_values":[{"action_type":"purchase","value":"999"},{"action_type":"omni_purchase","value":"100.00"}]}]}
                """);
        var success = adapter(successBuilder).readWindow(metrics());
        assertThat(success.impressions()).contains(10_000L);
        assertThat(success.reach()).contains(8_000L);
        assertThat(success.clicks()).contains(100L);
        assertThat(success.conversions()).contains(4L);
        assertThat(success.spend()).contains(new BigDecimal("25.50"));
        assertThat(success.revenue()).contains(new BigDecimal("100.00"));
        assertThat(success.freshnessStatus()).isEqualTo(FreshnessStatus.FRESH);
        successServer.verify();

        RestClient.Builder purchaseBuilder = RestClient.builder().baseUrl(LiveMetaInsightsReadAdapter.GRAPH_ORIGIN);
        MockRestServiceServer purchaseServer = MockRestServiceServer.bindTo(purchaseBuilder).build();
        expectInsights(purchaseServer, """
                {"data":[{"impressions":"10","clicks":"1","spend":"1",
                "actions":[{"action_type":"purchase","value":"2"}],
                "action_values":[{"action_type":"purchase","value":"3.5"}]}]}
                """);
        var purchase = adapter(purchaseBuilder).readWindow(metrics());
        assertThat(purchase.conversions()).contains(2L);
        assertThat(purchase.revenue()).contains(new BigDecimal("3.5"));
        assertThat(purchase.reach()).isEmpty();
        purchaseServer.verify();

        RestClient.Builder partialBuilder = RestClient.builder().baseUrl(LiveMetaInsightsReadAdapter.GRAPH_ORIGIN);
        MockRestServiceServer partialServer = MockRestServiceServer.bindTo(partialBuilder).build();
        expectInsights(partialServer, """
                {"data":[{"impressions":"10000","clicks":"100"}]}
                """);
        var partial = adapter(partialBuilder).readWindow(metrics());
        assertThat(partial.impressions()).contains(10_000L);
        assertThat(partial.clicks()).contains(100L);
        assertThat(partial.reach()).isEmpty();
        assertThat(partial.conversions()).isEmpty();
        assertThat(partial.spend()).isEmpty();
        assertThat(partial.revenue()).isEmpty();
        assertThat(partial.freshnessStatus()).isEqualTo(FreshnessStatus.FRESH);
        partialServer.verify();

        RestClient.Builder emptyBuilder = RestClient.builder().baseUrl(LiveMetaInsightsReadAdapter.GRAPH_ORIGIN);
        MockRestServiceServer emptyServer = MockRestServiceServer.bindTo(emptyBuilder).build();
        expectInsights(emptyServer, "{\"data\":[]}");
        var empty = adapter(emptyBuilder).readWindow(metrics());
        assertThat(empty.impressions()).isEmpty();
        assertThat(empty.spend()).isEmpty();
        assertThat(empty.freshnessStatus()).isEqualTo(FreshnessStatus.FRESH);
        emptyServer.verify();
    }

    @Test
    void rejectsNegativeSpendNonTwdAndMalformedNumbers() {
        assertContractInvalid("{\"data\":[{\"spend\":\"-1\",\"account_currency\":\"TWD\"}]}");
        assertContractInvalid("{\"data\":[{\"spend\":\"1\",\"account_currency\":\"USD\"}]}");
        assertContractInvalid("{\"data\":[{\"impressions\":\"1.5\"}]}");
        assertContractInvalid("{\"data\":[{},{}]}");
    }

    @Test
    void sanitizesUnauthorizedResponsesAndKeepsTokenOutOfTheUri() {
        RestClient.Builder builder = RestClient.builder().baseUrl(LiveMetaInsightsReadAdapter.GRAPH_ORIGIN);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), request -> {
                    assertThat(request.getURI().getHost()).isEqualTo("graph.facebook.com");
                    assertThat(request.getURI().toString()).doesNotContain(TOKEN);
                    assertThat(request.getURI().getQuery()).doesNotContain(TOKEN);
                })
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .body("{\"error\":{\"message\":\"token-value leaked\"}}")
                        .contentType(MediaType.APPLICATION_JSON));
        LiveMetaInsightsReadAdapter adapter = adapter(builder);

        assertThatThrownBy(() -> adapter.readObservedState(delivery("camp_1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Meta Graph access was denied")
                .hasMessageNotContaining(TOKEN)
                .hasMessageNotContaining("token-value leaked");
        server.verify();
    }

    @Test
    void retriesOnceOnTooManyRequestsThenMapsDelivery() {
        RestClient.Builder builder = RestClient.builder().baseUrl(LiveMetaInsightsReadAdapter.GRAPH_ORIGIN);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), request -> { })
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .body("secret provider details").contentType(MediaType.TEXT_PLAIN));
        expectDelivery(server, "camp_1", "ADSET_PAUSED");
        assertThat(adapter(builder).readObservedState(delivery("camp_1")).observedState())
                .isEqualTo(PlatformObservedState.PAUSED);
        server.verify();
    }

    @Test
    void rejectsInvocationInsideATransactionAndBlankToken() {
        RestClient.Builder builder = RestClient.builder().baseUrl(LiveMetaInsightsReadAdapter.GRAPH_ORIGIN);
        LiveMetaInsightsReadAdapter adapter = adapter(builder);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatThrownBy(() -> adapter.readObservedState(delivery("camp_1")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("adapter invoked inside transaction");
            assertThatThrownBy(() -> adapter.readWindow(metrics()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("adapter invoked inside transaction");
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }

        LiveMetaInsightsReadAdapter blank = LiveMetaInsightsReadAdapter.create(builder.build(), "  ", CLOCK);
        assertThatThrownBy(() -> blank.readObservedState(delivery("camp_1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Meta test access token is not configured");
    }

    private static void assertContractInvalid(String body) {
        RestClient.Builder builder = RestClient.builder().baseUrl(LiveMetaInsightsReadAdapter.GRAPH_ORIGIN);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        expectInsights(server, body);
        assertThatThrownBy(() -> adapter(builder).readWindow(metrics()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("PLATFORM_CONTRACT_INVALID");
        server.verify();
    }

    private static void expectDelivery(MockRestServiceServer server, String id, String status) {
        server.expect(once(), request -> {
                    assertThat(request.getURI().getScheme()).isEqualTo("https");
                    assertThat(request.getURI().getHost()).isEqualTo("graph.facebook.com");
                    assertThat(request.getURI().getPath())
                            .isEqualTo("/" + LiveMetaInsightsReadAdapter.GRAPH_VERSION + "/" + id);
                    assertThat(request.getURI().getQuery()).contains("fields=effective_status");
                    assertThat(request.getURI().toString()).doesNotContain(TOKEN);
                })
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andRespond(withSuccess("{\"effective_status\":\"" + status + "\"}", MediaType.APPLICATION_JSON));
    }

    private static void expectInsights(MockRestServiceServer server, String body) {
        server.expect(once(), request -> {
                    assertThat(request.getURI().getHost()).isEqualTo("graph.facebook.com");
                    assertThat(request.getURI().getPath())
                            .isEqualTo("/" + LiveMetaInsightsReadAdapter.GRAPH_VERSION + "/camp_1/insights");
                    String query = request.getURI().getQuery();
                    assertThat(query).contains("fields=impressions,reach,clicks,spend,actions,action_values");
                    assertThat(query).contains("time_increment=1");
                    assertThat(query).contains("level=campaign");
                    assertThat(query).contains("7d_click");
                    assertThat(query).contains("1d_view");
                    assertThat(query).contains("2026-08-22");
                    assertThat(request.getURI().toString()).doesNotContain(TOKEN);
                })
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private static LiveMetaInsightsReadAdapter adapter(RestClient.Builder builder) {
        return LiveMetaInsightsReadAdapter.create(builder.build(), TOKEN, CLOCK);
    }

    private static PlatformDeliveryReadPort.DeliveryReadCommand delivery(String externalId) {
        return new PlatformDeliveryReadPort.DeliveryReadCommand(
                ACCOUNT, PlatformEntityType.CAMPAIGN, CAMPAIGN, externalId, PlatformDesiredState.PAUSED);
    }

    private static PlatformMetricsReadPort.MetricReadCommand metrics() {
        return new PlatformMetricsReadPort.MetricReadCommand(
                ACCOUNT, PlatformEntityType.CAMPAIGN, CAMPAIGN, "camp_1",
                Instant.parse("2026-08-21T16:00:00Z"), Instant.parse("2026-08-22T16:00:00Z"),
                "Asia/Taipei", 7, 1, "TWD");
    }
}
