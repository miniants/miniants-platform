package cn.miniants.platform.core.json;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/**
 * 只把超出 JS {@code Number.MAX_SAFE_INTEGER}（2^53-1）的 Long 写成字符串，避免雪花 ID 丢精度。
 *
 * <p>安全范围内仍写 JSON number。无差别把 Long 转字符串会让 {@code ApiResult.code} 变成
 * {@code "200"}，前端 {@code code === 200} 全线失败。
 */
public final class JsSafeLongSerializer extends ValueSerializer<Long> {

    /** 与 JavaScript Number.MAX_SAFE_INTEGER 一致。 */
    public static final long JS_MAX_SAFE = 9007199254740991L;

    public static final JsSafeLongSerializer INSTANCE = new JsSafeLongSerializer();

    private JsSafeLongSerializer() {
    }

    @Override
    public void serialize(Long value, JsonGenerator gen, SerializationContext context) {
        if (value == null) {
            gen.writeNull();
            return;
        }
        long raw = value;
        if (raw > JS_MAX_SAFE || raw < -JS_MAX_SAFE) {
            gen.writeString(Long.toString(raw));
        } else {
            gen.writeNumber(raw);
        }
    }
}
