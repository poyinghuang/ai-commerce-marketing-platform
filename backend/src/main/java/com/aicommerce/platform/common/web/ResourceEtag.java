package com.aicommerce.platform.common.web;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ResourceEtag {

    private static final Pattern WEAK_ETAG = Pattern.compile("W/\\\"(0|[1-9][0-9]*)\\\"");

    private ResourceEtag() {
    }

    public static String format(long version) {
        if (version < 0) {
            throw new IllegalArgumentException("version must be non-negative");
        }
        return "W/\"" + version + "\"";
    }

    public static long parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("ETag is required");
        }
        Matcher matcher = WEAK_ETAG.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("ETag must use W/\"<version>\" format");
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("ETag version is outside the supported range", exception);
        }
    }
}
