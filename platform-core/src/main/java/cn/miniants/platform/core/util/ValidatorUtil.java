package cn.miniants.platform.core.util;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Bean Validation 包装；不依赖 Hutool。
 */
public final class ValidatorUtil {

    private static final Validator VALIDATOR;

    static {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            VALIDATOR = factory.getValidator();
        }
    }

    private ValidatorUtil() {
    }

    public static <T> Set<ConstraintViolation<T>> validate(T bean, Class<?>... groups) {
        return VALIDATOR.validate(bean, groups);
    }

    public static <T> BeanValidationResult warpValidate(T object, Class<?>... groups) {
        return toResult(validate(object, groups));
    }

    private static <T> BeanValidationResult toResult(Set<ConstraintViolation<T>> constraintViolations) {
        BeanValidationResult result = new BeanValidationResult(constraintViolations.isEmpty());
        for (ConstraintViolation<T> constraintViolation : constraintViolations) {
            result.addErrorMessage(new BeanValidationResult.ErrorMessage(
                    constraintViolation.getPropertyPath().toString(),
                    constraintViolation.getMessage(),
                    constraintViolation.getInvalidValue()));
        }
        return result;
    }

    public static final class BeanValidationResult {
        private final boolean success;
        private final List<ErrorMessage> errorMessages = new ArrayList<>();

        public BeanValidationResult(boolean success) {
            this.success = success;
        }

        public boolean isSuccess() {
            return success;
        }

        public List<ErrorMessage> getErrorMessages() {
            return Collections.unmodifiableList(errorMessages);
        }

        void addErrorMessage(ErrorMessage errorMessage) {
            errorMessages.add(errorMessage);
        }

        public static final class ErrorMessage {
            private final String propertyName;
            private final String message;
            private final Object value;

            public ErrorMessage(String propertyName, String message, Object value) {
                this.propertyName = propertyName;
                this.message = message;
                this.value = value;
            }

            public String getPropertyName() {
                return propertyName;
            }

            public String getMessage() {
                return message;
            }

            public Object getValue() {
                return value;
            }
        }
    }
}
