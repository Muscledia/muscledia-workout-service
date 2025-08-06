package com.muscledia.workout_service.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@Getter
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class ExternalApiException extends RuntimeException {
    private final String apiName;
    private final String endpoint;
    private final Integer statusCode;

    public ExternalApiException(String message, String apiName, String endpoint) {
        super(message);
        this.apiName = apiName;
        this.endpoint = endpoint;
        this.statusCode = null;
    }

    public ExternalApiException(String message, String apiName, String endpoint, Integer statusCode) {
        super(message);
        this.apiName = apiName;
        this.endpoint = endpoint;
        this.statusCode = statusCode;
    }

}