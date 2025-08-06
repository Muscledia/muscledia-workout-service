package com.muscledia.workout_service.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
        * Exception thrown when business rules are violated
 */
@Getter
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class BusinessRuleViolationException extends WorkoutServiceException {
    private final String ruleName;

    public BusinessRuleViolationException(String message) {
        super(message);
        this.ruleName = null;
    }

    public BusinessRuleViolationException(String message, String ruleName) {
        super(message);
        this.ruleName = ruleName;
    }

}
