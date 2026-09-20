package cn.miniants.platform.storage;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * 对象存储。接口里只出现 JDK 与平台存储类型，换后端不影响调用方。
 */
public interface ObjectStorage {

    /**
     * 换一个桶。返回的是新视图，当前实例不受影响——共享可变的「当前桶」会在同一线程里
     * 串到后面无关的调用上。
     */
    ObjectStorage bucket(String bucketName);

    String bucketName();

    /**
     * 取底层 SDK 客户端，用于本接口没覆盖的批量操作（列举、服务端复制等）。
     *
     * <p>用了就绑死在具体后端上，只在确实需要时用；日常读写走上面的方法。
     */
    default <T> T unwrap(Class<T> type) {
        if (type.isInstance(this)) {
            return type.cast(this);
        }
        throw new IllegalArgumentException("当前存储实现不支持 " + type.getName());
    }

    /** 对象不存在时抛异常，判存在用 {@link #exists(String)}。 */
    ObjectStat stat(String objectName);

    boolean exists(String objectName);

    /** 对象不存在返回 null。 */
    InputStream download(String objectName);

    boolean delete(String objectName);

    /**
     * @param length 字节数。网络流不能用 {@code available()} 顶替
     */
    StoredObject upload(InputStream source, long length, String objectName);

    default StoredObject upload(File file, String objectName) {
        try (InputStream source = new FileInputStream(file)) {
            return upload(source, file.length(), objectName);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    default StoredObject upload(byte[] content, String objectName) {
        return upload(new ByteArrayInputStream(content), content.length, objectName);
    }
}
