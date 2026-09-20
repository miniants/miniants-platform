package cn.miniants.platform.storage;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;

class ObjectStorageBoundaryTest {

    @Test
    void coreContractDoesNotExposeWebTypes() {
        Stream<Class<?>> signatureTypes = Arrays.stream(ObjectStorage.class.getMethods())
                .flatMap(ObjectStorageBoundaryTest::signatureTypes);

        assertFalse(signatureTypes.map(Class::getName).anyMatch(name ->
                name.startsWith("jakarta.servlet.")
                        || name.startsWith("org.springframework.")));
    }

    private static Stream<Class<?>> signatureTypes(Method method) {
        return Stream.concat(Stream.of(method.getReturnType()), Arrays.stream(method.getParameterTypes()));
    }
}
