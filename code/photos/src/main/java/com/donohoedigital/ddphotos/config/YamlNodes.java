package com.donohoedigital.ddphotos.config;

import org.snakeyaml.engine.v2.api.StreamDataWriter;
import org.snakeyaml.engine.v2.comments.CommentLine;
import org.snakeyaml.engine.v2.comments.CommentType;
import org.snakeyaml.engine.v2.common.FlowStyle;
import org.snakeyaml.engine.v2.common.ScalarStyle;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;
import org.snakeyaml.engine.v2.nodes.ScalarNode;
import org.snakeyaml.engine.v2.nodes.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Helpers for reading and writing SnakeYAML Engine node trees.
 *
 * <p>The config file classes ({@link AlbumsFile}, {@link PasswordsFile}) keep the parsed node
 * tree around rather than binding to plain maps, so that a load/save cycle preserves comments,
 * blank lines, key order and scalar quoting style.  These helpers are the shared vocabulary for
 * that: read a key, set a key in place (keeping its style and comments), remove a key.
 *
 * <p>Sequence-specific helpers stay on {@link AlbumsFile} — it is the only file with a block
 * sequence, and the emitter workarounds it needs are not generally useful.
 */
final class YamlNodes {

    private YamlNodes() {}

    // ── reading ─────────────────────────────────────────────────────────────

    static MappingNode getMappingNode(MappingNode parent, String key) {
        int idx = findTupleIndex(parent, key);
        if (idx < 0) return null;
        Node v = parent.getValue().get(idx).getValueNode();
        return v instanceof MappingNode mn ? mn : null;
    }

    static String getString(MappingNode node, String key) {
        int idx = findTupleIndex(node, key);
        if (idx < 0) return null;
        Node v = node.getValue().get(idx).getValueNode();
        if (v instanceof ScalarNode sn) {
            String val = sn.getValue();
            return (val == null || val.isEmpty()) ? null : val;
        }
        return null;
    }

    static boolean getBoolean(MappingNode node, String key) {
        return "true".equalsIgnoreCase(getString(node, key));
    }

    static int getInt(MappingNode node, String key) {
        String v = getString(node, key);
        if (v == null) return 0;
        try { return Integer.parseInt(v); } catch (NumberFormatException e) { return 0; }
    }

    // ── writing ─────────────────────────────────────────────────────────────

    static MappingNode ensureMappingNode(MappingNode parent, String key) {
        MappingNode existing = getMappingNode(parent, key);
        if (existing != null) return existing;
        MappingNode node = new MappingNode(Tag.MAP, new ArrayList<>(), FlowStyle.BLOCK);
        parent.getValue().add(new NodeTuple(scalar(key), node));
        return node;
    }

    /**
     * Sets a string value in a mapping node.
     * - Non-blank value: update existing node (preserving style/comments) or add new.
     * - Blank/null value + key exists: remove the key.
     * - Blank/null value + key missing: skip (don't add).
     */
    static void setOptionalString(MappingNode node, String key, String value) {
        int idx = findTupleIndex(node, key);
        if (!isBlank(value)) {
            if (idx >= 0) {
                NodeTuple old = node.getValue().get(idx);
                if (old.getValueNode() instanceof ScalarNode oldVal) {
                    ScalarNode newVal = new ScalarNode(Tag.STR, value, oldVal.getScalarStyle());
                    newVal.setBlockComments(oldVal.getBlockComments());
                    newVal.setInLineComments(oldVal.getInLineComments());
                    newVal.setEndComments(oldVal.getEndComments());
                    node.getValue().set(idx, new NodeTuple(old.getKeyNode(), newVal));
                } else {
                    node.getValue().set(idx, new NodeTuple(old.getKeyNode(), scalar(value)));
                }
            } else {
                node.getValue().add(new NodeTuple(scalar(key), scalar(value)));
            }
        } else if (idx >= 0) {
            removeKey(node, key);
        }
    }

    /**
     * Sets a boolean value in a mapping node, always emitting an explicit
     * "true"/"false" for clarity (updating the existing key or adding it).
     */
    static void setBoolean(MappingNode node, String key, boolean value) {
        int idx = findTupleIndex(node, key);
        ScalarNode boolNode = new ScalarNode(Tag.BOOL, Boolean.toString(value), ScalarStyle.PLAIN);
        if (idx >= 0) {
            node.getValue().set(idx, new NodeTuple(node.getValue().get(idx).getKeyNode(), boolNode));
        } else {
            node.getValue().add(new NodeTuple(scalar(key), boolNode));
        }
    }

    static void setOptionalInt(MappingNode node, String key, int value) {
        if (value == 0) {
            removeKey(node, key);
        } else {
            int idx = findTupleIndex(node, key);
            ScalarNode intNode = new ScalarNode(Tag.INT, Integer.toString(value), ScalarStyle.PLAIN);
            if (idx >= 0) {
                node.getValue().set(idx, new NodeTuple(node.getValue().get(idx).getKeyNode(), intNode));
            } else {
                node.getValue().add(new NodeTuple(scalar(key), intNode));
            }
        }
    }

    static void removeKey(MappingNode node, String key) {
        node.getValue().removeIf(t -> scalarKey(t).equals(key));
    }

    // ── comments ────────────────────────────────────────────────────────────

    /** Prepends a blank-line comment to the node unless it already starts with one. */
    static void ensureLeadingBlankLine(Node keyNode) {
        List<CommentLine> comments = keyNode.getBlockComments();
        comments = (comments == null) ? new ArrayList<>() : new ArrayList<>(comments);
        if (comments.isEmpty() || comments.getFirst().getCommentType() != CommentType.BLANK_LINE) {
            comments.addFirst(new CommentLine(Optional.empty(), Optional.empty(), "", CommentType.BLANK_LINE));
            keyNode.setBlockComments(comments);
        }
    }

    // ── low-level ───────────────────────────────────────────────────────────

    static int findTupleIndex(MappingNode node, String key) {
        List<NodeTuple> tuples = node.getValue();
        for (int i = 0; i < tuples.size(); i++) {
            if (scalarKey(tuples.get(i)).equals(key)) return i;
        }
        return -1;
    }

    static String scalarKey(NodeTuple t) {
        return t.getKeyNode() instanceof ScalarNode sn ? sn.getValue() : "";
    }

    static String scalarValue(NodeTuple t) {
        return t.getValueNode() instanceof ScalarNode sn ? (sn.getValue() != null ? sn.getValue() : "") : "";
    }

    static ScalarNode scalar(String value) {
        return new ScalarNode(Tag.STR, value, ScalarStyle.PLAIN);
    }

    static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** Collects {@code Dump.dumpNode()} output into a String. */
    static final class StringStreamWriter implements StreamDataWriter {
        private final StringBuilder sb = new StringBuilder();

        @Override
        public void write(String str) { sb.append(str); }

        @Override
        public void write(String str, int off, int len) { sb.append(str, off, off + len); }

        @Override
        public String toString() { return sb.toString(); }
    }
}
