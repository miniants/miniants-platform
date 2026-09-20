package cn.miniants.platform.storage.web;

import cn.miniants.platform.storage.ObjectStat;
import cn.miniants.platform.storage.ObjectStorage;
import cn.miniants.platform.storage.StoredObject;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.util.StreamUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;

/**
 * 对象存储的 Spring Web 适配器。
 *
 * <p>核心 {@link ObjectStorage} 只处理流和平台类型；Web 应用需要上传或写响应时显式经过这里。
 */
public final class ObjectStorageWebAdapter {

    private ObjectStorageWebAdapter() {
    }

    public static StoredObject upload(ObjectStorage storage, MultipartFile file, String objectName) {
        try (InputStream source = file.getInputStream()) {
            return storage.upload(source, file.getSize(), objectName);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String contentTypeOf(String filename) {
        return MediaTypeFactory.getMediaType(filename).orElse(MediaType.APPLICATION_OCTET_STREAM).toString();
    }

    public static void download(ObjectStorage storage, HttpServletResponse response, String objectName) {
        download(storage, response, objectName, null);
    }

    public static void download(
            ObjectStorage storage,
            HttpServletResponse response,
            String objectName,
            String downloadFileName) {
        try (InputStream source = storage.download(objectName)) {
            if (source == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文件不存在");
            }
            ObjectStat stat = storage.stat(objectName);
            response.setHeader("Content-Type", stat.contentType());
            response.setHeader("Content-Length", String.valueOf(stat.size()));
            response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(
                    downloadFileName == null || downloadFileName.isBlank()
                            ? Paths.get(objectName).getFileName().toString()
                            : downloadFileName,
                    StandardCharsets.UTF_8));
            StreamUtils.copy(source, response.getOutputStream());
            response.flushBuffer();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            // 不能把 IOException 串成 cause：框架会当成 CLIENT-ABORT，void 接口落成 204
            if (response.isCommitted()) {
                throw new IllegalStateException("读取文件失败");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "读取文件失败");
        }
    }
}
