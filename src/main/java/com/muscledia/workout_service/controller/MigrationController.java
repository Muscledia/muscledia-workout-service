package com.muscledia.workout_service.controller;


import com.muscledia.workout_service.service.migration.SetTypeMigrationService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/migration")
@RequiredArgsConstructor
@Slf4j
public class MigrationController {

    private final SetTypeMigrationService migrationService;

    @PostMapping("/set-types")
    @Operation(summary = "Migrate set types", description = "Sets all null setTypes to NORMAL")
    public Mono<ResponseEntity<Map<String, Object>>> migrateSetTypes() {
        log.info("🔧 ADMIN: Triggering SetType migration");

        return migrationService.migrateSetTypes()
                .map(count -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", true);
                    result.put("documentsUpdated", count);
                    result.put("timestamp", Instant.now());
                    result.put("message", "Migration completed successfully");
                    return ResponseEntity.ok(result);
                })
                .onErrorResume(error -> {
                    log.error("Migration failed", error);
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", false);
                    result.put("error", error.getMessage());
                    result.put("timestamp", Instant.now());
                    return Mono.just(ResponseEntity.status(500).body(result));
                });
    }

    @PostMapping("/set-types/from-booleans")
    @Operation(summary = "Migrate from boolean fields",
            description = "Migrate setType based on old warmUp/failure/dropSet boolean fields")
    public Mono<ResponseEntity<Map<String, Object>>> migrateFromBooleans() {
        log.info("🔧 ADMIN: Migrating from boolean fields");

        return migrationService.migrateFromBooleanFields()
                .then(Mono.fromCallable(() -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", true);
                    result.put("timestamp", Instant.now());
                    result.put("message", "Boolean field migration completed");
                    return ResponseEntity.ok(result);
                }))
                .onErrorResume(error -> {
                    log.error("Boolean migration failed", error);
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", false);
                    result.put("error", error.getMessage());
                    result.put("timestamp", Instant.now());
                    return Mono.just(ResponseEntity.status(500).body(result));
                });
    }

    @DeleteMapping("/set-types/cleanup")
    @Operation(summary = "Remove old boolean fields",
            description = "IRREVERSIBLE - Removes warmUp, failure, dropSet fields from database")
    public Mono<ResponseEntity<Map<String, Object>>> cleanupOldFields() {
        log.warn("⚠️ ADMIN: Removing old boolean fields - THIS IS IRREVERSIBLE");

        return migrationService.removeOldBooleanFields()
                .map(count -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", true);
                    result.put("documentsUpdated", count);
                    result.put("timestamp", Instant.now());
                    result.put("message", "Old fields removed successfully");
                    result.put("warning", "This operation is irreversible");
                    return ResponseEntity.ok(result);
                })
                .onErrorResume(error -> {
                    log.error("Cleanup failed", error);
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", false);
                    result.put("error", error.getMessage());
                    result.put("timestamp", Instant.now());
                    return Mono.just(ResponseEntity.status(500).body(result));
                });
    }
}
