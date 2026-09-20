package cn.miniants.platform.security.crypto;

/**
 * 证件号 → 不可逆查找摘要。明文只在调用栈短暂存在，不得落库或打日志。
 */
public interface IdCredentialHasher {

    String digest(String idDocType, String rawIdNumber);
}
