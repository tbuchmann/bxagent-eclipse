package dev.bxagent.eclipse.preferences;

/**
 * Preference key constants for the BXAgent Eclipse plugin.
 * Used by {@link BXAgentPreferencePage}, {@link BXAgentPreferenceInitializer},
 * and {@link dev.bxagent.eclipse.ui.ConversationController}.
 */
public final class BXAgentPreferenceConstants {

    private BXAgentPreferenceConstants() {}

    /** LLM provider: "anthropic", "openai", or "ollama". */
    public static final String LLM_PROVIDER      = "llm.provider"; //$NON-NLS-1$

    /** Model name, e.g. "claude-opus-4-7" or "gpt-4o". */
    public static final String LLM_MODEL         = "llm.model"; //$NON-NLS-1$

    /** Anthropic API key. */
    public static final String LLM_ANTHROPIC_KEY = "llm.anthropicKey"; //$NON-NLS-1$

    /** OpenAI API key. */
    public static final String LLM_OPENAI_KEY    = "llm.openaiKey"; //$NON-NLS-1$

    /** Ollama base URL. */
    public static final String LLM_OLLAMA_URL    = "llm.ollamaUrl"; //$NON-NLS-1$

    /** Directory where generated .java files are written. */
    public static final String AGENT_OUTPUT_DIR  = "agent.outputDir"; //$NON-NLS-1$

    /** Maximum number of LLM-assisted fix attempts during compilation. */
    public static final String MAX_FIX_ATTEMPTS  = "agent.maxFixAttempts"; //$NON-NLS-1$
}
