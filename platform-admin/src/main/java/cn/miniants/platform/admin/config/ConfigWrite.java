package cn.miniants.platform.admin.config;

/**
 * {@link ConfigSource#set} 的标题与本次是否写入共享缓存。
 * 无论 {@link #lookup()} 是否缓存，写完都会广播失效。
 */
public final class ConfigWrite {

    private final String title;
    private final ConfigLookup lookup;

    private ConfigWrite(String title, ConfigLookup lookup) {
        this.title = title;
        this.lookup = lookup == null ? ConfigLookup.dbOnly() : lookup;
    }

    public static ConfigWrite of(String title, ConfigLookup lookup) {
        return new ConfigWrite(title, lookup);
    }

    public String title() {
        return title;
    }

    public ConfigLookup lookup() {
        return lookup;
    }
}
