package com.github.garamflow.streamsettlement.util;

import java.util.Arrays;

public class CacheKeyUtil {
    public static String generateKey(String prefix, Object... parts) {
        return prefix + ":" + String.join(":", Arrays.stream(parts).map(Object::toString).toArray(String[]::new));
    }
}
