package dev.bxagent.eclipse.service;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;

import dev.bxagent.llm.LlmClient;
import dev.bxagent.llm.LlmClientFactory;
import dev.bxagent.validation.CompilationValidator;

/**
 * Real implementation of {@link IBXAgentService} that delegates to
 * {@code dev.bxagent.service.BXAgentService} from the {@code bx-agent} fat-JAR.
 *
 * <p>Replaces {@link StubBXAgentService} now that the JAR is on the
 * {@code Bundle-ClassPath} (Step 2 of PLAN.md complete).</p>
 *
 * <h3>Session bridging</h3>
 * The JAR's {@code BXAgentService$Session} is stored inside
 * {@link BXAgentSession#setInternalSession} so it can be passed back to the
 * delegate on subsequent calls without re-parsing the metamodels.
 *
 * <h3>LlmConfig bridging</h3>
 * The plugin's {@link LlmConfig} is converted to the JAR's
 * {@code dev.bxagent.llm.LlmConfig} via {@code LlmConfig.fromProperties()}.
 * Property keys used by the JAR: {@code llm.provider}, {@code llm.model},
 * {@code llm.api_key}, {@code llm.base_url}.
 */
public final class BXAgentServiceAdapter implements IBXAgentService {

    private static final String PLUGIN_ID = "dev.bxagent.eclipse";
    private static final ILog   LOG       = Platform.getLog(
            BXAgentServiceAdapter.class);

    private final dev.bxagent.service.BXAgentService delegate =
            new dev.bxagent.service.BXAgentService();

    // -----------------------------------------------------------------------
    // IBXAgentService
    // -----------------------------------------------------------------------

    @Override
    public BXAgentSession load(Path leftEcore, Path rightEcore) throws Exception {
        log(IStatus.INFO, "Loading metamodels: " + leftEcore + ", " + rightEcore, null);
        try {
            dev.bxagent.service.BXAgentService.Session s =
                    delegate.load(leftEcore, rightEcore);
            log(IStatus.INFO, "Metamodels loaded successfully.", null);
            return wrap(s, null);
        } catch (Exception e) {
            log(IStatus.ERROR, "Failed to load metamodels", e);
            throw e;
        }
    }

    @Override
    public BXAgentSession extractMapping(BXAgentSession session,
            LlmConfig config, Path cacheFile, List<String> excludes) throws Exception {

        dev.bxagent.service.BXAgentService.Session inner = unwrap(session);
        dev.bxagent.llm.LlmConfig jarConfig = toJarConfig(config);
        log(IStatus.INFO,
                "extractMapping — provider=" + jarConfig.getProvider()
                + ", model=" + jarConfig.getModel()
                + ", baseUrl=" + jarConfig.getBaseUrl()
                + ", excludes=" + excludes
                + ", cacheFile=" + cacheFile, null);

        LlmClient client = LlmClientFactory.create(jarConfig);
        log(IStatus.INFO, "LlmClient created: " + client.getClass().getName(), null);

        try {
            dev.bxagent.service.BXAgentService.Session updated =
                    delegate.extractSpec(inner, client, cacheFile, null,
                            excludes.isEmpty() ? Collections.emptyList() : excludes,
                            false);
            updated = delegate.checkBidirectionality(updated, choices -> 0);
            log(IStatus.INFO, "Mapping extracted successfully.", null);
            return wrap(updated, null);
        } catch (Exception e) {
            log(IStatus.ERROR, "extractMapping failed", e);
            throw new Exception(e.getMessage() + detailCause(e), e);
        }
    }

    @Override
    public BXAgentSession generate(BXAgentSession session, Path outputDir)
            throws Exception {
        log(IStatus.INFO, "Generating code into " + outputDir, null);
        dev.bxagent.service.BXAgentService.Session inner = unwrap(session);
        try {
            dev.bxagent.service.BXAgentService.Session updated =
                    delegate.generate(inner, outputDir);

            Path generatedPath = null;
            if (updated.generatedTransformation() != null) {
                generatedPath = outputDir.resolve(
                        updated.generatedTransformation().fileName());
            }
            log(IStatus.INFO, "Code generated: " + generatedPath, null);
            return wrap(updated, generatedPath);
        } catch (Exception e) {
            log(IStatus.ERROR, "generate failed", e);
            throw e;
        }
    }

    @Override
    public ValidationResult validate(BXAgentSession session,
            LlmConfig config, int maxAttempts) throws Exception {
        dev.bxagent.service.BXAgentService.Session inner = unwrap(session);
        dev.bxagent.llm.LlmConfig jarConfig = toJarConfig(config);
        log(IStatus.INFO,
                "validate — provider=" + jarConfig.getProvider()
                + ", model=" + jarConfig.getModel(), null);

        LlmClient client = LlmClientFactory.create(jarConfig);
        try {
            CompilationValidator.ValidationResult result =
                    delegate.validate(inner, client);

            if (result.success()) {
                log(IStatus.INFO, "Compilation succeeded.", null);
                return ValidationResult.ok();
            } else {
                log(IStatus.WARNING,
                        "Compilation failed: " + result.getErrorSummary(), null);
                return ValidationResult.failed(result.attemptErrors());
            }
        } catch (Exception e) {
            log(IStatus.ERROR, "validate failed", e);
            throw e;
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Wraps a JAR {@code Session} in a plugin {@link BXAgentSession}, carrying
     * the JAR session as an opaque object for future unwrapping.
     */
    private static BXAgentSession wrap(
            dev.bxagent.service.BXAgentService.Session s, Path generatedPath) {

        String leftDesc  = s.leftSummary()  != null
                ? s.leftSummary().toPromptString()  : "";
        String rightDesc = s.rightSummary() != null
                ? s.rightSummary().toPromptString() : "";
        String specJson  = s.rawMappingJson();

        BXAgentSession ps = new BXAgentSession(
                leftDesc, rightDesc, specJson, generatedPath);
        ps.setInternalSession(s);
        return ps;
    }

    /**
     * Extracts the JAR {@code Session} stored inside a plugin session.
     * Throws {@link IllegalStateException} if the session was not produced by
     * this adapter.
     */
    private static dev.bxagent.service.BXAgentService.Session unwrap(
            BXAgentSession session) {
        Object raw = session.getInternalSession();
        if (raw instanceof dev.bxagent.service.BXAgentService.Session) {
            return (dev.bxagent.service.BXAgentService.Session) raw;
        }
        throw new IllegalStateException(
                "BXAgentSession does not carry an internal BXAgentService.Session. "
                + "Was this session created by BXAgentServiceAdapter?");
    }

    /**
     * Converts the plugin's {@link LlmConfig} to the JAR's
     * {@code dev.bxagent.llm.LlmConfig} via
     * {@code LlmConfig.fromProperties(Properties)}.
     *
     * <p>The JAR uses these exact property keys (all lower-snake-case with
     * {@code llm.} prefix):</p>
     * <ul>
     *   <li>{@code llm.provider}  — "anthropic", "openai", or "ollama"</li>
     *   <li>{@code llm.model}     — model name</li>
     *   <li>{@code llm.api_key}   — API key (Anthropic / OpenAI)</li>
     *   <li>{@code llm.base_url}  — base URL (Ollama or custom endpoint)</li>
     * </ul>
     */
    private static dev.bxagent.llm.LlmConfig toJarConfig(LlmConfig cfg) {
        Properties props = new Properties();
        props.setProperty("llm.provider", cfg.getProvider().name().toLowerCase());
        props.setProperty("llm.model",    cfg.getModel());
        if (cfg.getApiKey() != null && !cfg.getApiKey().isEmpty()) {
            props.setProperty("llm.api_key", cfg.getApiKey());
        }
        if (cfg.getOllamaUrl() != null && !cfg.getOllamaUrl().isEmpty()) {
            props.setProperty("llm.base_url", cfg.getOllamaUrl());
        }
        return dev.bxagent.llm.LlmConfig.fromProperties(props);
    }

    /** Formats the root cause of an exception for display in the chat bubble. */
    private static String detailCause(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null) cause = cause.getCause();
        if (cause == t) return "";
        return "\nCause: " + cause.getClass().getSimpleName()
                + (cause.getMessage() != null ? ": " + cause.getMessage() : "");
    }

    /** Logs a message to the Eclipse Error Log view. */
    private void log(int severity, String message, Throwable t) {
        LOG.log(new Status(severity, PLUGIN_ID, message, t));
    }
}