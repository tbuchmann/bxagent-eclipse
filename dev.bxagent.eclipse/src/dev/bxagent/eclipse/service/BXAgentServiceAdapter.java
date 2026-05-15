package dev.bxagent.eclipse.service;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Properties;

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
 */
public final class BXAgentServiceAdapter implements IBXAgentService {

    private final dev.bxagent.service.BXAgentService delegate =
            new dev.bxagent.service.BXAgentService();

    // -----------------------------------------------------------------------
    // IBXAgentService
    // -----------------------------------------------------------------------

    @Override
    public BXAgentSession load(Path leftEcore, Path rightEcore) throws Exception {
        dev.bxagent.service.BXAgentService.Session s =
                delegate.load(leftEcore, rightEcore);
        return wrap(s, null);
    }

    @Override
    public BXAgentSession extractMapping(BXAgentSession session,
            LlmConfig config, Path cacheFile) throws Exception {

        dev.bxagent.service.BXAgentService.Session inner = unwrap(session);
        LlmClient client = LlmClientFactory.create(toJarConfig(config));

        // extractSpec: no system-prompt override, no class excludes, non-interactive
        dev.bxagent.service.BXAgentService.Session updated =
                delegate.extractSpec(inner, client, cacheFile, null,
                        Collections.emptyList(), false);

        // Resolve any ambiguous backward mappings automatically (pick index 0).
        // TODO (Step 8): open a ListSelectionDialog on the UI thread instead.
        updated = delegate.checkBidirectionality(updated, choices -> 0);

        return wrap(updated, null);
    }

    @Override
    public BXAgentSession generate(BXAgentSession session, Path outputDir)
            throws Exception {
        dev.bxagent.service.BXAgentService.Session inner = unwrap(session);
        dev.bxagent.service.BXAgentService.Session updated =
                delegate.generate(inner, outputDir);

        Path generatedPath = null;
        if (updated.generatedTransformation() != null) {
            generatedPath = outputDir.resolve(
                    updated.generatedTransformation().fileName());
        }
        return wrap(updated, generatedPath);
    }

    @Override
    public ValidationResult validate(BXAgentSession session,
            LlmConfig config, int maxAttempts) throws Exception {
        // Note: the JAR's validate() has its own internal fix-loop;
        // maxAttempts is therefore advisory and not forwarded directly.
        dev.bxagent.service.BXAgentService.Session inner = unwrap(session);
        LlmClient client = LlmClientFactory.create(toJarConfig(config));

        CompilationValidator.ValidationResult result =
                delegate.validate(inner, client);

        if (result.success()) {
            return ValidationResult.ok();
        } else {
            return ValidationResult.failed(result.attemptErrors());
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
     * this adapter (e.g. if a {@link StubBXAgentService} was used previously).
     */
    @SuppressWarnings("unchecked")
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
     * {@code dev.bxagent.llm.LlmConfig} using
     * {@code LlmConfig.fromProperties(Properties)}.
     */
    private static dev.bxagent.llm.LlmConfig toJarConfig(LlmConfig cfg) {
        Properties props = new Properties();
        props.setProperty("provider", cfg.getProvider().name().toLowerCase());
        props.setProperty("model",    cfg.getModel());
        if (cfg.getApiKey() != null && !cfg.getApiKey().isEmpty()) {
            props.setProperty("apiKey", cfg.getApiKey());
        }
        if (cfg.getOllamaUrl() != null && !cfg.getOllamaUrl().isEmpty()) {
            props.setProperty("baseUrl", cfg.getOllamaUrl());
        }
        return dev.bxagent.llm.LlmConfig.fromProperties(props);
    }
}
