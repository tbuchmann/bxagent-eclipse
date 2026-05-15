package dev.bxagent.eclipse.service;

import java.nio.file.Path;
import java.util.List;

/**
 * UI-agnostic façade for the BXAgent generation pipeline.
 * <p>
 * This interface is the contract between the Eclipse plugin and the {@code bx-agent}
 * module. The real implementation ({@code BXAgentService}) will be provided once
 * Steps 1 and 2 of the plan are complete and the fat-JAR is on the Bundle-ClassPath.
 * Until then, {@link StubBXAgentService} is used.
 * </p>
 *
 * <p>All methods are synchronous and intended to be called from a background
 * {@code Job}; never call them on the SWT UI thread.</p>
 */
public interface IBXAgentService {

    /**
     * Parse two {@code .ecore} files and return an initial session with metamodel
     * summaries populated.
     *
     * @param leftEcore  path to the left/source metamodel
     * @param rightEcore path to the right/target metamodel
     * @return new session; {@code spec} and {@code generatedClass} are {@code null}
     * @throws Exception if either file cannot be parsed
     */
    BXAgentSession load(Path leftEcore, Path rightEcore) throws Exception;

    /**
     * Invoke the LLM to produce a {@link TransformationSpec} from the metamodel
     * summaries already stored in {@code session}.
     *
     * @param session   current session (must have summaries)
     * @param config    LLM configuration (provider, model, key/URL)
     * @param cacheFile optional path to a cached JSON response; {@code null} = live call
     * @param excludes  EClass/EAttribute names to omit from the mapping prompt;
     *                  mirrors the CLI {@code --exclude} flag; may be empty but not null
     * @return updated session with {@code spec} populated
     * @throws Exception on LLM error or JSON parse failure
     */
    BXAgentSession extractMapping(BXAgentSession session, LlmConfig config,
            Path cacheFile, List<String> excludes) throws Exception;

    /**
     * Run FreeMarker code generation and write the {@code Transformation.java} file.
     *
     * @param session   current session (must have {@code spec})
     * @param outputDir directory where the generated {@code .java} file is written
     * @return updated session with {@code generatedClass} populated
     * @throws Exception on template or I/O error
     */
    BXAgentSession generate(BXAgentSession session, Path outputDir) throws Exception;

    /**
     * Compile the generated class with {@code javac}. On failure the LLM fix-loop
     * runs up to {@code maxAttempts} times.
     *
     * @param session     current session (must have {@code generatedClass})
     * @param config      LLM configuration for the fix loop
     * @param maxAttempts maximum number of LLM-assisted fix attempts (≥ 1)
     * @return validation result; {@link ValidationResult#success()} is {@code true}
     *         when compilation succeeded
     * @throws Exception on unexpected I/O or classpath error
     */
    ValidationResult validate(BXAgentSession session, LlmConfig config, int maxAttempts)
            throws Exception;
}
