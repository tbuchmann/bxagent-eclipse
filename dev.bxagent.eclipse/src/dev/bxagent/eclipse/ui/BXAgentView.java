package dev.bxagent.eclipse.ui;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.part.ViewPart;

import dev.bxagent.eclipse.ui.ChatBubble.Sender;

/**
 * The BXAgent Chat view (Step 4 of PLAN.md).
 *
 * <p>Layout:</p>
 * <pre>
 * ┌──────────────────────────────────────────────────────┐
 * │ Toolbar: [Load Metamodels…]  [Settings]  [Clear]     │
 * ├──────────────────────────────────────────────────────┤
 * │                                                       │
 * │  [scrollable chat history]                            │
 * │                                                       │
 * ├──────────────────────────────────────────────────────┤
 * │  [Text input field ........................] [Send ▶] │
 * └──────────────────────────────────────────────────────┘
 * </pre>
 *
 * <p>The view delegates all business logic to {@link ConversationController}.</p>
 */
public class BXAgentView extends ViewPart {

    public static final String ID = "dev.bxagent.eclipse.ui.BXAgentView"; //$NON-NLS-1$

    // UI widgets
    private ScrolledComposite scrolled;
    private Composite         historyPanel;
    private Text              inputField;
    private Button            sendButton;

    // Chat state
    private final List<ChatBubble> bubbles = new ArrayList<>();
    private ConversationController controller;

    // -----------------------------------------------------------------------
    // ViewPart lifecycle
    // -----------------------------------------------------------------------

    @Override
    public void createPartControl(Composite parent) {
        controller = new ConversationController(this);

        parent.setLayout(new GridLayout(1, false));

        createToolBar();
        createHistoryArea(parent);
        createInputArea(parent);

        appendAgentBubble(
                "Welcome to BXAgent Chat!\n\n" +
                "To get started, click \uD83D\uDCC2 Load Metamodels in the toolbar " +
                "and select your two .ecore files.");
    }

    @Override
    public void setFocus() {
        inputField.setFocus();
    }

    // -----------------------------------------------------------------------
    // Toolbar
    // -----------------------------------------------------------------------

    private void createToolBar() {
        IToolBarManager tb = getViewSite().getActionBars().getToolBarManager();

        Action loadAction = new Action("Load Metamodels\u2026") { //$NON-NLS-1$
            @Override public void run() { controller.onLoadMetamodels(); }
        };
        loadAction.setToolTipText("Select two .ecore metamodel files");
        // Icon placeholder — replace icons/load.png when artwork is available
        tb.add(loadAction);

        Action settingsAction = new Action("Settings") { //$NON-NLS-1$
            @Override public void run() { openPreferences(); }
        };
        settingsAction.setToolTipText("Open BXAgent preferences");
        tb.add(settingsAction);

        Action clearAction = new Action("Clear") { //$NON-NLS-1$
            @Override public void run() { clearHistory(); }
        };
        clearAction.setToolTipText("Clear conversation and reset session");
        tb.add(clearAction);
    }

    // -----------------------------------------------------------------------
    // History area
    // -----------------------------------------------------------------------

    private void createHistoryArea(Composite parent) {
        scrolled = new ScrolledComposite(parent, SWT.V_SCROLL | SWT.BORDER);
        scrolled.setExpandHorizontal(true);
        scrolled.setExpandVertical(false);
        GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true);
        scrolled.setLayoutData(gd);

        historyPanel = new Composite(scrolled, SWT.NONE);
        historyPanel.setLayout(new GridLayout(1, false));
        scrolled.setContent(historyPanel);
    }

    // -----------------------------------------------------------------------
    // Input area
    // -----------------------------------------------------------------------

    private void createInputArea(Composite parent) {
        Composite inputRow = new Composite(parent, SWT.NONE);
        GridLayout gl = new GridLayout(2, false);
        gl.marginWidth = 4;
        gl.marginHeight = 4;
        inputRow.setLayout(gl);
        inputRow.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));

        inputField = new Text(inputRow, SWT.MULTI | SWT.WRAP | SWT.BORDER | SWT.V_SCROLL);
        GridData inputGd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        inputGd.heightHint = 54;
        inputField.setLayoutData(inputGd);
        inputField.setMessage("Type a message… (Enter to send, Shift+Enter for newline)");

        // Enter = send, Shift+Enter = newline
        inputField.addKeyListener(new org.eclipse.swt.events.KeyAdapter() {
            @Override
            public void keyPressed(org.eclipse.swt.events.KeyEvent e) {
                if (e.keyCode == SWT.CR || e.keyCode == SWT.KEYPAD_CR) {
                    if ((e.stateMask & SWT.SHIFT) == 0) {
                        e.doit = false;
                        doSend();
                    }
                }
            }
        });

        sendButton = new Button(inputRow, SWT.PUSH);
        sendButton.setText("Send \u25B6"); //$NON-NLS-1$
        GridData btnGd = new GridData(SWT.RIGHT, SWT.CENTER, false, false);
        sendButton.setLayoutData(btnGd);
        sendButton.addListener(SWT.Selection, e -> doSend());
    }

    // -----------------------------------------------------------------------
    // Public API used by ConversationController
    // -----------------------------------------------------------------------

    /**
     * Appends a user bubble to the history. Must be called on the UI thread.
     *
     * @param text message text
     * @return the created bubble (so the controller can update it later)
     */
    public ChatBubble appendUserBubble(String text) {
        return appendBubble(Sender.USER, text);
    }

    /**
     * Appends an agent bubble to the history. Must be called on the UI thread.
     *
     * @param text message text
     * @return the created bubble
     */
    public ChatBubble appendAgentBubble(String text) {
        return appendBubble(Sender.AGENT, text);
    }

    /**
     * Appends an agent bubble that contains an inline action button.
     * Clicking the button fires {@code action} on the UI thread.
     *
     * @param label  button label
     * @param action logic to execute when the button is clicked
     */
    public void appendActionBubble(String label, Runnable action) {
        ChatBubble bubble = appendBubble(Sender.AGENT, "");
        Composite container = bubble.getContainer();
        Button btn = new Button(container, SWT.PUSH);
        btn.setText(label);
        btn.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false));
        btn.addListener(SWT.Selection, e -> {
            btn.setEnabled(false);
            action.run();
        });
        relayout();
    }

    /**
     * Enables or disables the send button and input field.
     * Called by the controller while a background job is running.
     */
    public void setInputEnabled(boolean enabled) {
        if (!inputField.isDisposed()) {
            inputField.setEnabled(enabled);
            sendButton.setEnabled(enabled);
        }
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private ChatBubble appendBubble(Sender sender, String text) {
        ChatBubble bubble = new ChatBubble(historyPanel, sender, text);
        bubbles.add(bubble);
        relayout();
        scrollToBottom();
        return bubble;
    }

    private void doSend() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;
        inputField.setText(""); //$NON-NLS-1$
        appendUserBubble(text);
        controller.onUserMessage(text);
    }

    private void clearHistory() {
        for (ChatBubble b : bubbles) {
            if (!b.getContainer().isDisposed()) b.getContainer().dispose();
        }
        bubbles.clear();
        controller.onClear();
        appendAgentBubble("Conversation cleared. Load metamodels to start a new session.");
        relayout();
    }

    private void relayout() {
        historyPanel.layout(true, true);
        historyPanel.setSize(historyPanel.computeSize(
                scrolled.getClientArea().width, SWT.DEFAULT));
        scrolled.layout(true, true);
    }

    private void scrollToBottom() {
        Display.getCurrent().asyncExec(() -> {
            if (!scrolled.isDisposed()) {
                scrolled.setOrigin(0, historyPanel.getSize().y);
            }
        });
    }

    private void openPreferences() {
        PreferenceDialog dialog = PreferencesUtil.createPreferenceDialogOn(
                getSite().getShell(),
                "dev.bxagent.eclipse.preferences.BXAgentPreferencePage", //$NON-NLS-1$
                new String[]{ "dev.bxagent.eclipse.preferences.BXAgentPreferencePage" },
                null);
        dialog.open();
    }
}
