package com.aicommerce.platform.common.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.stream.Stream;

import com.aicommerce.platform.common.application.FieldPatch;
import com.aicommerce.platform.common.persistence.ArchivableEntity;
import com.aicommerce.platform.common.web.ResourceEtag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class SharedDomainPrimitivesTest {

    @Test
    void lifecycleTransitionsAreControlledAndNoOpsKeepState() {
        TestResource resource = new TestResource();
        Instant archivedAt = Instant.parse("2026-08-07T00:00:00Z");

        assertThat(resource.getLifecycleStatus()).isEqualTo(LifecycleStatus.ACTIVE);
        assertThat(resource.archive(archivedAt)).isTrue();
        assertThat(resource.getArchivedAt()).isEqualTo(archivedAt);
        assertThat(resource.archive(archivedAt.plusSeconds(1))).isFalse();
        assertThat(resource.getArchivedAt()).isEqualTo(archivedAt);
        assertThat(resource.restore()).isTrue();
        assertThat(resource.getArchivedAt()).isNull();
        assertThat(resource.restore()).isFalse();
    }

    @Test
    void fieldPatchDistinguishesAbsentExplicitNullAndValue() {
        assertThat(FieldPatch.<String>absent().present()).isFalse();
        assertThat(FieldPatch.<String>absent().resolve("current")).isEqualTo("current");
        assertThat(FieldPatch.<String>present(null).present()).isTrue();
        assertThat(FieldPatch.<String>present(null).resolve("current")).isNull();
        assertThat(FieldPatch.present("new").resolve("current")).isEqualTo("new");
    }

    @Test
    void resourceEtagFormatsAndParsesStrictWeakVersion() {
        assertThat(ResourceEtag.format(0)).isEqualTo("W/\"0\"");
        assertThat(ResourceEtag.format(Long.MAX_VALUE)).isEqualTo("W/\"9223372036854775807\"");
        assertThat(ResourceEtag.parse("W/\"0\"")).isZero();
        assertThat(ResourceEtag.parse("W/\"42\"")).isEqualTo(42L);
    }

    @ParameterizedTest
    @MethodSource("invalidEtags")
    void resourceEtagRejectsMalformedValues(String value) {
        assertThatThrownBy(() -> ResourceEtag.parse(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resourceEtagRejectsNegativeVersionForFormatting() {
        assertThatThrownBy(() -> ResourceEtag.format(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    private static Stream<String> invalidEtags() {
        return Stream.of("\"1\"", "*", "W/\"-1\"", "W/\"01\"", "W/\" 1\"", "W/\"1\", W/\"2\"",
                "w/\"1\"", "W/\"9223372036854775808\"", "", " W/\"1\"");
    }

    private static final class TestResource extends ArchivableEntity {
    }
}
