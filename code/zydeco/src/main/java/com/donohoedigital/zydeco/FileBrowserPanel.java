package com.donohoedigital.zydeco;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.io.File;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class FileBrowserPanel extends JPanel
{
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter
            .ofLocalizedDateTime(FormatStyle.SHORT)
            .withLocale(Locale.getDefault())
            .withZone(ZoneId.systemDefault());

    private final FileTableModel tableModel = new FileTableModel();

    public FileBrowserPanel()
    {
        super(new BorderLayout());

        File home = new File(System.getProperty("user.home"));

        JTree tree = buildTree(home);
        JTable table = buildTable();

        JScrollPane treeScroll = new JScrollPane(tree);
        treeScroll.setPreferredSize(new Dimension(220, 0));

        JScrollPane tableScroll = new JScrollPane(table);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScroll, tableScroll);
        split.setDividerLocation(220);
        split.setDividerSize(6);
        split.setContinuousLayout(true);

        add(split, BorderLayout.CENTER);

        // Show home dir contents on startup
        tableModel.setDirectory(home);
    }

    private JTree buildTree(File root)
    {
        FileTreeNode rootNode = new FileTreeNode(root);
        DefaultTreeModel model = new DefaultTreeModel(rootNode);
        rootNode.loadChildren(model);

        JTree tree = buildJTree(model);

        tree.addTreeSelectionListener(e -> {
            TreePath path = e.getNewLeadSelectionPath();
            if (path != null && path.getLastPathComponent() instanceof FileTreeNode node) {
                tableModel.setDirectory(node.file);
            }
        });

        // Lazy-load children when a node is expanded
        tree.addTreeWillExpandListener(new javax.swing.event.TreeWillExpandListener()
        {
            @Override
            public void treeWillExpand(javax.swing.event.TreeExpansionEvent event)
            {
                FileTreeNode node = (FileTreeNode) event.getPath().getLastPathComponent();
                node.loadChildren(model);
            }

            @Override
            public void treeWillCollapse(javax.swing.event.TreeExpansionEvent event) {}
        });

        return tree;
    }

    private JTree buildJTree(DefaultTreeModel model) {
        JTree tree = new JTree(model);
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.expandRow(0);

        tree.setCellRenderer(new DefaultTreeCellRenderer()
        {
            @Override
            public Component getTreeCellRendererComponent(JTree t, Object value, boolean sel,
                                                          boolean expanded, boolean leaf, int row, boolean hasFocus)
            {
                super.getTreeCellRendererComponent(t, value, sel, expanded, leaf, row, hasFocus);
                if (value instanceof FileTreeNode node) {
                    setText(node.file.getName());
                    setIcon(expanded ? Icons.FOLDER_OPEN : Icons.FOLDER);
                }
                return this;
            }
        });
        return tree;
    }

    private JTable buildTable()
    {
        JTable table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.setRowHeight(22);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));

        // Column widths
        table.getColumnModel().getColumn(0).setPreferredWidth(260);
        table.getColumnModel().getColumn(1).setPreferredWidth(80);
        table.getColumnModel().getColumn(2).setPreferredWidth(120);
        table.getColumnModel().getColumn(3).setPreferredWidth(140);

        // Right-align the size column
        DefaultTableCellRenderer rightAlign = new DefaultTableCellRenderer();
        rightAlign.setHorizontalAlignment(SwingConstants.RIGHT);
        table.getColumnModel().getColumn(1).setCellRenderer(rightAlign);

        // Icon + name renderer for the Name column
        table.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer()
        {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int col)
            {
                super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, col);
                File file = tableModel.getFileAt(row);
                if (file != null) {
                    setIcon(file.isDirectory() ? Icons.FOLDER : Icons.FILE);
                    setText(file.getName());
                }
                return this;
            }
        });

        return table;
    }

    // -------------------------------------------------------------------------
    // Tree node
    // -------------------------------------------------------------------------

    private static class FileTreeNode extends DefaultMutableTreeNode
    {
        final File file;
        private boolean loaded = false;

        FileTreeNode(File file)
        {
            super(file);
            this.file = file;
            if (hasSubDirectories(file)) {
                add(new DefaultMutableTreeNode("Loading..."));
            }
        }

        private static boolean hasSubDirectories(File dir)
        {
            File[] children = dir.listFiles(f -> f.isDirectory() && !f.isHidden());
            return children != null && children.length > 0;
        }

        void loadChildren(DefaultTreeModel model)
        {
            if (loaded) return;
            loaded = true;
            removeAllChildren();
            File[] children = file.listFiles(f -> f.isDirectory() && !f.isHidden());
            if (children != null) {
                Arrays.sort(children, Comparator.comparing(f -> f.getName().toLowerCase(Locale.ROOT)));
                for (File child : children) {
                    add(new FileTreeNode(child));
                }
            }
            model.nodeStructureChanged(this);
        }
    }

    // -------------------------------------------------------------------------
    // Table model
    // -------------------------------------------------------------------------

    private static class FileTableModel extends AbstractTableModel
    {
        private static final String[] COLUMNS = {"Name", "Size", "Kind", "Date Modified"};
        private final List<File> files = new ArrayList<>();

        void setDirectory(File dir)
        {
            files.clear();
            File[] listing = dir.listFiles(f -> !f.isHidden());
            if (listing != null) {
                Arrays.sort(listing, Comparator
                        .comparing(File::isDirectory).reversed()
                        .thenComparing(f -> f.getName().toLowerCase(Locale.ROOT)));
                files.addAll(Arrays.asList(listing));
            }
            fireTableDataChanged();
        }

        File getFileAt(int row)
        {
            return (row >= 0 && row < files.size()) ? files.get(row) : null;
        }

        @Override public int getRowCount() { return files.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public Object getValueAt(int row, int col)
        {
            File f = files.get(row);
            return switch (col) {
                case 0 -> f.getName();
                case 1 -> f.isDirectory() ? "--" : formatSize(f.length());
                case 2 -> f.isDirectory() ? "Folder" : getKind(f);
                case 3 -> DATE_FMT.format(Instant.ofEpochMilli(f.lastModified()));
                default -> "";
            };
        }

        private static String formatSize(long bytes)
        {
            if (bytes < 1_024) return bytes + " B";
            if (bytes < 1_048_576) return new DecimalFormat("#.#").format(bytes / 1_024.0) + " KB";
            if (bytes < 1_073_741_824) return new DecimalFormat("#.#").format(bytes / 1_048_576.0) + " MB";
            return new DecimalFormat("#.#").format(bytes / 1_073_741_824.0) + " GB";
        }

        private static String getKind(File f)
        {
            String name = f.getName();
            int dot = name.lastIndexOf('.');
            if (dot < 0) return "Document";
            return name.substring(dot + 1).toUpperCase(Locale.ROOT) + " File";
        }
    }
}
