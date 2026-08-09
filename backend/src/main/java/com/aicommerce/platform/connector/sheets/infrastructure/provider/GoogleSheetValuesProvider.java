package com.aicommerce.platform.connector.sheets.infrastructure.provider;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

import com.aicommerce.platform.connector.sheets.application.SheetProviderException;
import com.aicommerce.platform.connector.sheets.application.SheetSource;
import com.aicommerce.platform.connector.sheets.application.SheetValuesProvider;
import com.aicommerce.platform.connector.sheets.application.SheetValuesSnapshot;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
@Profile("production | (!local & !test)")
public class GoogleSheetValuesProvider implements SheetValuesProvider {

    private static final String READ_SCOPE = "https://www.googleapis.com/auth/spreadsheets.readonly";
    private static final int MAX_ATTEMPTS = 2;
    private final RestClient client;
    private final CredentialsSource credentialsSource;

    public GoogleSheetValuesProvider() {
        this(fixedClient(), GoogleSheetValuesProvider::applicationDefaultCredentials);
    }

    GoogleSheetValuesProvider(RestClient client, CredentialsSource credentialsSource) {
        this.client = client;
        this.credentialsSource = credentialsSource;
    }

    private static RestClient fixedClient() {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        return RestClient.builder()
                .baseUrl("https://sheets.googleapis.com")
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public SheetValuesSnapshot read(SheetSource source) {
        GoogleCredentials credentials = credentials();
        try {
            credentials.refreshIfExpired();
            if (credentials.getAccessToken() == null) {
                credentials.refresh();
            }
            if (credentials.getAccessToken() == null || credentials.getAccessToken().getTokenValue() == null) {
                throw new IllegalStateException("Google credentials did not supply an access token");
            }
            return requestWithRetry(source, credentials.getAccessToken().getTokenValue());
        } catch (IOException | IllegalStateException exception) {
            throw new SheetProviderException("GOOGLE_AUTH_UNAVAILABLE",
                    "Google authentication is unavailable", exception);
        }
    }

    private SheetValuesSnapshot requestWithRetry(SheetSource source, String token) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                GoogleValuesResponse response = client.get()
                        .uri(builder -> builder.pathSegment("v4", "spreadsheets", source.spreadsheetId(), "values", source.range())
                                .queryParam("majorDimension", "ROWS")
                                .queryParam("valueRenderOption", "FORMATTED_VALUE")
                                .build())
                        .headers(headers -> headers.setBearerAuth(token))
                        .retrieve()
                        .body(GoogleValuesResponse.class);
                return new SheetValuesSnapshot(response == null || response.values() == null
                        ? List.of()
                        : response.values().stream().map(row -> row.stream().map(String::valueOf).toList()).toList());
            } catch (RestClientResponseException exception) {
                if (attempt < MAX_ATTEMPTS && retryable(exception.getStatusCode())) continue;
                throw providerFailure(exception.getStatusCode(), exception);
            } catch (RestClientException exception) {
                if (attempt < MAX_ATTEMPTS) continue;
                throw new SheetProviderException("GOOGLE_PROVIDER_UNAVAILABLE",
                        "Google Sheets is temporarily unavailable", exception);
            }
        }
        throw new IllegalStateException("retry loop exhausted");
    }

    private GoogleCredentials credentials() {
        try {
            return credentialsSource.load().createScoped(List.of(READ_SCOPE));
        } catch (IOException exception) {
            throw new SheetProviderException("GOOGLE_AUTH_UNAVAILABLE",
                    "Google Application Default Credentials are unavailable", exception);
        }
    }

    private static GoogleCredentials applicationDefaultCredentials() throws IOException {
        return GoogleCredentials.getApplicationDefault();
    }

    private boolean retryable(HttpStatusCode status) {
        return status.value() == 429 || status.is5xxServerError();
    }

    private SheetProviderException providerFailure(HttpStatusCode status, Exception cause) {
        if (status.value() == 401) {
            return new SheetProviderException("GOOGLE_AUTH_UNAVAILABLE", "Google authentication failed", cause);
        }
        if (status.value() == 403) {
            return new SheetProviderException("GOOGLE_PERMISSION_DENIED", "Google Sheets access was denied", cause);
        }
        if (status.value() == 429) {
            return new SheetProviderException("GOOGLE_RATE_LIMITED", "Google Sheets rate limit was reached", cause);
        }
        return new SheetProviderException("GOOGLE_PROVIDER_UNAVAILABLE",
                "Google Sheets is temporarily unavailable", cause);
    }

    private record GoogleValuesResponse(List<List<Object>> values) {
    }

    @FunctionalInterface
    interface CredentialsSource {
        GoogleCredentials load() throws IOException;
    }
}
