package com.muscledia.workout_service.external.hevy.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class HevyRoutineFolderResponse {
    private Integer page;

    @JsonProperty("page_count")
    private Integer pageCount;

    @JsonProperty("routine_folders")
    private List<HevyRoutineFolder> folders;

    @Data
    public static class HevyRoutineFolder {
        private Long id;
        private String title;

        @JsonProperty("folder_index")
        private Integer folderIndex;

        @JsonProperty("updated_at")
        private LocalDateTime updatedAt;

        @JsonProperty("created_at")
        private LocalDateTime createdAt;
    }
}