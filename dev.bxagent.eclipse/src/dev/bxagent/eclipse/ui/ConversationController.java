package dev.bxagent.eclipse.ui;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.dialogs.ElementTreeSelectionDialog;
import org.eclipse.ui.model.WorkbenchContentProvider;
import org.eclipse.ui.model.WorkbenchLabelProvider;

import dev.bxagent.eclipse.Activator;
import dev.bxagent.eclipse.preferences.BXAgentPreferenceConstants;
import dev.bxagent.eclipse.service.BXAgentSession;
import dev.bxagent.eclipse.service.IBXAgentService;
import dev.bxagent.eclipse.service.LlmConfig;
import dev.bxagent.eclipse.service.BXAgentServiceAdapter;
import dev.bxagent.eclipse.service.ValidationResult;

/**
 * Wires {@link BXAgentView} to {@link IBXAgentService}, implementing the
 * conversation state machine defined in Step 5 of PLAN.md.
 *
 * <h3>State machine</h3>
 * <pre>
 * IDLE
 *   → (load files)        → LOADING
 * LOADING
 *   → (success)           → LOADED      ["Metamodels loaded. Extract mapping?"]
 *   → (error)             → IDLE
 * LOADED
 *   → (user confirms)     → EXTRACTING
 * EXTRACTING
 *   → (success)           → SPEC_READY  [spec summary + "Generate code?"]
 *   → (LLM error)         → LOADED
 * SPEC_READY
 *   → (user confirms)     → GENERATING
 * GENERATING
 *   → (success)           → GENERATED   [file path + "Validate?"]
 *   → (error)             → SPEC_READY
 * GENERATED
 *   → (validate)          → VALIDATING
 * VALIDATING
 *   → (success)           → READY       ["Compilation OK. Ready."]
 *   → (still failing)     → GENERATED
 * READY
 *   → (new load)          → IDLE
 * </pre>
 */
public final class ConversationController {

    // -----------------------------------------------------------------------
    // State
    // -----------------------------------------------------------------------

    public enum State {
        IDLE, LOADING, LOADED, EXTRACTING, SPEC_READY,
        GENERATING, GENERATED, VALIDATING, READY
    }

    private State          state   = State.IDLE;
    private BXAgentSession session = null;
    private final IBXAgentService service;
    private final BXAgentView     view;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public ConversationController(BXAgentView view) {
        this.view    = view;
        // Steps 1 & 2 complete: use the real BXAgentServiceAdapter backed by the fat-JAR.
        this.service = new BXAgentServiceAdapter();
    }

    // -----------------------------------------------------------------------
    // View callbacks
    // -----------------------------------------------------------------------

    /** Called when the user types a free-text message and presses Send. */
    public void onUserMessage(String text) {
        String lower = text.toLowerCase();
        switch (state) {
            case LOADED:
                if (containsAffirmative(lower)) { doExtractMapping(); return; }
                break;
            case SPEC_READY:
                if (containsAffirmative(lower)) { doGenerate(); return; }
                break;
            case GENERATED:
                if (lower.contains("valid") || containsAffirmative(lower)) {
                    doValidate(); return;
                }
                break;
            default:
                break;
        }
        // Fallback: echo helpful hint
        appendAgent(stateHint());
    }

    /** Called when the "Load Metamodels" toolbar button is pressed. */
    public void onLoadMetamodels() {
        Display display = Display.getCurrent();

        IFile leftFile  = pickEcoreFile("Select Left / Source Metamodel (.ecore)");
        if (leftFile == null) return;
        IFile rightFile = pickEcoreFile("Select Right / Target Metamodel (.ecore)");
        if (rightFile == null) return;

        Path left  = leftFile.getLocation().toFile().toPath();
        Path right = rightFile.getLocation().toFile().toPath();

        appendAgent("Loading " + leftFile.getName() + " and " + rightFile.getName() + " …");
        view.setInputEnabled(false);
        setState(State.LOADING);

        Job job = Job.create("BXAgent: Load Metamodels", (IProgressMonitor monitor) -> {
            try {
                BXAgentSession loaded = service.load(left, right);
                display.asyncExec(() -> {
                    session = loaded;
                    setState(State.LOADED);
                    view.setInputEnabled(true);
                    appendAgent(
                            "\u2713 Metamodels loaded.\n\n" +
                            "Left:  " + leftFile.getName() + "\n" +
                            "Right: " + rightFile.getName() + "\n\n" +
                            "Type \u201Cyes\u201D or click the button to extract the mapping.");
                    appendActionPrompt("Extract Mapping", () -> doExtractMapping());
                });
            } catch (Exception e) {
                display.asyncExec(() -> {
                    setState(State.IDLE);
                    view.setInputEnabled(true);
                    appendError("Failed to load metamodels: " + e.getMessage());
                });
            }
            return Status.OK_STATUS;
        });
        job.setUser(true);
        job.schedule();
    }

    /** Called when the "Clear" toolbar button is pressed. */
    public void onClear() {
        session = null;
        setState(State.IDLE);
    }

    // -----------------------------------------------------------------------
    // Pipeline steps
    // -----------------------------------------------------------------------

    private void doExtractMapping() {
        if (session == null || state != State.LOADED) return;
        ChatBubble spinner = appendAgent("Extracting mapping \u2026 (calling LLM)");
        view.setInputEnabled(false);
        setState(State.EXTRACTING);
        Display display = Display.getCurrent();

        Job job = Job.create("BXAgent: Extract Mapping", (IProgressMonitor monitor) -> {
            try {
                BXAgentSession updated = service.extractMapping(session, buildLlmConfig(), null);
                display.asyncExec(() -> {
                    session = updated;
                    setState(State.SPEC_READY);
                    view.setInputEnabled(true);
                    spinner.updateBody(
                            "\u2713 Mapping extracted.\n\n" +
                            "Type \u201Cyes\u201D or click the button to generate the transformation class.");
                    appendActionPrompt("Generate Code", () -> doGenerate());
                });
            } catch (Exception e) {
                display.asyncExec(() -> {
                    setState(State.LOADED);
                    view.setInputEnabled(true);
                    spinner.updateBody("\u2717 Mapping extraction failed: " + e.getMessage());
                });
            }
            return Status.OK_STATUS;
        });
        job.setUser(true);
        job.schedule();
    }

    private void doGenerate() {
        if (session == null || state != State.SPEC_READY) return;
        ChatBubble spinner = appendAgent("Generating transformation class \u2026");
        view.setInputEnabled(false);
        setState(State.GENERATING);
        Display display = Display.getCurrent();

        Path outputDir = resolveOutputDir();

        Job job = Job.create("BXAgent: Generate Code", (IProgressMonitor monitor) -> {
            try {
                BXAgentSession updated = service.generate(session, outputDir);
                display.asyncExec(() -> {
                    session = updated;
                    setState(State.GENERATED);
                    view.setInputEnabled(true);
                    spinner.updateBody(
                            "\u2713 Code generated:\n" +
                            updated.getGeneratedClass().toString() + "\n\n" +
                            "Type \u201Cvalidate\u201D or click the button to compile.");
                    appendActionPrompt("Validate (Compile)", () -> doValidate());
                });
            } catch (Exception e) {
                display.asyncExec(() -> {
                    setState(State.SPEC_READY);
                    view.setInputEnabled(true);
                    spinner.updateBody("\u2717 Code generation failed: " + e.getMessage());
                });
            }
            return Status.OK_STATUS;
        });
        job.setUser(true);
        job.schedule();
    }

    private void doValidate() {
        if (session == null || state != State.GENERATED) return;
        ChatBubble spinner = appendAgent("Compiling \u2026 (LLM fix loop active if needed)");
        view.setInputEnabled(false);
        setState(State.VALIDATING);
        Display display = Display.getCurrent();

        int maxAttempts = Activator.getDefault().getPreferenceStore()
                .getInt(BXAgentPreferenceConstants.MAX_FIX_ATTEMPTS);

        Job job = Job.create("BXAgent: Validate", (IProgressMonitor monitor) -> {
            try {
                ValidationResult result = service.validate(session, buildLlmConfig(), maxAttempts);
                display.asyncExec(() -> {
                    view.setInputEnabled(true);
                    if (result.isSuccess()) {
                        setState(State.READY);
                        spinner.updateBody(
                                "\u2713 Compilation successful!\n\n" +
                                "The transformation class is ready to use.\n" +
                                "(Sync support will be available in a future version.)");
                    } else {
                        setState(State.GENERATED);
                        spinner.updateBody(
                                "\u2717 Compilation failed after " + maxAttempts + " attempt(s).\n\n" +
                                String.join("\n", result.getErrors()) + "\n\n" +
                                "You may try again or adjust the spec.");
                        appendActionPrompt("Retry Validation", () -> doValidate());
                    }
                });
            } catch (Exception e) {
                display.asyncExec(() -> {
                    setState(State.GENERATED);
                    view.setInputEnabled(true);
                    spinner.updateBody("\u2717 Validation error: " + e.getMessage());
                });
            }
            return Status.OK_STATUS;
        });
        job.setUser(true);
        job.schedule();
    }

    // -----------------------------------------------------------------------
    // UI helpers
    // -----------------------------------------------------------------------

    /**
     * Appends an agent bubble and returns it so the caller can update it later
     * (e.g. replace "working…" with the final result).
     */
    private ChatBubble appendAgent(String text) {
        return view.appendAgentBubble(text);
    }

    private void appendError(String text) {
        view.appendAgentBubble("\u274C " + text);
    }

    /**
     * Appends an agent bubble that contains a clickable action button.
     * This gives a Copilot-style "inline action" experience without requiring
     * the user to type a confirmation.
     *
     * <p>Implementation note: the actual Button widget is created inside
     * {@link BXAgentView}; here we call back via a Runnable. This approach keeps
     * the view responsible for all widget creation while the controller owns
     * the logic.</p>
     */
    private void appendActionPrompt(String label, Runnable action) {
        // Delegate widget creation to the view
        view.appendActionBubble(label, action);
    }

    private String stateHint() {
        switch (state) {
            case IDLE:       return "Please load metamodels first using the toolbar button.";
            case LOADED:     return "Metamodels are loaded. Say \u201Cyes\u201D to extract the mapping.";
            case SPEC_READY: return "Mapping is ready. Say \u201Cyes\u201D to generate the transformation class.";
            case GENERATED:  return "Code is generated. Say \u201Cvalidate\u201D to compile it.";
            case READY:      return "The transformation class is compiled and ready.";
            default:         return "Please wait for the current operation to finish.";
        }
    }

    private static boolean containsAffirmative(String text) {
        return text.contains("yes") || text.contains("ok") || text.contains("sure")
                || text.contains("go") || text.contains("proceed") || text.contains("continue");
    }

    // -----------------------------------------------------------------------
    // Config helpers
    // -----------------------------------------------------------------------

    private LlmConfig buildLlmConfig() {
        var prefs = Activator.getDefault().getPreferenceStore();
        String provider = prefs.getString(BXAgentPreferenceConstants.LLM_PROVIDER);
        String model    = prefs.getString(BXAgentPreferenceConstants.LLM_MODEL);
        switch (provider) {
            case "openai":
                return LlmConfig.openai(
                        prefs.getString(BXAgentPreferenceConstants.LLM_OPENAI_KEY), model);
            case "ollama":
                return LlmConfig.ollama(
                        prefs.getString(BXAgentPreferenceConstants.LLM_OLLAMA_URL), model);
            default: // anthropic
                return LlmConfig.anthropic(
                        prefs.getString(BXAgentPreferenceConstants.LLM_ANTHROPIC_KEY), model);
        }
    }

    private Path resolveOutputDir() {
        var prefs = Activator.getDefault().getPreferenceStore();
        String raw = prefs.getString(BXAgentPreferenceConstants.AGENT_OUTPUT_DIR);
        if (raw == null || raw.isBlank()) {
            return ResourcesPlugin.getWorkspace().getRoot().getLocation()
                    .append("generated").toFile().toPath();
        }
        return Paths.get(raw);
    }

    // -----------------------------------------------------------------------
    // Workspace file picker
    // -----------------------------------------------------------------------

    private IFile pickEcoreFile(String title) {
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        ElementTreeSelectionDialog dialog = new ElementTreeSelectionDialog(
                Display.getCurrent().getActiveShell(),
                new WorkbenchLabelProvider(),
                new WorkbenchContentProvider());
        dialog.setTitle(title);
        dialog.setMessage("Select an .ecore metamodel file:");
        dialog.setInput(root);
        dialog.addFilter(new ViewerFilter() {
            @Override
            public boolean select(Viewer viewer, Object parentElement, Object element) {
                if (element instanceof IFile) {
                    return ((IFile) element).getFileExtension() != null
                            && ((IFile) element).getFileExtension().equals("ecore");
                }
                return true; // show containers
            }
        });
        if (dialog.open() == Window.OK) {
            Object result = dialog.getFirstResult();
            if (result instanceof IFile) return (IFile) result;
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Internal
    // -----------------------------------------------------------------------

    private void setState(State newState) {
        this.state = newState;
    }

    public State getState() {
        return state;
    }
}
