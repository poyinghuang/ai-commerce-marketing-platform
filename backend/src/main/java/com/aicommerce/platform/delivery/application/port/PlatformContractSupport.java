package com.aicommerce.platform.delivery.application.port;
import java.math.BigDecimal; import java.util.Objects; import java.util.Optional; import java.util.regex.Pattern;
final class PlatformContractSupport {
 static final Pattern SAFE=Pattern.compile("^[A-Za-z0-9._:-]{1,128}$"), HASH=Pattern.compile("^[0-9a-f]{64}$");
 static <T>T req(T v){return Objects.requireNonNull(v,"PLATFORM_CONTRACT_INVALID");}
 static <T>Optional<T> opt(Optional<T> v){return Objects.requireNonNull(v,"PLATFORM_CONTRACT_INVALID");}
 static String safe(String v){if(v==null||!SAFE.matcher(v).matches())throw invalid();return v;}
 static String hash(String v){if(v==null||!HASH.matcher(v).matches())throw invalid();return v;}
 static BigDecimal money(BigDecimal v){req(v);if(v.signum()<=0||v.scale()>6)throw invalid();return v.stripTrailingZeros();}
 static IllegalArgumentException invalid(){return new IllegalArgumentException("PLATFORM_CONTRACT_INVALID");}
 private PlatformContractSupport(){}
}
