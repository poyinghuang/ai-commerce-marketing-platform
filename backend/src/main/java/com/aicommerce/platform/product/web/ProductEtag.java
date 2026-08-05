package com.aicommerce.platform.product.web;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ProductEtag {

    private static final Pattern ETAG_PATTERN = Pattern.compile("W/\\\"(0|[1-9][0-9]*)\\\"");

    private ProductEtag() {
    }

    public static String fromVersion(long version) {
        return "W/\"" + version + "\"";
    }

    public static long requireVersion(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new PreconditionRequiredException();
        }
        Matcher matcher = ETAG_PATTERN.matcher(ifMatch);
        if (!matcher.matches()) {
            throw new InvalidIfMatchException();
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException exception) {
            throw new InvalidIfMatchException();
        }
    }
}
