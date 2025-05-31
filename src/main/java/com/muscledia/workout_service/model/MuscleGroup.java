package com.muscledia.workout_service.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

@Data
@Document(collection = "muscle_groups")
public class MuscleGroup {
    @Id
    private String id;

    @Indexed(unique = true)
    private String name;

    @Field("latin_name")
    private String latinName;

    private String description;

    @CreatedDate
    @Field("created_at")
    private LocalDateTime createdAt;
}