package com.muscledia.workout_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "api.exercise")
public class ApiProperties {
    private String url;
    private String key;
}