package cn.miniants.platform.security;

public enum Actor {
    USER,
    CLIENT,
    ANON;

    public String wire() {
        return switch (this) {
            case USER -> "user";
            case CLIENT -> "client";
            case ANON -> "anon";
        };
    }

    public static Actor fromWire(String raw) {
        if ("user".equals(raw)) {
            return USER;
        }
        if ("client".equals(raw)) {
            return CLIENT;
        }
        return ANON;
    }
}
