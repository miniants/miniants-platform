package cn.miniants.platform.security.sas.keystore;

import java.util.List;

/**
 * JWT 签发/验签密钥集合。本层只读；生成、激活、摘旧由 platform-admin 实现。
 *
 * <p>最小接口面：{@link #list()} 给出当前应公布/验签的全部条目，
 * {@link #loadActive()} 标出哪一把带私钥用于签发。
 */
public interface JwtKeyStore {

    /**
     * 当前密钥集合（含 active 与只验签）。不含已摘旧。
     * 顺序不保证；{@link RotatableJwkSource} 会把 active 排到快照首位。
     */
    List<JwtKeyEntry> list();

    /**
     * 当前签发钥（须含私钥）。没有则 {@code null}，由 {@link RotatableJwkSource} 走 classpath / 临时钥兜底。
     */
    JwtKeyEntry loadActive();
}
