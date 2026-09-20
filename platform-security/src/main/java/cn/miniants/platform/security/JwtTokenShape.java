package cn.miniants.platform.security;

/**
 * 只描述票的形态，不回显票面。
 */
public final class JwtTokenShape {

    private JwtTokenShape() {
    }

    public static boolean looksLikeJwt(String token) {
        if (token == null) {
            return false;
        }
        int first = token.indexOf('.');
        if (first <= 0) {
            return false;
        }
        int second = token.indexOf('.', first + 1);
        return second > first + 1 && second < token.length() - 1 && token.indexOf('.', second + 1) < 0;
    }

    public static String describe(String token) {
        if (token == null) {
            return "null";
        }
        String trimmed = token.trim();
        if (trimmed.isEmpty()) {
            return "empty";
        }
        if ("undefined".equals(trimmed) || "null".equalsIgnoreCase(trimmed)) {
            return trimmed;
        }
        if ("[object Object]".equals(trimmed)) {
            return "object-string";
        }
        int dots = 0;
        for (int i = 0; i < trimmed.length(); i++) {
            if (trimmed.charAt(i) == '.') {
                dots++;
            }
        }
        if (looksLikeJwt(trimmed)) {
            return "jwt-like dots=" + dots + " len=" + trimmed.length();
        }
        if (dots == 0 && trimmed.indexOf('-') >= 0 && trimmed.length() >= 32) {
            return "opaque-uuid len=" + trimmed.length();
        }
        return "other dots=" + dots + " len=" + trimmed.length();
    }
}
