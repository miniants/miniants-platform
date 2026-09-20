package cn.miniants.platform.integration.cache;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisKeyCommands;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class RedisKeyScannerTest {

    @Test
    void scansByPatternWithoutKeysCommand() {
        RedisTemplate<String, Object> template = mock(RedisTemplate.class);
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisConnection connection = mock(RedisConnection.class);
        RedisKeyCommands keyCommands = mock(RedisKeyCommands.class);
        @SuppressWarnings("unchecked")
        Cursor<byte[]> cursor = mock(Cursor.class);
        when(template.getConnectionFactory()).thenReturn(factory);
        when(factory.getConnection()).thenReturn(connection);
        when(connection.keyCommands()).thenReturn(keyCommands);
        when(keyCommands.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(true, true, false);
        when(cursor.next()).thenReturn(
                bytes("jwy:rbac:role-perm:1"),
                bytes("jwy:rbac:role-perm:2"));

        assertThat(RedisKeyScanner.scan(template, "jwy:rbac:role-perm:*"))
                .containsExactly("jwy:rbac:role-perm:1", "jwy:rbac:role-perm:2");
        ArgumentCaptor<ScanOptions> options = ArgumentCaptor.forClass(ScanOptions.class);
        verify(keyCommands).scan(options.capture());
        assertThat(options.getValue().getPattern()).isEqualTo("jwy:rbac:role-perm:*");
        assertThat(options.getValue().getCount()).isEqualTo(256L);
        verify(connection).close();
    }

    @Test
    void scanFailureReturnsEmptyAndClosesConnection() {
        RedisTemplate<String, Object> template = mock(RedisTemplate.class);
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisConnection connection = mock(RedisConnection.class);
        RedisKeyCommands keyCommands = mock(RedisKeyCommands.class);
        when(template.getConnectionFactory()).thenReturn(factory);
        when(factory.getConnection()).thenReturn(connection);
        when(connection.keyCommands()).thenReturn(keyCommands);
        when(keyCommands.scan(any(ScanOptions.class)))
                .thenThrow(new IllegalStateException("unavailable"));

        assertThat(RedisKeyScanner.scan(template, "jwy:*")).isEmpty();
        verify(connection).close();
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
