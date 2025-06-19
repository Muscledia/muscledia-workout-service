package com.muscledia.workout_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class ExternalApiException extends RuntimeException {
    private final String apiName;
    private final String endpoint;

    public ExternalApiException(String apiName, String endpoint, String message) {
        super(String.format("External API error for %s at %s: %s", apiName, endpoint, message));
        this.apiName = apiName;
        this.endpoint = endpoint;
    }

    public ExternalApiException(String apiName, String endpoint, String message, Throwable cause) {
        super(String.format("External API error for %s at %s: %s", apiName, endpoint, message), cause);
        this.apiName = apiName;
        this.endpoint = endpoint;
    }

    public String getApiName() {
        return apiName;
    }

    public String getEndpoint() {
        return endpoint;
    }
}