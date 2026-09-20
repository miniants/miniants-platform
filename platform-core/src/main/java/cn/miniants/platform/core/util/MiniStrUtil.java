package cn.miniants.platform.core.util;

import tools.jackson.databind.JsonNode;

import java.security.SecureRandom;

public final class MiniStrUtil {

    private static final String ALPHANUM =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private MiniStrUtil() {
    }

    public static String createRandomStr1(int length) {
        StringBuilder stringBuffer = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            stringBuffer.append(ALPHANUM.charAt(RANDOM.nextInt(ALPHANUM.length())));
        }
        return stringBuffer.toString();
    }

    public static String getOrEmpty(Object obj) {
        return getOrDefault(obj, "");
    }

    public static String getOrDefault(Object obj, String defaultStr) {
        if (null == obj) {
            return defaultStr;
        }
        if (obj instanceof String s) {
            return s;
        }
        if (obj instanceof JsonNode node) {
            return node.asString();
        }
        return obj.toString();
    }
}
