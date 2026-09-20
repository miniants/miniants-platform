package cn.miniants.platform.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperLog {

    /** 业务标题。 */
    String value();

    /** 细分类，见 {@link OperLogEventTypes}。 */
    String eventType() default OperLogEventTypes.DATA_MUTATE;

    /** 是否记录请求摘要（方法 + URI + query，不含 body）。 */
    boolean captureRequest() default false;

    /** 是否尝试记录脱敏后的响应摘要（默认否；文件下载勿开）。 */
    boolean captureResponse() default false;
}
