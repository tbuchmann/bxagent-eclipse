package dev.bxagent.eclipse.preferences;

import org.eclipse.jface.preference.DirectoryFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IntegerFieldEditor;
import org.eclipse.jface.preference.RadioGroupFieldEditor;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import dev.bxagent.eclipse.Activator;

/**
 * Preference page for BXAgent (Step 6 of PLAN.md).
 * Accessible via Window → Preferences → BXAgent.
 */
public class BXAgentPreferencePage extends FieldEditorPreferencePage
        implements IWorkbenchPreferencePage {

    public BXAgentPreferencePage() {
        super(GRID);
        setPreferenceStore(Activator.getDefault().getPreferenceStore());
        setDescription("Configure the BXAgent LLM backend and code generation settings.");
    }

    @Override
    public void init(IWorkbench workbench) {
        // nothing needed
    }

    @Override
    protected void createFieldEditors() {

        // -- LLM Provider --------------------------------------------------
        addField(new RadioGroupFieldEditor(
                BXAgentPreferenceConstants.LLM_PROVIDER,
                "LLM Provider",
                1,
                new String[][] {
                    { "Anthropic (Claude)", "anthropic" },
                    { "OpenAI (GPT)",       "openai"    },
                    { "Ollama (local)",     "ollama"    }
                },
                getFieldEditorParent()));

        // -- Model name ----------------------------------------------------
        addField(new StringFieldEditor(
                BXAgentPreferenceConstants.LLM_MODEL,
                "Model name:",
                getFieldEditorParent()));

        // -- API keys / URL ------------------------------------------------
        StringFieldEditor anthropicKey = new StringFieldEditor(
                BXAgentPreferenceConstants.LLM_ANTHROPIC_KEY,
                "Anthropic API key:",
                getFieldEditorParent());
        anthropicKey.getTextControl(getFieldEditorParent()).setEchoChar('*');
        addField(anthropicKey);

        StringFieldEditor openaiKey = new StringFieldEditor(
                BXAgentPreferenceConstants.LLM_OPENAI_KEY,
                "OpenAI API key:",
                getFieldEditorParent());
        openaiKey.getTextControl(getFieldEditorParent()).setEchoChar('*');
        addField(openaiKey);

        addField(new StringFieldEditor(
                BXAgentPreferenceConstants.LLM_OLLAMA_URL,
                "Ollama URL:",
                getFieldEditorParent()));

        StringFieldEditor ollamaKey = new StringFieldEditor(
                BXAgentPreferenceConstants.LLM_OLLAMA_KEY,
                "Ollama API key (Bearer token, optional):",
                getFieldEditorParent());
        ollamaKey.getTextControl(getFieldEditorParent()).setEchoChar('*');
        addField(ollamaKey);

        // -- Code generation -----------------------------------------------
        addField(new DirectoryFieldEditor(
                BXAgentPreferenceConstants.AGENT_OUTPUT_DIR,
                "Generated code output directory:",
                getFieldEditorParent()));

        IntegerFieldEditor maxFix = new IntegerFieldEditor(
                BXAgentPreferenceConstants.MAX_FIX_ATTEMPTS,
                "Max LLM fix attempts (compilation):",
                getFieldEditorParent());
        maxFix.setValidRange(1, 10);
        addField(maxFix);

        addField(new StringFieldEditor(
                BXAgentPreferenceConstants.AGENT_EXCLUDE_LIST,
                "Exclude from mapping (comma-separated class/attribute names):",
                getFieldEditorParent()));
    }
}
