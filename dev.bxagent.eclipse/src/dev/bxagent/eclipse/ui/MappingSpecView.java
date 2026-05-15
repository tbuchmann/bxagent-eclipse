package dev.bxagent.eclipse.ui;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.ViewPart;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Eclipse view that visualises the {@code TransformationSpec} produced by the
 * LLM after the "Extract Mapping" step.
 *
 * <p>Layout:</p>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  [info label: transformation name / last updated]               │
 * ├──────────────────────────┬──────────────────────────────────────┤
 * │  TreeViewer              │  Detail Text (monospace, read-only)  │
 * │  ▼ Type Mappings (2)     │  {                                   │
 * │     Person → Member      │    "sourceType": "Person",           │
 * │     Company → Org        │    "targetType": "Member",           │
 * │  ▶ Attribute Mappings(5) │    "keyAttributes": ["id"]           │
 * │  ▶ Reference Mappings(1) │  }                                   │
 * │  …                       │                                      │
 * └──────────────────────────┴──────────────────────────────────────┘
 * </pre>
 *
 * <p>Populated by {@link ConversationController} via
 * {@link #populate(String)} after a successful extraction.</p>
 */
public class MappingSpecView extends ViewPart {

    public static final String ID = "dev.bxagent.eclipse.ui.MappingSpecView"; //$NON-NLS-1$

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private Label       infoLabel;
    private TreeViewer  treeViewer;
    private Text        detailText;

    // -----------------------------------------------------------------------
    // ViewPart lifecycle
    // -----------------------------------------------------------------------

    @Override
    public void createPartControl(Composite parent) {
        parent.setLayout(new GridLayout(1, false));

        // -- info bar --------------------------------------------------------
        infoLabel = new Label(parent, SWT.NONE);
        infoLabel.setText("No mapping extracted yet.");
        infoLabel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        // -- sash split -------------------------------------------------------
        SashForm sash = new SashForm(parent, SWT.HORIZONTAL);
        sash.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        // left: tree
        treeViewer = new TreeViewer(sash,
                SWT.SINGLE | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
        treeViewer.setContentProvider(new MappingTreeContent());
        treeViewer.setLabelProvider(new LabelProvider() {
            @Override public String getText(Object element) {
                return element instanceof MappingSpecNode
                        ? ((MappingSpecNode) element).getLabel() : String.valueOf(element);
            }
        });
        treeViewer.addSelectionChangedListener(e -> {
            IStructuredSelection sel = treeViewer.getStructuredSelection();
            if (!sel.isEmpty() && sel.getFirstElement() instanceof MappingSpecNode) {
                MappingSpecNode node = (MappingSpecNode) sel.getFirstElement();
                String d = node.getDetail();
                detailText.setText(d != null ? d : "");
            }
        });

        // right: detail
        detailText = new Text(sash,
                SWT.MULTI | SWT.READ_ONLY | SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);
        detailText.setFont(JFaceResources.getTextFont()); // monospace

        sash.setWeights(40, 60);

        treeViewer.setInput(List.of()); // empty until populate() is called
    }

    @Override
    public void setFocus() {
        treeViewer.getControl().setFocus();
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Parses {@code specJson} and populates the tree.
     * Must be called on the SWT UI thread.
     *
     * @param specJson raw JSON string from {@code BXAgentSession.getSpecJson()}
     */
    public void populate(String specJson) {
        if (specJson == null || specJson.isBlank()) {
            infoLabel.setText("No mapping spec available.");
            treeViewer.setInput(List.of());
            return;
        }
        try {
            List<MappingSpecNode> nodes = buildTree(specJson);
            treeViewer.setInput(nodes);
            treeViewer.expandAll();
            infoLabel.setText("Mapping spec — "
                    + countLeaves(nodes) + " mapping entries across "
                    + nodes.size() + " categories.");
            detailText.setText("");
        } catch (Exception ex) {
            infoLabel.setText("Failed to parse spec: " + ex.getMessage());
            treeViewer.setInput(List.of());
        }
    }

    /**
     * Opens (or activates) the view and returns it.
     * Safe to call from a background thread via {@code Display.asyncExec}.
     */
    public static MappingSpecView show(IWorkbenchPage page) throws PartInitException {
        return (MappingSpecView) page.showView(ID, null,
                IWorkbenchPage.VIEW_VISIBLE);
    }

    // -----------------------------------------------------------------------
    // JSON → tree builder
    // -----------------------------------------------------------------------

    private static List<MappingSpecNode> buildTree(String specJson) throws Exception {
        JsonNode root = MAPPER.readTree(specJson);
        List<MappingSpecNode> result = new ArrayList<>();

        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String    key   = entry.getKey();
            JsonNode  value = entry.getValue();

            if (value.isArray() && value.size() > 0) {
                // category node with one child per array element
                List<MappingSpecNode> children = new ArrayList<>();
                for (JsonNode item : value) {
                    String label  = extractLabel(key, item);
                    String detail = MAPPER.writeValueAsString(item);
                    children.add(new MappingSpecNode(label, detail, List.of()));
                }
                result.add(new MappingSpecNode(
                        humanize(key) + " (" + children.size() + ")",
                        null, children));

            } else if (value.isArray() && value.size() == 0) {
                // skip empty arrays to reduce noise
            } else if (value.isObject()) {
                result.add(new MappingSpecNode(
                        humanize(key),
                        MAPPER.writeValueAsString(value),
                        List.of()));
            } else if (!value.isNull()) {
                // scalar — put at top as a simple leaf
                result.add(new MappingSpecNode(
                        humanize(key) + ": " + value.asText(),
                        null, List.of()));
            }
        }
        return result;
    }

    /**
     * Derives a human-readable label for one element of a mapping array.
     * Falls back to the index-based JSON if no known fields are present.
     */
    private static String extractLabel(String arrayKey, JsonNode item) {
        // Common patterns: sourceType→targetType, source→target, name, etc.
        String src = firstText(item, "sourceType", "sourceClass", "source",
                "sourceAttribute", "sourceReference", "typeMappingName");
        String tgt = firstText(item, "targetType", "targetClass", "target",
                "targetAttribute", "targetReference");
        String name = firstText(item, "name", "transformationName",
                "groupKeyAttribute", "deduplicationKey");

        if (src != null && tgt != null) return src + " → " + tgt;
        if (src != null)                return src;
        if (name != null)               return name;
        // last resort: first string field value
        Iterator<Map.Entry<String, JsonNode>> it = item.fields();
        while (it.hasNext()) {
            JsonNode v = it.next().getValue();
            if (v.isTextual()) return v.asText();
        }
        return "(item)";
    }

    /** Returns the text of the first field whose name matches one of {@code keys}. */
    private static String firstText(JsonNode node, String... keys) {
        for (String k : keys) {
            JsonNode v = node.get(k);
            if (v != null && v.isTextual() && !v.asText().isBlank())
                return v.asText();
        }
        return null;
    }

    /** Converts "typeMappings" → "Type Mappings" etc. */
    private static String humanize(String camel) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < camel.length(); i++) {
            char c = camel.charAt(i);
            if (i == 0) {
                sb.append(Character.toUpperCase(c));
            } else if (Character.isUpperCase(c)) {
                sb.append(' ');
                sb.append(c);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static int countLeaves(List<MappingSpecNode> nodes) {
        int n = 0;
        for (MappingSpecNode node : nodes) {
            if (node.hasChildren()) n += node.getChildren().size();
            else                    n++;
        }
        return n;
    }

    // -----------------------------------------------------------------------
    // TreeViewer content provider
    // -----------------------------------------------------------------------

    private static final class MappingTreeContent implements ITreeContentProvider {

        @Override
        public Object[] getElements(Object input) {
            if (input instanceof List<?>) return ((List<?>) input).toArray();
            return new Object[0];
        }

        @Override
        public Object[] getChildren(Object parent) {
            if (parent instanceof MappingSpecNode) {
                return ((MappingSpecNode) parent).getChildren().toArray();
            }
            return new Object[0];
        }

        @Override
        public Object getParent(Object element) { return null; }

        @Override
        public boolean hasChildren(Object element) {
            return element instanceof MappingSpecNode
                    && ((MappingSpecNode) element).hasChildren();
        }
    }
}
