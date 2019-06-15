package com.github.mschroeder.github.srdfse;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;

/**
 *
 * @author Markus Schr&ouml;der
 */
public class SwingUtility {

    /**
     * Opens a waiting dialog with indeterminate progress bar.
     *
     * @param <T> return type
     * @param message show a message in the dialog
     * @param owner
     * @param modal
     * @param cancelable adds a cancel button to cancel the processing
     * @param supplier
     * @return returns what the supplier returns.
     */
    public static <T> T wait(String message, Window owner, boolean modal, boolean cancelable, Supplier<T> supplier) {
        final JDialog loading = new JDialog(owner, modal ? JDialog.DEFAULT_MODALITY_TYPE : Dialog.ModalityType.MODELESS);
        loading.setTitle(message);
        JPanel p1 = new JPanel(new BorderLayout());
        JLabel l = new JLabel(message);
        l.setHorizontalAlignment(JLabel.CENTER);
        p1.add(l, BorderLayout.NORTH);
        JProgressBar p = new JProgressBar();
        p.setIndeterminate(true);
        p1.add(p, BorderLayout.CENTER);

        JButton btn = new JButton("Cancel");
        if (cancelable) {
            p1.add(btn, BorderLayout.SOUTH);
        }

        loading.setUndecorated(true);
        loading.getContentPane().add(p1);
        loading.pack();
        loading.setLocationRelativeTo(owner);
        loading.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        loading.setModal(true);

        SwingWorker<T, Object> worker = new SwingWorker<T, Object>() {
            @Override
            protected T doInBackground() throws Exception {
                return supplier.get();
            }

            @Override
            protected void done() {
                loading.dispose();
            }
        };

        if (cancelable) {
            btn.addActionListener((e) -> {
                worker.cancel(true);
            });
        }

        worker.execute();

        loading.setVisible(true);

        try {
            return worker.get();
        } catch (InterruptedException | ExecutionException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static <T> T wait(String message, Window owner, boolean modal, Supplier<T> supplier) {
        return wait(message, owner, modal, false, supplier);
    }

    public static <T, U> List<T> waitList(List<U> inputList, Function<U, List<T>> f, String message, Window owner, boolean modal) {
        final JDialog loading = new JDialog(owner, modal ? JDialog.DEFAULT_MODALITY_TYPE : Dialog.ModalityType.MODELESS);
        loading.setTitle(message);
        JPanel p1 = new JPanel(new BorderLayout());
        JLabel l = new JLabel(message);
        l.setHorizontalAlignment(JLabel.CENTER);
        p1.add(l, BorderLayout.NORTH);
        JProgressBar p = new JProgressBar();
        p.setMaximum(inputList.size());
        p.setStringPainted(true);
        p1.add(p, BorderLayout.CENTER);

        JButton btnCancel = new JButton("Cancel");
        p1.add(btnCancel, BorderLayout.SOUTH);

        loading.setUndecorated(true);
        loading.getContentPane().add(p1);
        loading.pack();
        loading.setLocationRelativeTo(owner);
        loading.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        loading.setModal(true);

        //https://stackoverflow.com/questions/20260372/swingworker-progressbar
        //to cancel it
        class CancelObject {

            boolean cancelled;
        }
        CancelObject co = new CancelObject();

        SwingWorker<List<T>, Integer> worker = new SwingWorker<List<T>, Integer>() {
            @Override
            protected void process(List<Integer> chunks) {
                int i = chunks.get(chunks.size() - 1);
                p.setValue(i);
            }

            @Override
            protected List<T> doInBackground() throws Exception {

                List<T> l = new ArrayList<>();

                int i = 1;
                for (U u : inputList) {
                    if (co.cancelled) {
                        break;
                    }

                    List<T> result = f.apply(u);

                    if (result != null) {
                        l.addAll(result);
                    }

                    publish(i++);
                }

                return l;
            }

            @Override
            protected void done() {
                loading.dispose();
            }
        };

        btnCancel.addActionListener((e) -> {
            co.cancelled = true;
            loading.dispose();
        });

        worker.execute();

        loading.setVisible(true);

        try {
            return worker.get();
        } catch (InterruptedException | ExecutionException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Opens message dialog with error icon and throws RuntimeException.
     *
     * @param parent
     * @param throwable
     */
    public static void exception(Component parent, Throwable throwable) {
        JOptionPane.showMessageDialog(parent, throwable.getMessage(), "Exception", JOptionPane.ERROR_MESSAGE);
        throw new RuntimeException(throwable);
    }

    /**
     * Resizes the columns to the contents size.
     *
     * @param table target table
     */
    public static void resizeColumnWidth(JTable table) {
        final TableColumnModel columnModel = table.getColumnModel();
        for (int column = 0; column < table.getColumnCount(); column++) {
            int width = 15; // Min width
            for (int row = 0; row < table.getRowCount(); row++) {
                TableCellRenderer renderer = table.getCellRenderer(row, column);
                Component comp = table.prepareRenderer(renderer, row, column);
                width = Math.max(comp.getPreferredSize().width + 1, width);
            }
            //if (width > 300) {
            //    width = 300;
            //}
            columnModel.getColumn(column).setPreferredWidth(width);
        }
    }

    /**
     * Expands all nodes.
     *
     * @param tree
     */
    public static void expandAll(JTree tree) {
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
    }

    /**
     * Collapses all nodes.
     *
     * @param tree
     */
    public static void collapseAll(JTree tree) {
        for (int i = tree.getRowCount() - 1; i > 0; i--) {
            tree.collapseRow(i);
        }
    }

    /**
     * Expands to the node.
     *
     * @param node
     * @param tree
     */
    public static void expandNode(DefaultMutableTreeNode node, JTree tree) {
        tree.expandPath(new TreePath(node.getPath()));
    }

    public static JMenuItem createMenuItem(String label, ActionListener action) {
        return createMenuItem(label, -1, -1, action);
    }

    public static JMenuItem createMenuItem(String label, int mod, int keycode, ActionListener action) {
        JMenuItem item = new JMenuItem(label);

        if (mod >= 0 && keycode >= 0) {
            item.setAccelerator(javax.swing.KeyStroke.getKeyStroke(keycode, mod));
            //globalActions.put(new Pair<>(mod, keycode), action);
        }

        //item.setToolTipText("test");
        item.addActionListener(action);
        return item;
    }

    /**
     * Walks the tree with path segmented by '/'. Directly start with child in
     * path.
     *
     * @param path if empty returns root, use '/' separator
     * @param tree
     * @return null if not found
     */
    public static DefaultMutableTreeNode getNodeByPath(String path, JTree tree) {
        DefaultMutableTreeNode cur = (DefaultMutableTreeNode) tree.getModel().getRoot();

        if (path.isEmpty()) {
            return cur;
        }

        for (String segment : path.split("/")) {

            boolean found = false;
            for (int i = 0; i < cur.getChildCount(); i++) {

                DefaultMutableTreeNode child = (DefaultMutableTreeNode) cur.getChildAt(i);

                if (child.getUserObject() instanceof String) {
                    String value = (String) child.getUserObject();
                    if (value.equals(segment)) {
                        cur = child;
                        found = true;
                        break;
                    }
                }
            }

            if (!found) {
                return null;
            }
        }

        return cur;
    }

    public static int getIndexOfTitle(JTabbedPane tab, String title) {
        for (int i = 0; i < tab.getTabCount(); i++) {
            if (tab.getTitleAt(i).equals(title)) {
                return i;
            }
        }
        return -1;
    }

    public static DocumentListener getAllListener(Consumer<DocumentEvent> c) {
        return new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                c.accept(e);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                c.accept(e);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                c.accept(e);
            }
        };
    }

    //https://stackoverflow.com/questions/10245220/java-image-resize-maintain-aspect-ratio
    public static Dimension getScaledDimension(Dimension imgSize, Dimension boundary) {

        int original_width = imgSize.width;
        int original_height = imgSize.height;
        int bound_width = boundary.width;
        int bound_height = boundary.height;
        int new_width = original_width;
        int new_height = original_height;

        // first check if we need to scale width
        if (original_width > bound_width) {
            //scale width to fit
            new_width = bound_width;
            //scale height to maintain aspect ratio
            new_height = (new_width * original_height) / original_width;
        }

        // then check if we need to scale even with the new height
        if (new_height > bound_height) {
            //scale height to fit instead
            new_height = bound_height;
            //scale width to maintain aspect ratio
            new_width = (new_height * original_width) / original_height;
        }

        return new Dimension(new_width, new_height);
    }
    
    public static <T> TreeCellRenderer renderTree(Class<T> type, BiConsumer<JLabel, T> renderer) {
        return new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
                JLabel label = (JLabel) super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            
                DefaultMutableTreeNode dmtn = (DefaultMutableTreeNode) value;
                T se = (T) dmtn.getUserObject();
                
                renderer.accept(label, se);
                
                return label;
            }
        };
    }
    
    public static TreeCellRenderer renderTreeNode(BiConsumer<JLabel, DefaultMutableTreeNode> renderer) {
        return new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
                JLabel label = (JLabel) super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
                DefaultMutableTreeNode dmtn = (DefaultMutableTreeNode) value;
                renderer.accept(label, dmtn);
                return label;
            }
        };
    }
}
