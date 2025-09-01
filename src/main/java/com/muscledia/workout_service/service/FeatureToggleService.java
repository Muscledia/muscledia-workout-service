package com.muscledia.workout_service.service;


import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing feature toggles/flags
 * Allows gradual rollout of new features with fallback mechanisms
 */

@Service
@Slf4j
public class FeatureToggleService {

    // Global feature flags from configuration
    @Value("${workout.features.use-new-calculation:true}")
    private boolean globalUseNewCalculation;

    @Value("${workout.features.enhanced-validation:true}")
    private boolean globalEnhancedValidation;

    @Value("${workout.features.advanced-metrics:true}")
    private boolean globalAdvancedMetrics;

    // User-specific overrides (in production, this would come from a database)
    private final Set<Long> newCalculationEnabledUsers = ConcurrentHashMap.newKeySet();
    private final Set<Long> newCalculationDisabledUsers = ConcurrentHashMap.newKeySet();

    /**
     * Check if user should use new calculation service
     */
    public boolean shouldUseNewCalculation(Long userId) {
        if (userId == null) {
            return globalUseNewCalculation;
        }

        // User-specific override takes precedence
        if (newCalculationEnabledUsers.contains(userId)) {
            log.debug("User {} has new calculation explicitly enabled", userId);
            return true;
        }

        if (newCalculationDisabledUsers.contains(userId)) {
            log.debug("User {} has new calculation explicitly disabled", userId);
            return false;
        }

        // Fall back to global setting
        return globalUseNewCalculation;
    }

    /**
     * Check if enhanced validation should be used
     */
    public boolean shouldUseEnhancedValidation(Long userId) {
        // For now, just use global setting
        // In production, you could add user-specific logic here
        return globalEnhancedValidation;
    }

    /**
     * Check if advanced metrics calculation should be used
     */
    public boolean shouldUseAdvancedMetrics(Long userId) {
        return globalAdvancedMetrics;
    }

    /**
     * Enable new calculation for specific user
     */
    public void enableNewCalculationForUser(Long userId) {
        if (userId != null) {
            newCalculationEnabledUsers.add(userId);
            newCalculationDisabledUsers.remove(userId);
            log.info("Enabled new calculation for user {}", userId);
        }
    }

    /**
     * Disable new calculation for specific user
     */
    public void disableNewCalculationForUser(Long userId) {
        if (userId != null) {
            newCalculationDisabledUsers.add(userId);
            newCalculationEnabledUsers.remove(userId);
            log.info("Disabled new calculation for user {}", userId);
        }
    }

    /**
     * Check if a feature is enabled for a user
     */
    public boolean isFeatureEnabled(String featureName, Long userId) {
        return switch (featureName.toLowerCase()) {
            case "new-calculation", "new_calculation" -> shouldUseNewCalculation(userId);
            case "enhanced-validation", "enhanced_validation" -> shouldUseEnhancedValidation(userId);
            case "advanced-metrics", "advanced_metrics" -> shouldUseAdvancedMetrics(userId);
            default -> {
                log.warn("Unknown feature requested: {}", featureName);
                yield false;
            }
        };
    }

    /**
     * Get all feature states for a user (useful for debugging)
     */
    public FeatureFlags getFeatureFlagsForUser(Long userId) {
        return FeatureFlags.builder()
                .useNewCalculation(shouldUseNewCalculation(userId))
                .useEnhancedValidation(shouldUseEnhancedValidation(userId))
                .useAdvancedMetrics(shouldUseAdvancedMetrics(userId))
                .userId(userId)
                .build();
    }

    /**
     * Value object for feature flags
     */
    @Builder
    @Data
    public static class FeatureFlags {
        private final Long userId;
        private final boolean useNewCalculation;
        private final boolean useEnhancedValidation;
        private final boolean useAdvancedMetrics;
    }
}
