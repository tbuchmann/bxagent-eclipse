package dev.bxagent.eclipse.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

/**
 * A single chat message bubble rendered inside the history panel of
 * {@link BXAgentView}.
 *
 * <p>Each bubble consists of:</p>
 * <ul>
 *   <li>A bold sender label ("You" or "BXAgent")</li>
 *   <li>A read-only, word-wrapped text area for the message body</li>
 *   <li>A thin separator line beneath</li>
 * </ul>
 *
 * <p>The background colour differs between user and agent bubbles to give a
 * Copilot-style alternating appearance.</p>
 */
public final class ChatBubble {

    /** Who sent this message. */
    public enum Sender { USER, AGENT }

    private final Composite container;
    private final Text       bodyText;

    /**
     * Creates and lays out the bubble inside {@code parent}.
     *
     * @param parent the parent composite (typically the inner composite of a
     *               {@code ScrolledComposite})
     * @param sender who is speaking
     * @param body   initial message text; may be updated later via {@link #updateBody}
     */
    public ChatBubble(Composite parent, Sender sender, String body) {
        Display display = parent.getDisplay();

        // -- outer wrapper --------------------------------------------------
        container = new Composite(parent, SWT.NONE);
        GridLayout layout = new GridLayout(1, false);
        layout.marginWidth  = 12;
        layout.marginHeight = 8;
        layout.verticalSpacing = 4;
        container.setLayout(layout);
        container.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        Color bg = sender == Sender.USER
                ? display.getSystemColor(SWT.COLOR_LIST_BACKGROUND)
                : new Color(display, 240, 246, 255); // light blue tint for agent
        container.setBackground(bg);

        // -- sender label ---------------------------------------------------
        Label senderLabel = new Label(container, SWT.NONE);
        senderLabel.setText(sender == Sender.USER ? "You" : "BXAgent");
        senderLabel.setBackground(bg);
        senderLabel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        FontData[] fd = senderLabel.getFont().getFontData();
        for (FontData d : fd) d.setStyle(SWT.BOLD);
        Font boldFont = new Font(display, fd);
        senderLabel.setFont(boldFont);
        senderLabel.addDisposeListener(e -> boldFont.dispose());

        // -- message body ---------------------------------------------------
        bodyText = new Text(container,
                SWT.MULTI | SWT.WRAP | SWT.READ_ONLY | SWT.V_SCROLL);
        bodyText.setText(body);
        bodyText.setBackground(bg);
        GridData gd = new GridData(SWT.FILL, SWT.TOP, true, false);
        gd.widthHint = 400;
        bodyText.setLayoutData(gd);

        // -- separator ------------------------------------------------------
        Label sep = new Label(container, SWT.SEPARATOR | SWT.HORIZONTAL);
        sep.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
    }

    /**
     * Replaces the body text. Must be called on the SWT UI thread.
     *
     * @param text new message body (e.g. to swap a "…working…" spinner placeholder
     *             with the final result)
     */
    public void updateBody(String text) {
        if (!bodyText.isDisposed()) {
            bodyText.setText(text);
            container.getParent().layout(true, true);
        }
    }

    /**
     * Appends text to the existing body. Useful for streaming-style updates.
     * Must be called on the SWT UI thread.
     */
    public void appendBody(String text) {
        if (!bodyText.isDisposed()) {
            bodyText.setText(bodyText.getText() + text);
            container.getParent().layout(true, true);
        }
    }

    /** Returns the root composite so the containing panel can re-layout. */
    public Composite getContainer() {
        return container;
    }
}
