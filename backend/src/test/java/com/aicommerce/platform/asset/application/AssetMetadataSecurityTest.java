package com.aicommerce.platform.asset.application;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class AssetMetadataSecurityTest {
    private final AssetMetadataSecurity security=new AssetMetadataSecurity(new JsonMapper());
    @Test void recursivelyRejectsSensitiveKeysIncludingObjectsInsideArrays() {
        for(String key:List.of("accessToken","client_secret","PASSWORD","authorizationHeader","sessionCookie","credentialId"))
            assertThatThrownBy(()->security.validateAndCanonicalize(Map.of("outer",List.of(Map.of(key,"redacted")))))
                    .isInstanceOf(AssetValidationException.class).hasMessageNotContaining("redacted").hasMessageNotContaining(key);
    }
    @Test void enforcesSerializedUtf8LimitAndCreatesStableNonReversibleFingerprint() {
        Map<String,Object> exact=security.validateAndCanonicalize(Map.of("data","x".repeat(16*1024-11)));
        assertThat(exact).containsKey("data");
        assertThatThrownBy(()->security.validateAndCanonicalize(Map.of("data","x".repeat(16*1024-10))))
                .isInstanceOf(AssetValidationException.class);
        String first=security.fingerprint(Map.of("b",2,"a",1));
        assertThat(first).isEqualTo(security.fingerprint(Map.of("a",1,"b",2))).matches("\\[SHA256:[0-9a-f]{64}]").doesNotContain("1");
    }
}
