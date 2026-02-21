package kr.jemi.zticket.common.validation;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ValidationUtilsTest {

    @Nested
    @DisplayName("validate() - 직접 호출")
    class Validate {

        @Test
        @DisplayName("유효한 객체는 예외 없이 통과한다")
        void shouldPassForValidObject() {
            var target = new SampleObject("hello", 1);

            assertThatCode(() -> ValidationUtils.validate(target))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("@NotNull 필드가 null이면 IllegalArgumentException이 발생한다")
        void shouldThrowWhenNotNullFieldIsNull() {
            var target = new SampleObject(null, 1);

            assertThatThrownBy(() -> ValidationUtils.validate(target))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("name");
        }

        @Test
        @DisplayName("@Min 제약을 위반하면 IllegalArgumentException이 발생한다")
        void shouldThrowWhenMinViolated() {
            var target = new SampleObject("hello", 0);

            assertThatThrownBy(() -> ValidationUtils.validate(target))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("value");
        }

        @Test
        @DisplayName("여러 제약 위반 시 모든 필드가 메시지에 포함된다")
        void shouldIncludeAllViolationsInMessage() {
            var target = new SampleObject(null, 0);

            assertThatThrownBy(() -> ValidationUtils.validate(target))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("name")
                    .hasMessageContaining("value");
        }

        @Test
        @DisplayName("예외 메시지에 클래스 이름이 포함된다")
        void shouldIncludeClassNameInMessage() {
            var target = new SampleObject(null, 1);

            assertThatThrownBy(() -> ValidationUtils.validate(target))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("SampleObject");
        }
    }

    @Nested
    @DisplayName("SelfValidating 인터페이스")
    class SelfValidatingInterface {

        @Test
        @DisplayName("유효한 객체는 validateSelf() 통과한다")
        void shouldPassForValidObject() {
            var target = new SelfValidatingObject("hello", 1);

            assertThatCode(target::validateSelf)
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("제약 위반 시 validateSelf()가 IllegalArgumentException을 던진다")
        void shouldThrowOnViolation() {
            var target = new SelfValidatingObject(null, 1);

            assertThatThrownBy(target::validateSelf)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("name");
        }
    }

    static class SampleObject {
        @NotNull
        private final String name;
        @Min(1)
        private final int value;

        SampleObject(String name, int value) {
            this.name = name;
            this.value = value;
        }
    }

    static class SelfValidatingObject implements SelfValidating {
        @NotNull
        private final String name;
        @Min(1)
        private final int value;

        SelfValidatingObject(String name, int value) {
            this.name = name;
            this.value = value;
        }
    }
}
