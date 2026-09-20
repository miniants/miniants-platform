package cn.miniants.platform.storage;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ObjectStorageBucketTest {

    /** 改造前 bucket() 把桶名塞进 ThreadLocal 且不清，同线程后续调用会误用上一次的桶。 */
    @Test
    void switchingBucketDoesNotAffectTheOriginal() {
        RecordingStorage root = new RecordingStorage("default");

        ObjectStorage photos = root.bucket("photos");
        photos.upload(new byte[]{1}, "a.png");
        root.upload(new byte[]{1}, "b.png");

        assertEquals(List.of("photos/a.png", "default/b.png"), root.writes);
    }

    @Test
    void switchingToTheSameBucketReusesTheInstance() {
        RecordingStorage root = new RecordingStorage("default");

        assertSame(root, root.bucket("default"));
        assertSame(root, root.bucket(null));
        assertSame(root, root.bucket("  "));
    }

    @Test
    void suffixComesFromTheObjectNameNotTheUpload() {
        assertEquals("pdf", new StoredObject("b", "TEMP/x.PDF", null).suffix());
        assertEquals("", new StoredObject("b", "no-extension", null).suffix());
    }

    private static final class RecordingStorage implements ObjectStorage {

        private final List<String> writes;
        private final String bucketName;

        RecordingStorage(String bucketName) {
            this(bucketName, new ArrayList<>());
        }

        private RecordingStorage(String bucketName, List<String> writes) {
            this.bucketName = bucketName;
            this.writes = writes;
        }

        @Override
        public ObjectStorage bucket(String name) {
            if (name == null || name.isBlank() || name.equals(bucketName)) {
                return this;
            }
            return new RecordingStorage(name, writes);
        }

        @Override
        public String bucketName() {
            return bucketName;
        }

        @Override
        public ObjectStat stat(String objectName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean exists(String objectName) {
            return false;
        }

        @Override
        public InputStream download(String objectName) {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public boolean delete(String objectName) {
            return true;
        }

        @Override
        public StoredObject upload(InputStream source, long length, String objectName) {
            writes.add(bucketName + "/" + objectName);
            return new StoredObject(bucketName, objectName, null);
        }
    }
}
