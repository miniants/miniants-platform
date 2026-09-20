package cn.miniants.platform.storage;

/**
 * 一次写入的结果。
 *
 * @param versionId 后端没开版本控制就是 null
 */
public record StoredObject(String bucketName, String objectName, String versionId) {

    public String suffix() {
        int dot = objectName.lastIndexOf('.');
        return dot < 0 ? "" : objectName.substring(dot + 1).toLowerCase();
    }
}
