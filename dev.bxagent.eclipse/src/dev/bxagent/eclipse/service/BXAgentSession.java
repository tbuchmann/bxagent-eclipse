package dev.bxagent.eclipse.service;

import java.nio.file.Path;

/**
 * Immutable snapshot of the current BXAgent pipeline state for one Eclipse session.
 * <p>
 * Fields are {@code null} until the corresponding pipeline step has completed:
 * <ul>
 *   <li>{@code leftSummary} / {@code rightSummary} — after {@code load()}</li>
 *   <li>{@code specJson} — after {@code extractMapping()}</li>
 *   <li>{@code generatedClass} — after {@code generate()}</li>
 * </ul>
 * </p>
 *
 * <p>This class mirrors {@code BXAgentService.Session} from the {@code bx-agent}
 * module. Once the fat-JAR is available (Steps 1 & 2), this class will be replaced
 * by a thin wrapper or removed entirely.</p>
 */
public final class BXAgentSession {

    private final String leftSummary;
    private final String rightSummary;
    /** Raw JSON string of the TransformationSpec produced by the LLM. */
    private final String specJson;
    /** Path to the generated {@code Transformation.java} file. */
    private final Path generatedClass;

    /**
     * Opaque carrier for the {@code dev.bxagent.service.BXAgentService$Session}
     * from the fat-JAR.  Set and read only by {@code BXAgentServiceAdapter};
     * all other code must ignore this field.
     */
    private Object internalSession;

    public BXAgentSession(String leftSummary, String rightSummary,
            String specJson, Path generatedClass) {
        this.leftSummary = leftSummary;
        this.rightSummary = rightSummary;
        this.specJson = specJson;
        this.generatedClass = generatedClass;
    }

    /** Convenience constructor for a freshly loaded session (no spec/code yet). */
    public static BXAgentSession loaded(String leftSummary, String rightSummary) {
        return new BXAgentSession(leftSummary, rightSummary, null, null);
    }

    public BXAgentSession withSpec(String specJson) {
        return new BXAgentSession(leftSummary, rightSummary, specJson, generatedClass);
    }

    public BXAgentSession withGeneratedClass(Path generatedClass) {
        return new BXAgentSession(leftSummary, rightSummary, specJson, generatedClass);
    }

    public String getLeftSummary()  { return leftSummary; }
    public String getRightSummary() { return rightSummary; }
    public String getSpecJson()     { return specJson; }
    public Path   getGeneratedClass() { return generatedClass; }

    public boolean hasSpec()           { return specJson != null; }
    public boolean hasGeneratedClass() { return generatedClass != null; }

    /** For use by {@code BXAgentServiceAdapter} only. */
    public void   setInternalSession(Object s) { this.internalSession = s; }
    /** For use by {@code BXAgentServiceAdapter} only. */
    public Object getInternalSession()         { return internalSession; }
}
