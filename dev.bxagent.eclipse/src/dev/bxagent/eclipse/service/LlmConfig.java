package dev.bxagent.eclipse.service;

/**
 * LLM provider + credentials, read from the Eclipse preference store.
 * <p>
 * This mirrors {@code dev.bxagent.llm.LlmConfig} from the {@code bx-agent} module.
 * Once the fat-JAR is on the Bundle-ClassPath the controller will construct the real
 * {@code LlmConfig} directly; this class can then be removed.
 * </p>
 */
public final class LlmConfig {

    public enum Provider { ANTHROPIC, OPENAI, OLLAMA }

    private final Provider provider;
    private final String   model;
    private final String   apiKey;   // null for Ollama
    private final String   ollamaUrl; // null for Anthropic / OpenAI

    private LlmConfig(Provider provider, String model, String apiKey, String ollamaUrl) {
        this.provider  = provider;
        this.model     = model;
        this.apiKey    = apiKey;
        this.ollamaUrl = ollamaUrl;
    }

    public static LlmConfig anthropic(String apiKey, String model) {
        return new LlmConfig(Provider.ANTHROPIC, model, apiKey, null);
    }

    public static LlmConfig openai(String apiKey, String model) {
        return new LlmConfig(Provider.OPENAI, model, apiKey, null);
    }

    public static LlmConfig ollama(String url, String model) {
        return new LlmConfig(Provider.OLLAMA, model, null, url);
    }

    public Provider getProvider()  { return provider; }
    public String   getModel()     { return model; }
    public String   getApiKey()    { return apiKey; }
    public String   getOllamaUrl() { return ollamaUrl; }
}
