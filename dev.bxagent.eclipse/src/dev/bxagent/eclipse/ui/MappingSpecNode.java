package dev.bxagent.eclipse.ui;

import java.util.List;

/**
 * A node in the {@link MappingSpecView} tree.
 *
 * <ul>
 *   <li><b>Category node</b> — has children; {@code detail} is {@code null}.
 *       Example: "Type Mappings (3)".</li>
 *   <li><b>Leaf node</b> — no children; {@code detail} holds the pretty-printed
 *       JSON fragment shown in the right-hand panel when selected.</li>
 * </ul>
 */
public final class MappingSpecNode {

    private final String             label;
    private final String             detail;   // null for category nodes
    private final List<MappingSpecNode> children;

    public MappingSpecNode(String label, String detail,
            List<MappingSpecNode> children) {
        this.label    = label;
        this.detail   = detail;
        this.children = children == null ? List.of() : List.copyOf(children);
    }

    public String             getLabel()    { return label; }
    public String             getDetail()   { return detail; }
    public List<MappingSpecNode> getChildren() { return children; }
    public boolean            hasChildren() { return !children.isEmpty(); }

    @Override public String toString() { return label; }
}
