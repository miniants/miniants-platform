package cn.miniants.platform.data.support;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public final class GoldenSnapshot {

    private GoldenSnapshot() {
    }

    public static void assertResource(Class<?> testClass, String resource, List<String> actualLines) {
        String actual = String.join("\n", actualLines).replace("\r\n", "\n");
        if (!actual.isEmpty()) {
            actual = actual + "\n";
        }
        boolean update = Boolean.parseBoolean(System.getProperty("platform.update.golden", "false"));
        URL url = testClass.getResource(resource);
        if (url == null || update) {
            Path dest = Path.of("src/test/resources")
                    .resolve(resource.startsWith("/") ? resource.substring(1) : resource);
            try {
                Files.createDirectories(dest.getParent());
                Files.writeString(dest, actual, StandardCharsets.UTF_8);
            } catch (IOException ex) {
                fail("无法写入黄金快照 " + dest + ": " + ex.getMessage());
            }
            if (url == null) {
                fail("已生成黄金快照 " + dest + "，请提交后重跑");
            }
        }
        String expected;
        try {
            expected = new String(testClass.getResourceAsStream(resource).readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n");
        } catch (IOException ex) {
            fail("无法读取黄金快照 " + resource);
            return;
        }
        assertFalse(expected.isBlank(), "黄金快照为空: " + resource);
        assertEquals(expected, actual, "黄金快照不一致，确认后用 -Dplatform.update.golden=true 更新 " + resource);
    }
}
