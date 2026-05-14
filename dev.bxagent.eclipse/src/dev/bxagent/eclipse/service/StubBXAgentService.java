package dev.bxagent.eclipse.service;

import java.nio.file.Path;
import java.util.List;

/**
 * Placeholder implementation of {@link IBXAgentService} used while
 * {@code bx-agent} (Steps 1 & 2) is still being integrated.
 * <p>
 * Every method throws {@link UnsupportedOperationException} with a clear message.
 * Replace this with the real adapter once the fat-JAR is on the Bundle-ClassPath.
 * </p>
 */
public final class StubBXAgentService implements IBXAgentService {

    private static final String MSG =
            "BXAgentService (bx-agent JAR) is not yet integrated. " +
            "Complete Steps 1 & 2 of PLAN.md first.";

    @Override
    public BXAgentSession load(Path leftEcore, Path rightEcore) throws Exception {
        // TODO: replace with real BXAgentService.load() call
        throw new UnsupportedOperationException(MSG);
    }

    @Override
    public BXAgentSession extractMapping(BXAgentSession session, LlmConfig config,
            Path cacheFile) throws Exception {
        // TODO: replace with real BXAgentService.extractMapping() call
        throw new UnsupportedOperationException(MSG);
    }

    @Override
    public BXAgentSession generate(BXAgentSession session, Path outputDir) throws Exception {
        // TODO: replace with real BXAgentService.generate() call
        throw new UnsupportedOperationException(MSG);
    }

    @Override
    public ValidationResult validate(BXAgentSession session, LlmConfig config,
            int maxAttempts) throws Exception {
        // TODO: replace with real BXAgentService.validate() call
        throw new UnsupportedOperationException(MSG);
    }
}
