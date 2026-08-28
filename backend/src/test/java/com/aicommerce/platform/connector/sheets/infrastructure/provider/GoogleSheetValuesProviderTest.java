package com.aicommerce.platform.connector.sheets.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Date;

import com.aicommerce.platform.connector.sheets.application.SheetProviderException;
import com.aicommerce.platform.connector.sheets.application.SheetSource;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GoogleSheetValuesProviderTest {

    @Test
    void usesFixedGoogleOriginEncodedPathReadonlyTokenAndFormattedRows() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://sheets.googleapis.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), request -> {
                    assertThat(request.getMethod()).isEqualTo(HttpMethod.GET);
                    assertThat(request.getURI().getScheme()).isEqualTo("https");
                    assertThat(request.getURI().getHost()).isEqualTo("sheets.googleapis.com");
                    assertThat(request.getURI().getPath()).contains("/v4/spreadsheets/sheet_123/values/");
                    assertThat(request.getURI().getQuery()).contains("majorDimension=ROWS")
                            .contains("valueRenderOption=FORMATTED_VALUE");
                })
                .andExpect(header("Authorization", "Bearer test-token"))
                .andRespond(withSuccess("{\"values\":[[\"product_uuid\",\"product_id\",\"product_name\"],[\"\",\"\",\"One\"]]}",
                        MediaType.APPLICATION_JSON));
        GoogleSheetValuesProvider provider = new GoogleSheetValuesProvider(builder.build(), this::credentials);

        var result = provider.read(new SheetSource("sheet_123", "Products", "'Products'!A1:M1001"));

        assertThat(result.values()).hasSize(2);
        assertThat(result.values().get(1)).containsExactly("", "", "One");
        server.verify();
    }

    @Test
    void retriesOnlyBoundedTransientResponsesAndSanitizesFailure() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://sheets.googleapis.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), request -> { })
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .body("secret provider details").contentType(MediaType.TEXT_PLAIN));
        server.expect(once(), request -> { })
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .body("secret provider details").contentType(MediaType.TEXT_PLAIN));
        GoogleSheetValuesProvider provider = new GoogleSheetValuesProvider(builder.build(), this::credentials);

        assertThatThrownBy(() -> provider.read(new SheetSource("sheet_123", "Products", "'Products'!A1:M1001")))
                .isInstanceOfSatisfying(SheetProviderException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("GOOGLE_RATE_LIMITED");
                    assertThat(exception.getMessage()).doesNotContain("secret provider details");
                });
        server.verify();
    }

    @Test
    void permissionFailureIsNotRetriedAndProviderBodyIsNotExposed() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://sheets.googleapis.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), request -> { })
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .body("credential payload").contentType(MediaType.TEXT_PLAIN));
        GoogleSheetValuesProvider provider = new GoogleSheetValuesProvider(builder.build(), this::credentials);

        assertThatThrownBy(() -> provider.read(new SheetSource("sheet_123", "Products", "'Products'!A1:M1001")))
                .isInstanceOfSatisfying(SheetProviderException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("GOOGLE_PERMISSION_DENIED");
                    assertThat(exception.getMessage()).doesNotContain("credential payload");
                });
        server.verify();
    }

    @Test
    void authenticationFailureIsNotRetriedAndProviderBodyIsNotExposed() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://sheets.googleapis.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), request -> { })
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .body("token-value").contentType(MediaType.TEXT_PLAIN));
        GoogleSheetValuesProvider provider = new GoogleSheetValuesProvider(builder.build(), this::credentials);

        assertThatThrownBy(() -> provider.read(new SheetSource("sheet_123", "Products", "'Products'!A1:M1001")))
                .isInstanceOfSatisfying(SheetProviderException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("GOOGLE_AUTH_UNAVAILABLE");
                    assertThat(exception.getMessage()).doesNotContain("token-value");
                });
        server.verify();
    }

    @Test
    void retriesOnceOnTooManyRequestsThenReturnsFormattedRows() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://sheets.googleapis.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), request -> { })
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .body("secret provider details").contentType(MediaType.TEXT_PLAIN));
        server.expect(once(), request -> { })
                .andExpect(header("Authorization", "Bearer test-token"))
                .andRespond(withSuccess("{\"values\":[[\"product_name\"],[\"Retry\"]]}", MediaType.APPLICATION_JSON));
        GoogleSheetValuesProvider provider = new GoogleSheetValuesProvider(builder.build(), this::credentials);

        var result = provider.read(new SheetSource("sheet_123", "Products", "'Products'!A1:M1001"));

        assertThat(result.values().get(1)).containsExactly("Retry");
        server.verify();
    }

    @Test
    void missingCredentialsFailClosedWithSanitizedAuthError() {
        GoogleSheetValuesProvider provider = new GoogleSheetValuesProvider(RestClient.create(),
                () -> { throw new java.io.IOException("credential file location"); });
        assertThatThrownBy(() -> provider.read(new SheetSource("sheet_123", "Products", "'Products'!A1:M1001")))
                .isInstanceOfSatisfying(SheetProviderException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("GOOGLE_AUTH_UNAVAILABLE");
                    assertThat(exception.getMessage()).doesNotContain("credential file location");
                });
    }

    private GoogleCredentials credentials() {
        return GoogleCredentials.create(new AccessToken("test-token", new Date(System.currentTimeMillis() + 3_600_000)));
    }
}
