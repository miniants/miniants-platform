package cn.miniants.platform.storage;

import java.time.Instant;

/**
 * 对象元信息。刻意不用后端 SDK 的响应类型，否则换存储就要改所有调用方。
 *
 * @param lastModified 后端没给就是 null
 */
public record ObjectStat(String bucketName, String objectName, long size, String contentType,
                         Instant lastModified) {
}
