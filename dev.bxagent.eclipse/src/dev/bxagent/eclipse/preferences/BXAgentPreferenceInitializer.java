package dev.bxagent.eclipse.preferences;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;

import dev.bxagent.eclipse.Activator;

/**
 * Registers default values for all BXAgent preferences.
 * Declared via the {@code org.eclipse.core.runtime.preferences} extension point
 * in {@code plugin.xml}.
 */
public class BXAgentPreferenceInitializer extends AbstractPreferenceInitializer {

    @Override
    public void initializeDefaultPreferences() {
        IPreferenceStore store = Activator.getDefault().getPreferenceStore();

        store.setDefault(BXAgentPreferenceConstants.LLM_PROVIDER,      "anthropic"); //$NON-NLS-1$
        store.setDefault(BXAgentPreferenceConstants.LLM_MODEL,         "claude-opus-4-7"); //$NON-NLS-1$
        store.setDefault(BXAgentPreferenceConstants.LLM_ANTHROPIC_KEY, ""); //$NON-NLS-1$
        store.setDefault(BXAgentPreferenceConstants.LLM_OPENAI_KEY,    ""); //$NON-NLS-1$
        store.setDefault(BXAgentPreferenceConstants.LLM_OLLAMA_URL,    "http://localhost:11434"); //$NON-NLS-1$
        store.setDefault(BXAgentPreferenceConstants.AGENT_OUTPUT_DIR,  ""); //$NON-NLS-1$ blank = workspace/generated
        store.setDefault(BXAgentPreferenceConstants.MAX_FIX_ATTEMPTS,  3);
    }
}
