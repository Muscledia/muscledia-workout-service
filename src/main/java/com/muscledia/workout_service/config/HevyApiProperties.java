package com.muscledia.workout_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "api.hevy")
public class HevyApiProperties {
    private String baseUrl = "https://api.hevyapp.com";
    private String version = "v1";
    private String apiKey = "879a3ffd-2482-4bd4-92af-b54d3b8c65f5";
    private Integer routinesPageSize = 10;
    private Integer routineFoldersPageSize = 10;
    private Integer maxRoutinesPages = 6;
    private Integer maxRoutineFoldersPages = 2;
}