package kr.jemi.zticket.common.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import java.util.Set;
import java.util.stream.Collectors;

public final class ValidationUtils {

    private static final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private ValidationUtils() {}

    public static void validate(Object target) {
        Set<ConstraintViolation<Object>> violations = validator.validate(target);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException(
                    target.getClass().getSimpleName() + " 검증 실패: " + message);
        }
    }
}
