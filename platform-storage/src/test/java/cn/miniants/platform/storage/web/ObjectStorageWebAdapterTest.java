package cn.miniants.platform.storage.web;

import cn.miniants.platform.storage.ObjectStat;
import cn.miniants.platform.storage.ObjectStorage;
import cn.miniants.platform.storage.StoredObject;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ObjectStorageWebAdapterTest {

    @Test
    void writesStoredObjectToServletResponse() {
        RecordingStorage storage = new RecordingStorage("内容".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        ObjectStorageWebAdapter.download(storage, response, "目录/原文件.txt", "下载 文件.txt");

        assertEquals("text/plain", response.getHeader("Content-Type"));
        assertEquals(String.valueOf(storage.content.length), response.getHeader("Content-Length"));
        assertEquals("attachment;filename=%E4%B8%8B%E8%BD%BD+%E6%96%87%E4%BB%B6.txt",
                response.getHeader("Content-Disposition"));
        assertArrayEquals(storage.content, response.getContentAsByteArray());
    }

    @Test
    void mapsMissingObjectToNotFound() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> ObjectStorageWebAdapter.download(
                        new RecordingStorage(null), new MockHttpServletResponse(), "missing.txt"));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        assertEquals("文件不存在", error.getReason());
    }

    @Test
    void uploadsMultipartThroughCoreStreamContract() {
        RecordingStorage storage = new RecordingStorage(null);
        MockMultipartFile file = new MockMultipartFile(
                "file", "a.txt", "text/plain", "abc".getBytes(StandardCharsets.UTF_8));

        StoredObject stored = ObjectStorageWebAdapter.upload(storage, file, "a.txt");

        assertEquals("a.txt", stored.objectName());
        assertArrayEquals("abc".getBytes(StandardCharsets.UTF_8), storage.content);
    }

    private static final class RecordingStorage implements ObjectStorage {

        private byte[] content;

        private RecordingStorage(byte[] content) {
            this.content = content;
        }

        @Override
        public ObjectStorage bucket(String bucketName) {
            return this;
        }

        @Override
        public String bucketName() {
            return "test";
        }

        @Override
        public ObjectStat stat(String objectName) {
            return new ObjectStat(bucketName(), objectName, content.length, "text/plain", null);
        }

        @Override
        public boolean exists(String objectName) {
            return content != null;
        }

        @Override
        public InputStream download(String objectName) {
            return content == null ? null : new ByteArrayInputStream(content);
        }

        @Override
        public boolean delete(String objectName) {
            return false;
        }

        @Override
        public StoredObject upload(InputStream source, long length, String objectName) {
            try {
                content = source.readAllBytes();
                return new StoredObject(bucketName(), objectName, null);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
