package dev.bxagent.eclipse.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

import dev.bxagent.eclipse.ui.BXAgentView;

/**
 * Command handler that opens (or brings to front) the BXAgent Chat view.
 * Bound to Ctrl+Shift+B via the {@code org.eclipse.ui.bindings} extension point.
 */
public class OpenBXAgentViewHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IWorkbenchPage page = PlatformUI.getWorkbench()
                .getActiveWorkbenchWindow().getActivePage();
        try {
            page.showView(BXAgentView.ID);
        } catch (PartInitException e) {
            throw new ExecutionException("Could not open BXAgent Chat view", e);
        }
        return null;
    }
}
