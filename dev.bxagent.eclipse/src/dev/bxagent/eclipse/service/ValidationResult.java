package dev.bxagent.eclipse.service;

import java.util.Collections;
import java.util.List;

/**
 * Result of a {@link IBXAgentService#validate} call.
 * <p>
 * Mirrors {@code dev.bxagent.validation.ValidationResult} from {@code bx-agent}.
 * </p>
 */
public final class ValidationResult {

    private final boolean      success;
    private final List<String> errors;

    private ValidationResult(boolean success, List<String> errors) {
        this.success = success;
        this.errors  = errors;
    }

    public static ValidationResult ok() {
        return new ValidationResult(true, Collections.emptyList());
    }

    public static ValidationResult failed(List<String> errors) {
        return new ValidationResult(false, Collections.unmodifiableList(errors));
    }

    public boolean      isSuccess() { return success; }
    public List<String> getErrors() { return errors; }
}
