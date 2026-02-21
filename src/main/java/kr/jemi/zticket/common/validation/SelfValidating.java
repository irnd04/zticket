package kr.jemi.zticket.common.validation;

public interface SelfValidating {

    default void validateSelf() {
        ValidationUtils.validate(this);
    }
}
