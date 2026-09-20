package cn.miniants.platform.data.audit;

public record Auditor(Long id, String name) {

    public static Auditor system() {
        return new Auditor(-1L, "system");
    }
}
