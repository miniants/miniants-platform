package cn.miniants.platform.core.advice;

import cn.miniants.platform.core.api.ApiResult;
import cn.miniants.platform.core.api.RawBody;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.lang.reflect.Method;

@RestControllerAdvice
public class PlatformResultAdvice implements ResponseBodyAdvice<Object> {

    private final ObjectMapper objectMapper;

    public PlatformResultAdvice(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        Method method = returnType.getMethod();
        if (method == null || method.getReturnType() == Void.TYPE) {
            return false;
        }
        return !hasRawBody(returnType);
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (alreadyWrapped(body) || hasRawBody(returnType)) {
            return body;
        }
        ApiResult<Object> wrapped = ApiResult.ok(body);
        if (String.class.equals(returnType.getParameterType())) {
            try {
                return objectMapper.writeValueAsString(wrapped);
            } catch (JacksonException ex) {
                throw new IllegalStateException("无法序列化返回体", ex);
            }
        }
        return wrapped;
    }

    public static boolean alreadyWrapped(Object body) {
        if (body == null) {
            return false;
        }
        if (body instanceof ApiResult<?>) {
            return true;
        }
        Class<?> type = body.getClass();
        while (type != null && type != Object.class) {
            if ("ApiResult".equals(type.getSimpleName())) {
                return true;
            }
            type = type.getSuperclass();
        }
        return false;
    }

    private static boolean hasRawBody(MethodParameter returnType) {
        return returnType.hasMethodAnnotation(RawBody.class)
                || returnType.getContainingClass().isAnnotationPresent(RawBody.class);
    }
}
