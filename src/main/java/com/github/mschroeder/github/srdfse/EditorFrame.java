package com.github.mschroeder.github.srdfse;

import java.awt.Color;
import java.awt.Component;
import static java.awt.EventQueue.invokeLater;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import static java.awt.event.KeyEvent.VK_ENTER;
import static java.awt.event.KeyEvent.VK_TAB;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import static java.util.stream.Collectors.toList;
import javax.imageio.ImageIO;
import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import static javax.swing.TransferHandler.MOVE;
import javax.swing.event.DocumentEvent;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.filechooser.FileFilter;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.text.CaseUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 *
 * @author Markus Schr&ouml;der
 */
public class EditorFrame extends javax.swing.JFrame {

    private List<Ontology> ontologies;

    //one selected (focused) resource
    private Resource.Type selectedType;
    private Resource selected;

    //true in order to have not a recursive listening call between label and localname
    private boolean currentlySyncing;

    private Color notNewColor = new Color(245, 245, 255);
    private Color domainRangeColor = new Color(188, 223, 188);

    private final String TITLE = "Simple RDFS Editor";
    private final String EMPTY = "∅";
    private File file;

    private static Icon ontologyIcon;
    private static Icon classIcon;
    private static Icon propertyIcon;
    private static Icon datatypeIcon;

    {
        try {
            ontologyIcon = new ImageIcon(ImageIO.read(EditorFrame.class.getResourceAsStream("/web/img/ontology.png")));
            classIcon = new ImageIcon(ImageIO.read(EditorFrame.class.getResourceAsStream("/web/img/class.png")));
            propertyIcon = new ImageIcon(ImageIO.read(EditorFrame.class.getResourceAsStream("/web/img/property.png")));
            datatypeIcon = new ImageIcon(ImageIO.read(EditorFrame.class.getResourceAsStream("/web/img/datatype.png")));
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private enum TreeType {
        Domain,
        Property,
        Range
    }

    public EditorFrame() {
        initComponents();
        ontologies = new ArrayList<>();

        DefaultComboBoxModel<String> cbm = (DefaultComboBoxModel<String>) jComboBoxLabelLang.getModel();
        cbm.removeAllElements();
        cbm.addElement("");
        cbm.addElement("en");
        cbm.addElement("de");
        jComboBoxLabelLang.addItemListener((e) -> {
            updateSelected();
        });

        jTextFieldOntoURI.getDocument().addDocumentListener(SwingUtility.getAllListener(c -> {
            getUserOntology().setUri(jTextFieldOntoURI.getText());
            fireEvents(OntologyTreeModel.Modification.Changed, getUserOntology());
        }));
        jTextFieldPrefix.getDocument().addDocumentListener(SwingUtility.getAllListener(c -> {
            getUserOntology().setPrefix(jTextFieldPrefix.getText());
            fireEvents(OntologyTreeModel.Modification.Changed, getUserOntology());
        }));

        jTextFieldLocalname.getDocument().addDocumentListener(SwingUtility.getAllListener(this::localnameChanged));
        jTextFieldLabel.getDocument().addDocumentListener(SwingUtility.getAllListener(this::labelChanged));
        jTextAreaComment.getDocument().addDocumentListener(SwingUtility.getAllListener(this::commentChanged));

        updateTypeLabel(Resource.Type.Class);
        updateSelected();

        initFilechooser();
        initTrees();

        //an empty one which is created by the user
        newOntology();

        //GUI state
        jTextFieldLabel.requestFocus();
        setLocationRelativeTo(null);
    }

    private ActionEvent noevt() {
        return new ActionEvent(this, 0, null);
    }

    private void newOntology() {
        //an empty one which is created by the user
        updateSelected(null);
        updateFile(null);
        Ontology ont = new Ontology();
        loadUserOntology(ont);
    }

    /*package*/ void loadUserOntology(Ontology onto) {
        newButton(Resource.Type.Class, noevt());

        //the one which is created by the user
        ontologies.clear();
        ontologies.add(onto);
        jTextFieldOntoURI.setText(getUserOntology().getUri());
        jTextFieldPrefix.setText(getUserOntology().getPrefix());

        getModels().forEach(m -> m.fireEvent(OntologyTreeModel.Modification.Structure, onto));

        getTrees().forEach(jtree -> SwingUtility.expandAll(jtree));
    }

    /*package*/ void importOntologyFromResource(String resourcePath) {
        String basename = FilenameUtils.getBaseName(resourcePath);

        Model m = ModelFactory.createDefaultModel().read(
                EditorFrame.class.getResourceAsStream(resourcePath),
                null,
                "TTL"
        );

        Ontology onto = new Ontology();
        onto.setPrefix(basename);

        Ontology.load(onto, m);

        importOntology(onto);
    }

    /*package*/ void importOntology(Ontology onto) {
        //no duplicate
        for (int i = 0; i < ontologies.size(); i++) {
            if (ontologies.get(i).getPrefix().equals(onto.getPrefix())) {
                return;
            }
        }

        ontologies.add(onto);
        fireEvents(OntologyTreeModel.Modification.Inserted, onto);

        getTrees().forEach(jtree -> SwingUtility.expandAll(jtree));
    }

    private void initFilechooser() {
        if (!SwingUtilities.isEventDispatchThread()) {
            return;
        }

        jFileChooserLoad.addChoosableFileFilter(new FileFilter() {
            @Override
            public boolean accept(File f) {
                String n = f.getName();
                return f.isDirectory() || n.endsWith("rdf") || n.endsWith("ttl") || n.endsWith("n3") || n.endsWith("jsonld") || n.endsWith("owl");
            }

            @Override
            public String getDescription() {
                return "RDF Serialzations (*.rdf, *.ttl, *.n3, *.jsonld, *.owl)";
            }
        });
        jFileChooserLoad.setFileFilter(jFileChooserLoad.getChoosableFileFilters()[1]);
        jFileChooserLoad.setCurrentDirectory(new File(System.getProperty("user.dir")));

        jFileChooserSave.addChoosableFileFilter(new FileFilter() {
            @Override
            public boolean accept(File f) {
                String n = f.getName();
                return f.isDirectory() || n.endsWith("ttl");
            }

            @Override
            public String getDescription() {
                return "Turtle (*.ttl)";
            }
        });
        jFileChooserSave.setFileFilter(jFileChooserSave.getChoosableFileFilters()[1]);
        jFileChooserSave.setCurrentDirectory(new File(System.getProperty("user.dir")));
    }

    private void initTrees() {
        jTreeDomain.setModel(new OntologyTreeModel(ontologies, Resource.Type.Class, false));
        jTreeProperties.setModel(new OntologyTreeModel(ontologies, Resource.Type.Property, false));
        jTreeRange.setModel(new OntologyTreeModel(ontologies, Resource.Type.Class, true));

        getTrees().forEach(jtree -> SwingUtility.expandAll(jtree));

        TargetSourceTransferHandler handler = new TargetSourceTransferHandler();

        getTrees().forEach(jtree -> {
            jtree.setTransferHandler(handler);
            jtree.setDragEnabled(true);
        });

        getTrees().forEach(jtree -> {
            jtree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        });

        //getTrees().forEach(jtree -> jtree.getSelectionModel().addTreeSelectionListener(this::treeSelected));
        getTrees().forEach(jtree -> jtree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!jtree.isSelectionEmpty()) {
                    treeSelected(new TreeSelectionEvent(jtree, jtree.getSelectionPath(), false, jtree.getSelectionPath(), jtree.getSelectionPath()));
                }
            }
        }));

        OntologyCellRenderer r = new OntologyCellRenderer();
        getTrees().forEach(jtree -> {
            jtree.setCellRenderer(r);
        });
    }

    private Resource getSelectedResource() {
        return selected;
    }

    private class OntologyCellRenderer extends DefaultTreeCellRenderer {

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            JLabel label = (JLabel) super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            label.setOpaque(false);
            if (value instanceof Ontology) {
                render((Ontology) value, label);
            }
            if (value instanceof Resource) {
                render((Resource) value, label, tree);
            }
            return label;
        }

        private void render(Ontology ontology, JLabel label) {
            label.setText(ontology.getPrefix());
            label.setIcon(ontologyIcon);
        }

        private void render(Resource resource, JLabel label, JTree jtree) {
            if (resource.getType() == Resource.Type.Class) {
                label.setText(resource.getLocalname(true));
                label.setIcon(classIcon);
            } else if (resource.getType() == Resource.Type.Property) {
                StringBuilder sb = new StringBuilder();
                sb.append("<html>");
                sb.append(resource.getLocalname(true));

                sb.append("<font color=gray>");

                sb.append(" : ");

                if (resource.hasDomain()) {
                    sb.append(resource.getDomain().getLocalname());
                } else {
                    sb.append(EMPTY);
                }

                sb.append(" - ");

                if (resource.hasRange()) {
                    sb.append(resource.getRange().getLocalname());
                } else {
                    sb.append(EMPTY);
                }
                sb.append("</font>");

                sb.append("</html>");

                label.setText(sb.toString());
                label.setIcon(propertyIcon);
            } else if (resource.getType() == Resource.Type.Datatype) {
                label.setText(resource.getLocalname(true));
                label.setIcon(datatypeIcon);
            }

            if (hasSelected()) {
                Resource property = getSelectedResource();
                if (jtree == jTreeDomain && property.hasDomain() && property.getDomain() == resource) {
                    label.setOpaque(true);
                    label.setBackground(domainRangeColor);
                }
                if (jtree == jTreeRange && property.hasRange() && property.getRange() == resource) {
                    label.setOpaque(true);
                    label.setBackground(domainRangeColor);
                }
            }
        }
    }

    private void treeSelected(TreeSelectionEvent e) {
        JTree jtree = (JTree) e.getSource();
        Object obj = e.getPath().getLastPathComponent();
        if (obj instanceof Resource) {
            updateSelected((Resource) obj);
        } else if (obj instanceof Ontology) {
            Ontology ont = (Ontology) obj;
            if (ont == getUserOntology()) {
                if (jtree == jTreeDomain || jtree == jTreeRange) {
                    newButton(Resource.Type.Class, new ActionEvent(jtree, 0, null));
                } else {
                    newButton(Resource.Type.Property, new ActionEvent(jtree, 0, null));
                }
            }
        }
    }

    /*package*/ Ontology getUserOntology() {
        return ontologies.get(0);
    }

    private boolean hasSelected() {
        return selected != null;
    }

    private void updateTypeLabel() {
        jLabelType.setText(selectedType.toString());
        switch (selectedType) {
            case Class:
                jLabelType.setIcon(classIcon);
                break;
            case Property:
                jLabelType.setIcon(propertyIcon);
                break;
            case Datatype:
                jLabelType.setIcon(datatypeIcon);
                break;
        }
    }

    private void updateTypeLabel(Resource.Type newType) {
        selectedType = newType;
        updateTypeLabel();
    }

    private void updateSelected(Resource toBeSelected) {
        selected = toBeSelected;
        updateSelected();
    }

    private void updateSelected() {
        Color c = Color.white;
        if (!hasSelected()) {
            c = notNewColor;
            jCheckBoxSync.setEnabled(true);
        } else {
            jCheckBoxSync.setEnabled(false);
        }

        jTextFieldLocalname.setBackground(c);
        jTextFieldLabel.setBackground(c);
        jTextAreaComment.setBackground(c);

        jTextFieldLocalname.setEditable(true);
        jTextFieldLabel.setEditable(true);
        jTextAreaComment.setEditable(true);

        if (hasSelected()) {
            jTextFieldLocalname.setText(selected.getLocalname());
            jTextFieldLabel.setText(selected.getLabel().get((String) jComboBoxLabelLang.getSelectedItem()));
            jTextAreaComment.setText(selected.getComment().get((String) jComboBoxLabelLang.getSelectedItem()));

            jTreeDomain.repaint();
            jTreeRange.repaint();

            updateTypeLabel(selected.getType());

            if (selected.getOntology() != getUserOntology()) {
                jTextFieldLocalname.setEditable(false);
                jTextFieldLabel.setEditable(false);
                jTextAreaComment.setEditable(false);
            }
        }
    }

    private void updateFile(File file) {
        this.file = file;
        setTitle((file != null ? (file.getName() + " - ") : "") + TITLE);
    }

    private void createResourceByEnter(KeyEvent evt, boolean needControl) {
        if (evt.getKeyCode() == VK_ENTER && (!needControl || evt.isControlDown()) && !hasSelected()) {

            Resource res = new Resource(getUserOntology(), selectedType);

            //label lang value
            res.setLocalname(jTextFieldLocalname.getText());
            res.getLabel().put((String) jComboBoxLabelLang.getSelectedItem(), jTextFieldLabel.getText());
            res.getComment().put((String) jComboBoxLabelLang.getSelectedItem(), jTextAreaComment.getText());

            if (selectedType == Resource.Type.Class) {
                getUserOntology().getRootClasses().add(res);

                fireEvents(OntologyTreeModel.Modification.Inserted, res);

            } else if (selectedType == Resource.Type.Property) {
                getUserOntology().getRootProperties().add(res);

                fireEvents(OntologyTreeModel.Modification.Inserted, res);
            }

            //expand
            expandPaths(getUserOntology());

            //what do next
            if (!needControl && evt.isControlDown()) {
                //reset
                newButton(selectedType, noevt());
            } else {
                if (jTextFieldLabel.hasFocus()) {
                    jTextAreaComment.requestFocus();
                }
                updateSelected(res);
            }
        }
    }

    private void expandPaths(Object obj) {
        //expand
        getTrees().forEach(jtree -> {
            OntologyTreeModel otm = (OntologyTreeModel) jtree.getModel();
            invokeLater(() -> {
                TreePath tp = otm.toEvent(obj).getTreePath().pathByAddingChild(obj);
                jtree.expandPath(tp);
            });
        });
    }

    private void newButton(Resource.Type type, ActionEvent evt) {
        updateTypeLabel(type);
        updateSelected(null);
        boolean ctrl = (evt.getModifiers() & ActionEvent.CTRL_MASK) == ActionEvent.CTRL_MASK;
        if (!ctrl) {
            jTextFieldLocalname.setText("");
            jTextFieldLabel.setText("");
            jTextAreaComment.setText("");
        }
        jTextFieldLabel.requestFocus();
        jTreeDomain.repaint();
        jTreeRange.repaint();
    }

    private void localnameChanged(DocumentEvent e) {
        if (jCheckBoxSync.isSelected() && !currentlySyncing && !hasSelected()) {
            invokeLater(() -> {
                currentlySyncing = true;
                jTextFieldLabel.setText(localname2label(jTextFieldLocalname.getText()));
                currentlySyncing = false;
            });
        }

        if (hasSelected()) {
            selected.setLocalname(jTextFieldLocalname.getText());
            fireEvents(OntologyTreeModel.Modification.Changed, selected);
        }
    }

    private void labelChanged(DocumentEvent e) {
        if (jCheckBoxSync.isSelected() && !currentlySyncing && !hasSelected()) {
            invokeLater(() -> {
                currentlySyncing = true;
                jTextFieldLocalname.setText(label2localname(jTextFieldLabel.getText()));
                currentlySyncing = false;
            });
        }

        if (hasSelected()) {
            selected.getLabel().put((String) jComboBoxLabelLang.getSelectedItem(), jTextFieldLabel.getText());
            fireEvents(OntologyTreeModel.Modification.Changed, selected);
        }
    }

    private void commentChanged(DocumentEvent e) {
        if (hasSelected()) {
            selected.getComment().put((String) jComboBoxLabelLang.getSelectedItem(), jTextAreaComment.getText());
            fireEvents(OntologyTreeModel.Modification.Changed, selected);
        }
    }

    private String label2localname(String label) {
        return encodeURIComponent(CaseUtils.toCamelCase(label.trim(), selectedType == Resource.Type.Class));
    }

    private String localname2label(String localname) {
        return decodeURIComponent(splitCamelCaseString(localname.trim()));
    }

    public static String splitCamelCaseString(String s) {
        LinkedList<String> result = new LinkedList<String>();
        for (String w : s.split("(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])")) {
            result.add(w);
        }
        return result.stream().collect(Collectors.joining(" ")).replaceAll("\\s+", " ");
    }

    //https://stackoverflow.com/a/14424783
    private static String encodeURIComponent(String s) {
        String result;

        try {
            result = URLEncoder.encode(s, "UTF-8")
                    .replaceAll("\\+", "%20")
                    .replaceAll("\\%21", "!")
                    .replaceAll("\\%27", "'")
                    .replaceAll("\\%28", "(")
                    .replaceAll("\\%29", ")")
                    .replaceAll("\\%7E", "~");
        } catch (UnsupportedEncodingException e) {
            result = s;
        }

        return result;
    }

    private static String decodeURIComponent(String s) {
        String result;

        try {
            result = URLDecoder.decode(s, "UTF-8");
            //TODO
            //.replaceAll("\\+", "%20")
            //.replaceAll("\\%21", "!")
            //.replaceAll("\\%27", "'")
            //.replaceAll("\\%28", "(")
            //.replaceAll("\\%29", ")")
            //.replaceAll("\\%7E", "~");
        } catch (UnsupportedEncodingException e) {
            result = s;
        }

        return result;
    }

    private JTree getTree(TreeType t) {
        switch (t) {
            case Domain:
                return jTreeDomain;
            case Property:
                return jTreeProperties;
            case Range:
                return jTreeRange;
        }
        return null;
    }

    private List<JTree> getTrees() {
        return Arrays.asList(
                getTree(TreeType.Domain),
                getTree(TreeType.Property),
                getTree(TreeType.Range)
        );
    }

    /*
    private DefaultTreeModel getDTM(TreeType t) {
        return (DefaultTreeModel) getTree(t).getModel();
    }
     */
    private OntologyTreeModel getModel(TreeType t) {
        return (OntologyTreeModel) getTree(t).getModel();
    }

    private List<OntologyTreeModel> getModels() {
        return Arrays.asList(
                getModel(TreeType.Domain),
                getModel(TreeType.Property),
                getModel(TreeType.Range)
        );
    }

    private List<OntologyTreeModel> getModels(Resource.Type type) {
        if (type == Resource.Type.Class) {
            return Arrays.asList(
                    getModel(TreeType.Domain),
                    getModel(TreeType.Range)
            );
        }

        return Arrays.asList(
                getModel(TreeType.Property)
        );
    }

    private void fireEvents(OntologyTreeModel.Modification mod, Object obj) {
        if (obj instanceof Ontology || (obj instanceof Resource && ((Resource) obj).getType() == Resource.Type.Class)) {
            getModel(TreeType.Domain).fireEvent(mod, obj);
            getModel(TreeType.Range).fireEvent(mod, obj);
        }
        if (obj instanceof Ontology || (obj instanceof Resource && ((Resource) obj).getType() == Resource.Type.Property)) {
            getModel(TreeType.Property).fireEvent(mod, obj);
        }
    }

    private void fireEvent(JTree tree, OntologyTreeModel.Modification mod, TreeModelEvent e) {
        ((OntologyTreeModel) tree.getModel()).fireEvent(mod, e);
    }

    private DataFlavor createFlavor(Class clazz) {
        String mimeType = DataFlavor.javaJVMLocalObjectMimeType + ";class=\"" + clazz.getName() + "\"";
        try {
            return new DataFlavor(mimeType);
        } catch (ClassNotFoundException ex) {
            throw new RuntimeException(ex);
        }
    }

    private class TreeTransfer {

        JTree jtree;
        TreePath[] selected;

        public TreeTransfer(JTree jtree, TreePath[] selected) {
            this.jtree = jtree;
            this.selected = selected;
        }
    }

    private DataFlavor treeTransferFlavor = createFlavor(TreeTransfer.class);

    private class TargetSourceTransferHandler extends TransferHandler {

        @Override
        public int getSourceActions(JComponent c) {
            return MOVE;
        }

        @Override
        protected Transferable createTransferable(JComponent c) {
            //has to be here to get selection before importData
            JTree tree = (JTree) c;
            TreePath[] selected = tree.getSelectionPaths();

            return new Transferable() {
                @Override
                public DataFlavor[] getTransferDataFlavors() {
                    return new DataFlavor[]{treeTransferFlavor};
                }

                @Override
                public boolean isDataFlavorSupported(DataFlavor flavor) {
                    return treeTransferFlavor.equals(flavor);
                }

                @Override
                public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
                    if (!isDataFlavorSupported(flavor)) {
                        throw new UnsupportedFlavorException(flavor);
                    }
                    return new TreeTransfer(tree, selected);
                }
            };
        }

        @Override
        public boolean canImport(TransferHandler.TransferSupport support) {
            return support.isDataFlavorSupported(treeTransferFlavor);
        }

        @Override
        public boolean importData(TransferHandler.TransferSupport support) {
            Transferable t = support.getTransferable();
            //System.out.println("importData " + t);

            Object obj;
            try {
                obj = t.getTransferData(treeTransferFlavor);
            } catch (UnsupportedFlavorException | IOException ex) {
                throw new RuntimeException(ex);
            }

            TreeTransfer source = (TreeTransfer) obj;

            JTree targetTree = (JTree) support.getComponent();
            TreePath target = targetTree.getSelectionPath();

            dragAndDrop(source.jtree, source.selected, targetTree, target);

            //expand
            SwingUtility.expandNode((DefaultMutableTreeNode) target.getLastPathComponent(), targetTree);

            return true;
        }

    }

    //called by TargetSourceTransferHandler.importData
    private void dragAndDrop(JTree sourceTree, TreePath[] sources, JTree targetTree, TreePath target) {
        //System.out.println("src: " + Arrays.toString(sources));
        //System.out.println("target: " + target);

        for (TreePath source : sources) {
            Object src = source.getLastPathComponent();
            Object trg = target.getLastPathComponent();

            dragAndDrop(sourceTree, src, targetTree, trg, true);
        }
    }

    //called by tree-based and Server-based dragAndDrop
    //src and trg can be Resource or Ontology instance
    private void dragAndDrop(JTree sourceTree, Object src, JTree targetTree, Object trg, boolean guiEvents) {
        //property creation
        if (sourceTree == jTreeDomain && targetTree == jTreeRange
                && src instanceof Resource && trg instanceof Resource) {
            Resource domain = (Resource) src;
            Resource range = (Resource) trg;

            Resource property = new Resource(getUserOntology(), Resource.Type.Property);
            property.setLocalname("has" + range.getLocalname());
            property.getLabel().put((String) jComboBoxLabelLang.getSelectedItem(), "has " + range.getLocalname());

            property.setDomain(domain);
            property.setRange(range);

            getUserOntology().getRootProperties().add(property);
            
            if(guiEvents) {
                fireEvents(OntologyTreeModel.Modification.Inserted, property);
                expandPaths(getUserOntology());
                updateSelected(property);
            }
            
            return;
        }

        //set domain
        if (sourceTree == jTreeDomain && targetTree == jTreeProperties
                && src instanceof Resource && trg instanceof Resource) {
            Resource domain = (Resource) src;
            Resource property = (Resource) trg;
            if (property.getOntology() == getUserOntology()) {
                property.setDomain(domain);
                if(guiEvents) {
                    updateSelected(property);
                }
            }
        }

        //set range
        if (sourceTree == jTreeRange && targetTree == jTreeProperties
                && src instanceof Resource && trg instanceof Resource) {
            Resource range = (Resource) src;
            Resource property = (Resource) trg;
            if (property.getOntology() == getUserOntology()) {
                property.setRange(range);
                if(guiEvents) {
                    updateSelected(property);
                }
            }
        }

        if (src == trg || src instanceof Ontology) {
            return;
        }

        Resource srcR = (Resource) src;

        //resource -> ontology (maybe import)
        //target can only be user ontology
        if (trg instanceof Ontology && trg == getUserOntology() && sourceTree == targetTree) {
            Ontology trgO = (Ontology) trg;
            boolean sameOnto = trgO == srcR.getOntology();

            if (sameOnto) {
                removeResource(srcR, guiEvents);
            } else {
                //it's an import (copy resource)
                srcR = srcR.copyOnlyRef();
            }

            trgO.getRoot(srcR.getType()).add(srcR);
            
            if(guiEvents) {
                fireEvents(OntologyTreeModel.Modification.Inserted, srcR);
                expandPaths(srcR);
            }
            
        } //target is a resource
        else if (trg instanceof Resource
                && (((Resource) trg).getOntology() == getUserOntology() || ((Resource) trg).isImported())
                && sourceTree == targetTree) {
            Resource trgR = (Resource) trg;

            removeResource(srcR, guiEvents);

            trgR.addChild(srcR);
            
            if(guiEvents) {
                fireEvents(OntologyTreeModel.Modification.Inserted, srcR);
            }
        }

        if(guiEvents) {
            expandPaths(trg);
        }
    }

    //called by Server Websocket messageDragAndDrop
    /*package*/ void dragAndDrop(String srcTreeType, int srcHashCode, String dstTreeType, int dstHashCode) {
        JTree sourceTree = getTree(TreeType.valueOf(srcTreeType));
        JTree targetTree = getTree(TreeType.valueOf(dstTreeType));
        
        Object src = getObjectByHashCode(srcHashCode);
        if(src == null)
            throw new RuntimeException("No src object found for hashCode " + srcHashCode);
        
        Object dst = getObjectByHashCode(dstHashCode);
        if(dst == null)
            throw new RuntimeException("No dst object found for hashCode " + dstHashCode);
        
        dragAndDrop(sourceTree, src, targetTree, dst, false);
    }
    
    /*package*/ Object getObjectByHashCode(int hashCode) {
        for(Ontology onto : ontologies) {
            if(onto.hashCode() == hashCode)
                return onto;
            
            Resource res = onto.findByHashCode(hashCode);
            if(res != null)
                return res;
        }
        return null;
    }
    
    /* package */ void removeOntology(Ontology onto) {
        ontologies.remove(onto);
    }

    private void removeResource(Resource srcR, boolean guiEvents) {
        List<OntologyTreeModel> sourceModels = getModels(srcR.getType());

        //create it before manipulation
        List<TreeModelEvent> events = sourceModels.stream().map(sm -> sm.toEvent(srcR)).collect(toList());

        if (srcR.hasParent()) {
            srcR.getParent().removeChild(srcR);
        } else {
            Ontology onto = srcR.isImported() ? getUserOntology() : srcR.getOntology();

            onto.getRoot(srcR.getType()).remove(srcR);
        }

        //because we can have two: in case of 'class' we have domain and range
        if(guiEvents) {
            for (int i = 0; i < events.size(); i++) {
                sourceModels.get(i).fireEvent(OntologyTreeModel.Modification.Removed, events.get(i));
            }
        }
    }

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();

        JSONArray ontoArray = new JSONArray();
        int index = 0;
        for (Ontology ontology : ontologies) {
            JSONObject onto = ontology.toJSON(index);
            onto.put("isUser", index == 0);
            onto.put("index", index++);
            ontoArray.put(onto);
        }
        json.put("ontologies", ontoArray);

        return json;
    }

    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jFileChooserLoad = new javax.swing.JFileChooser();
        jFileChooserSave = new javax.swing.JFileChooser();
        jPanelTop = new javax.swing.JPanel();
        jTextFieldOntoURI = new javax.swing.JTextField();
        jButtonNewClass = new javax.swing.JButton();
        jButtonNewProp = new javax.swing.JButton();
        jLabelOntoURI = new javax.swing.JLabel();
        jTextFieldLocalname = new javax.swing.JTextField();
        jComboBoxLabelLang = new javax.swing.JComboBox<>();
        jLabelLocalname = new javax.swing.JLabel();
        jLabelLabel = new javax.swing.JLabel();
        jTextFieldLabel = new javax.swing.JTextField();
        jLabelComment = new javax.swing.JLabel();
        jScrollPaneComment = new javax.swing.JScrollPane();
        jTextAreaComment = new javax.swing.JTextArea();
        jLabelType = new javax.swing.JLabel();
        jCheckBoxSync = new javax.swing.JCheckBox();
        jLabel1 = new javax.swing.JLabel();
        jTextFieldPrefix = new javax.swing.JTextField();
        jPanelBottom = new javax.swing.JPanel();
        jPanelDomain = new javax.swing.JPanel();
        jLabelDomain = new javax.swing.JLabel();
        jTextFieldDomainFilter = new javax.swing.JTextField();
        jScrollPaneDomain = new javax.swing.JScrollPane();
        jTreeDomain = new javax.swing.JTree();
        jPanelProperties = new javax.swing.JPanel();
        jLabelProperties = new javax.swing.JLabel();
        jTextFieldPropertiesFilter = new javax.swing.JTextField();
        jScrollPaneProperties = new javax.swing.JScrollPane();
        jTreeProperties = new javax.swing.JTree();
        jPanelRange = new javax.swing.JPanel();
        jLabelRange = new javax.swing.JLabel();
        jTextFieldRangeFilter = new javax.swing.JTextField();
        jScrollPaneRange = new javax.swing.JScrollPane();
        jTreeRange = new javax.swing.JTree();
        jMenuBarMain = new javax.swing.JMenuBar();
        jMenuFile = new javax.swing.JMenu();
        jMenuItemNew = new javax.swing.JMenuItem();
        jMenuItemLoad = new javax.swing.JMenuItem();
        jMenuItemSave = new javax.swing.JMenuItem();
        jSeparator1 = new javax.swing.JPopupMenu.Separator();
        jMenuItemQuit = new javax.swing.JMenuItem();
        jMenuEdit = new javax.swing.JMenu();
        jMenuItemNewClass = new javax.swing.JMenuItem();
        jMenuItemNewProp = new javax.swing.JMenuItem();
        jSeparator3 = new javax.swing.JPopupMenu.Separator();
        jMenuItemResetDomain = new javax.swing.JMenuItem();
        jMenuItemResetRange = new javax.swing.JMenuItem();
        jMenuImport = new javax.swing.JMenu();
        jMenuItemImportFromFile = new javax.swing.JMenuItem();
        jSeparator2 = new javax.swing.JPopupMenu.Separator();
        jMenuItemXSD = new javax.swing.JMenuItem();
        jMenuItemFOAF = new javax.swing.JMenuItem();
        jMenuItemDCT = new javax.swing.JMenuItem();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("Simple RDFS Editor");

        jButtonNewClass.setText("New Class");
        jButtonNewClass.setMargin(new java.awt.Insets(2, 2, 2, 2));
        jButtonNewClass.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonNewClassActionPerformed(evt);
            }
        });

        jButtonNewProp.setText("New Prop");
        jButtonNewProp.setMargin(new java.awt.Insets(2, 2, 2, 2));
        jButtonNewProp.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonNewPropActionPerformed(evt);
            }
        });

        jLabelOntoURI.setText("Onto. URI");

        jTextFieldLocalname.setNextFocusableComponent(jLabelLabel);
        jTextFieldLocalname.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                jTextFieldLocalnameKeyPressed(evt);
            }
        });

        jComboBoxLabelLang.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "en", "de" }));

        jLabelLocalname.setText("Localname");

        jLabelLabel.setText("Label");

        jTextFieldLabel.setNextFocusableComponent(jTextAreaComment);
        jTextFieldLabel.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                jTextFieldLabelKeyPressed(evt);
            }
        });

        jLabelComment.setText("Comment");

        jTextAreaComment.setColumns(20);
        jTextAreaComment.setLineWrap(true);
        jTextAreaComment.setRows(5);
        jTextAreaComment.setWrapStyleWord(true);
        jTextAreaComment.setNextFocusableComponent(jLabelLocalname);
        jTextAreaComment.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                jTextAreaCommentKeyPressed(evt);
            }
        });
        jScrollPaneComment.setViewportView(jTextAreaComment);

        jLabelType.setText(" ");

        jCheckBoxSync.setSelected(true);
        jCheckBoxSync.setText("Sync");

        jLabel1.setText("Prefix");

        javax.swing.GroupLayout jPanelTopLayout = new javax.swing.GroupLayout(jPanelTop);
        jPanelTop.setLayout(jPanelTopLayout);
        jPanelTopLayout.setHorizontalGroup(
            jPanelTopLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelTopLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelTopLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanelTopLayout.createSequentialGroup()
                        .addGroup(jPanelTopLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanelTopLayout.createSequentialGroup()
                                .addGroup(jPanelTopLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(jLabelComment)
                                    .addComponent(jComboBoxLabelLang, 0, 72, Short.MAX_VALUE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(jScrollPaneComment, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(jPanelTopLayout.createSequentialGroup()
                                .addGroup(jPanelTopLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(jLabelLocalname)
                                    .addComponent(jLabelLabel))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(jPanelTopLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(jTextFieldLabel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 243, Short.MAX_VALUE)
                                    .addComponent(jTextFieldLocalname, javax.swing.GroupLayout.Alignment.TRAILING)
                                    .addGroup(jPanelTopLayout.createSequentialGroup()
                                        .addComponent(jTextFieldPrefix, javax.swing.GroupLayout.PREFERRED_SIZE, 56, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(jLabelOntoURI))))
                            .addGroup(jPanelTopLayout.createSequentialGroup()
                                .addComponent(jLabelType, javax.swing.GroupLayout.PREFERRED_SIZE, 142, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jButtonNewClass, javax.swing.GroupLayout.PREFERRED_SIZE, 84, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jButtonNewProp, javax.swing.GroupLayout.PREFERRED_SIZE, 84, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jCheckBoxSync)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(jPanelTopLayout.createSequentialGroup()
                        .addComponent(jLabel1)
                        .addGap(194, 194, 194)
                        .addComponent(jTextFieldOntoURI)))
                .addContainerGap())
        );
        jPanelTopLayout.setVerticalGroup(
            jPanelTopLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelTopLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelTopLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanelTopLayout.createSequentialGroup()
                        .addComponent(jTextFieldOntoURI, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanelTopLayout.createSequentialGroup()
                        .addGroup(jPanelTopLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel1)
                            .addComponent(jTextFieldPrefix, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabelOntoURI))
                        .addGap(18, 18, 18)))
                .addGroup(jPanelTopLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButtonNewProp)
                    .addComponent(jButtonNewClass)
                    .addComponent(jLabelType, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(10, 10, 10)
                .addGroup(jPanelTopLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(jPanelTopLayout.createSequentialGroup()
                        .addGroup(jPanelTopLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jTextFieldLocalname, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabelLocalname))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGroup(jPanelTopLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabelLabel)
                            .addComponent(jTextFieldLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addComponent(jCheckBoxSync, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 44, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanelTopLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanelTopLayout.createSequentialGroup()
                        .addComponent(jLabelComment)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jComboBoxLabelLang, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(jScrollPaneComment, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanelBottom.setLayout(new java.awt.GridLayout(1, 3));

        jLabelDomain.setText("Domain");

        javax.swing.tree.DefaultMutableTreeNode treeNode1 = new javax.swing.tree.DefaultMutableTreeNode("root");
        jTreeDomain.setModel(new javax.swing.tree.DefaultTreeModel(treeNode1));
        jTreeDomain.setRootVisible(false);
        jTreeDomain.setShowsRootHandles(true);
        jTreeDomain.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                jTreeKeyPressed(evt);
            }
        });
        jScrollPaneDomain.setViewportView(jTreeDomain);

        javax.swing.GroupLayout jPanelDomainLayout = new javax.swing.GroupLayout(jPanelDomain);
        jPanelDomain.setLayout(jPanelDomainLayout);
        jPanelDomainLayout.setHorizontalGroup(
            jPanelDomainLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jLabelDomain, javax.swing.GroupLayout.DEFAULT_SIZE, 228, Short.MAX_VALUE)
            .addComponent(jTextFieldDomainFilter)
            .addComponent(jScrollPaneDomain)
        );
        jPanelDomainLayout.setVerticalGroup(
            jPanelDomainLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelDomainLayout.createSequentialGroup()
                .addComponent(jLabelDomain)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jTextFieldDomainFilter, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPaneDomain, javax.swing.GroupLayout.DEFAULT_SIZE, 191, Short.MAX_VALUE)
                .addGap(0, 0, 0))
        );

        jPanelBottom.add(jPanelDomain);

        jLabelProperties.setText("Properties");

        treeNode1 = new javax.swing.tree.DefaultMutableTreeNode("root");
        jTreeProperties.setModel(new javax.swing.tree.DefaultTreeModel(treeNode1));
        jTreeProperties.setRootVisible(false);
        jTreeProperties.setShowsRootHandles(true);
        jTreeProperties.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                jTreeKeyPressed(evt);
            }
        });
        jScrollPaneProperties.setViewportView(jTreeProperties);

        javax.swing.GroupLayout jPanelPropertiesLayout = new javax.swing.GroupLayout(jPanelProperties);
        jPanelProperties.setLayout(jPanelPropertiesLayout);
        jPanelPropertiesLayout.setHorizontalGroup(
            jPanelPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jLabelProperties, javax.swing.GroupLayout.DEFAULT_SIZE, 228, Short.MAX_VALUE)
            .addComponent(jTextFieldPropertiesFilter)
            .addComponent(jScrollPaneProperties)
        );
        jPanelPropertiesLayout.setVerticalGroup(
            jPanelPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelPropertiesLayout.createSequentialGroup()
                .addComponent(jLabelProperties)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jTextFieldPropertiesFilter, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPaneProperties, javax.swing.GroupLayout.DEFAULT_SIZE, 191, Short.MAX_VALUE))
        );

        jPanelBottom.add(jPanelProperties);

        jLabelRange.setText("Range");

        treeNode1 = new javax.swing.tree.DefaultMutableTreeNode("root");
        jTreeRange.setModel(new javax.swing.tree.DefaultTreeModel(treeNode1));
        jTreeRange.setRootVisible(false);
        jTreeRange.setShowsRootHandles(true);
        jTreeRange.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                jTreeKeyPressed(evt);
            }
        });
        jScrollPaneRange.setViewportView(jTreeRange);

        javax.swing.GroupLayout jPanelRangeLayout = new javax.swing.GroupLayout(jPanelRange);
        jPanelRange.setLayout(jPanelRangeLayout);
        jPanelRangeLayout.setHorizontalGroup(
            jPanelRangeLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jLabelRange, javax.swing.GroupLayout.DEFAULT_SIZE, 228, Short.MAX_VALUE)
            .addComponent(jTextFieldRangeFilter)
            .addComponent(jScrollPaneRange)
        );
        jPanelRangeLayout.setVerticalGroup(
            jPanelRangeLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelRangeLayout.createSequentialGroup()
                .addComponent(jLabelRange)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jTextFieldRangeFilter, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPaneRange, javax.swing.GroupLayout.DEFAULT_SIZE, 191, Short.MAX_VALUE))
        );

        jPanelBottom.add(jPanelRange);

        jMenuFile.setText("File");

        jMenuItemNew.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_N, java.awt.event.InputEvent.CTRL_MASK));
        jMenuItemNew.setText("New");
        jMenuItemNew.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemNewActionPerformed(evt);
            }
        });
        jMenuFile.add(jMenuItemNew);

        jMenuItemLoad.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_O, java.awt.event.InputEvent.CTRL_MASK));
        jMenuItemLoad.setText("Open");
        jMenuItemLoad.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemLoadActionPerformed(evt);
            }
        });
        jMenuFile.add(jMenuItemLoad);

        jMenuItemSave.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, java.awt.event.InputEvent.CTRL_MASK));
        jMenuItemSave.setText("Save");
        jMenuItemSave.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemSaveActionPerformed(evt);
            }
        });
        jMenuFile.add(jMenuItemSave);
        jMenuFile.add(jSeparator1);

        jMenuItemQuit.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Q, java.awt.event.InputEvent.CTRL_MASK));
        jMenuItemQuit.setText("Quit");
        jMenuItemQuit.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemQuitActionPerformed(evt);
            }
        });
        jMenuFile.add(jMenuItemQuit);

        jMenuBarMain.add(jMenuFile);

        jMenuEdit.setText("Edit");

        jMenuItemNewClass.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_K, java.awt.event.InputEvent.CTRL_MASK));
        jMenuItemNewClass.setIcon(new javax.swing.ImageIcon(getClass().getResource("/web/img/class.png"))); // NOI18N
        jMenuItemNewClass.setText("New Class");
        jMenuItemNewClass.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemNewClassActionPerformed(evt);
            }
        });
        jMenuEdit.add(jMenuItemNewClass);

        jMenuItemNewProp.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_B, java.awt.event.InputEvent.CTRL_MASK));
        jMenuItemNewProp.setIcon(new javax.swing.ImageIcon(getClass().getResource("/web/img/property.png"))); // NOI18N
        jMenuItemNewProp.setText("New Property");
        jMenuItemNewProp.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemNewPropActionPerformed(evt);
            }
        });
        jMenuEdit.add(jMenuItemNewProp);
        jMenuEdit.add(jSeparator3);

        jMenuItemResetDomain.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_D, java.awt.event.InputEvent.CTRL_MASK));
        jMenuItemResetDomain.setText("Reset Domain");
        jMenuItemResetDomain.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemResetDomainActionPerformed(evt);
            }
        });
        jMenuEdit.add(jMenuItemResetDomain);

        jMenuItemResetRange.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_R, java.awt.event.InputEvent.CTRL_MASK));
        jMenuItemResetRange.setText("Reset Range");
        jMenuItemResetRange.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemResetRangeActionPerformed(evt);
            }
        });
        jMenuEdit.add(jMenuItemResetRange);

        jMenuBarMain.add(jMenuEdit);

        jMenuImport.setText("Import");

        jMenuItemImportFromFile.setText("From File ...");
        jMenuItemImportFromFile.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemImportFromFileActionPerformed(evt);
            }
        });
        jMenuImport.add(jMenuItemImportFromFile);
        jMenuImport.add(jSeparator2);

        jMenuItemXSD.setText("XSD");
        jMenuItemXSD.setToolTipText("XML Schema Definition");
        jMenuItemXSD.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemXSDActionPerformed(evt);
            }
        });
        jMenuImport.add(jMenuItemXSD);

        jMenuItemFOAF.setText("FOAF");
        jMenuItemFOAF.setToolTipText("Friend of a Friend");
        jMenuItemFOAF.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemFOAFActionPerformed(evt);
            }
        });
        jMenuImport.add(jMenuItemFOAF);

        jMenuItemDCT.setText("DCT");
        jMenuItemDCT.setToolTipText("DC Terms");
        jMenuItemDCT.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemDCTActionPerformed(evt);
            }
        });
        jMenuImport.add(jMenuItemDCT);

        jMenuBarMain.add(jMenuImport);

        setJMenuBar(jMenuBarMain);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanelTop, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(jPanelBottom, javax.swing.GroupLayout.DEFAULT_SIZE, 685, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jPanelTop, javax.swing.GroupLayout.PREFERRED_SIZE, 217, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanelBottom, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void jButtonNewClassActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonNewClassActionPerformed
        newButton(Resource.Type.Class, evt);
    }//GEN-LAST:event_jButtonNewClassActionPerformed

    private void jButtonNewPropActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonNewPropActionPerformed
        newButton(Resource.Type.Property, evt);
    }//GEN-LAST:event_jButtonNewPropActionPerformed

    private void jTextFieldLocalnameKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_jTextFieldLocalnameKeyPressed
        createResourceByEnter(evt, false);
    }//GEN-LAST:event_jTextFieldLocalnameKeyPressed

    private void jTextFieldLabelKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_jTextFieldLabelKeyPressed
        createResourceByEnter(evt, false);
    }//GEN-LAST:event_jTextFieldLabelKeyPressed

    private void jTextAreaCommentKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_jTextAreaCommentKeyPressed
        if (evt.getKeyCode() == VK_TAB) {
            evt.consume();
            jTextAreaComment.transferFocus();
            return;
        }

        createResourceByEnter(evt, true);
    }//GEN-LAST:event_jTextAreaCommentKeyPressed

    private void jMenuItemNewClassActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemNewClassActionPerformed
        newButton(Resource.Type.Class, noevt());
    }//GEN-LAST:event_jMenuItemNewClassActionPerformed

    private void jMenuItemNewPropActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemNewPropActionPerformed
        newButton(Resource.Type.Property, noevt());
    }//GEN-LAST:event_jMenuItemNewPropActionPerformed

    private void jMenuItemLoadActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemLoadActionPerformed
        if (jFileChooserLoad.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = jFileChooserLoad.getSelectedFile();
            Ontology onto = Ontology.load(f);
            loadUserOntology(onto);
            updateFile(f);
        }
    }//GEN-LAST:event_jMenuItemLoadActionPerformed

    private void jMenuItemNewActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemNewActionPerformed
        newOntology();
    }//GEN-LAST:event_jMenuItemNewActionPerformed

    private void jMenuItemQuitActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemQuitActionPerformed
        this.dispose();
    }//GEN-LAST:event_jMenuItemQuitActionPerformed

    private void jMenuItemSaveActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemSaveActionPerformed
        if (file != null) {
            getUserOntology().save(file);
        } else {
            //suggest
            jFileChooserSave.setSelectedFile(new File("./" + getUserOntology().getPrefix() + ".ttl"));
            //ask if ok
            if (jFileChooserSave.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                File file = jFileChooserSave.getSelectedFile();
                //if ext is missing: append
                if (!file.getName().endsWith("ttl")) {
                    file = new File(file.getAbsolutePath() + ".ttl");
                }
                updateFile(file);
                getUserOntology().save(file);
            }
        }
    }//GEN-LAST:event_jMenuItemSaveActionPerformed

    private void jMenuItemXSDActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemXSDActionPerformed
        importOntologyFromResource("/vocab/xsd.ttl");
    }//GEN-LAST:event_jMenuItemXSDActionPerformed

    private void jMenuItemFOAFActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemFOAFActionPerformed
        importOntologyFromResource("/vocab/foaf.ttl");
    }//GEN-LAST:event_jMenuItemFOAFActionPerformed

    private void jMenuItemDCTActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemDCTActionPerformed
        importOntologyFromResource("/vocab/dcterms.ttl");
    }//GEN-LAST:event_jMenuItemDCTActionPerformed

    private void jTreeKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_jTreeKeyPressed
        if (evt.getKeyCode() == KeyEvent.VK_DELETE) {
            JTree jtree = (JTree) evt.getSource();
            TreePath selected = jtree.getSelectionPath();
            if (selected != null) {
                Object obj = selected.getLastPathComponent();
                if (obj instanceof Resource) {
                    Resource res = (Resource) obj;
                    if (res == this.selected) {
                        newButton(res.getType(), noevt());
                    }
                    if (res.getOntology() == getUserOntology() || res.isImported()) {
                        removeResource(res, true);
                    }
                } else if (obj instanceof Ontology && obj != getUserOntology()) {
                    fireEvents(OntologyTreeModel.Modification.Removed, obj);
                    ontologies.remove((Ontology) obj);
                }
            }
        }
    }//GEN-LAST:event_jTreeKeyPressed

    private void jMenuItemResetDomainActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemResetDomainActionPerformed
        if (hasSelected() && selected.getType() == Resource.Type.Property) {
            selected.setDomain(null);
            updateSelected();
        }
    }//GEN-LAST:event_jMenuItemResetDomainActionPerformed

    private void jMenuItemResetRangeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemResetRangeActionPerformed
        if (hasSelected() && selected.getType() == Resource.Type.Property) {
            selected.setRange(null);
            updateSelected();
        }
    }//GEN-LAST:event_jMenuItemResetRangeActionPerformed

    private void jMenuItemImportFromFileActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemImportFromFileActionPerformed
        if (jFileChooserLoad.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = jFileChooserLoad.getSelectedFile();
            Ontology onto = Ontology.load(f);
            importOntology(onto);
        }
    }//GEN-LAST:event_jMenuItemImportFromFileActionPerformed

    public static void showGUI(String args[]) {
        java.awt.EventQueue.invokeLater(() -> {
            new EditorFrame().setVisible(true);
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton jButtonNewClass;
    private javax.swing.JButton jButtonNewProp;
    private javax.swing.JCheckBox jCheckBoxSync;
    private javax.swing.JComboBox<String> jComboBoxLabelLang;
    private javax.swing.JFileChooser jFileChooserLoad;
    private javax.swing.JFileChooser jFileChooserSave;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabelComment;
    private javax.swing.JLabel jLabelDomain;
    private javax.swing.JLabel jLabelLabel;
    private javax.swing.JLabel jLabelLocalname;
    private javax.swing.JLabel jLabelOntoURI;
    private javax.swing.JLabel jLabelProperties;
    private javax.swing.JLabel jLabelRange;
    private javax.swing.JLabel jLabelType;
    private javax.swing.JMenuBar jMenuBarMain;
    private javax.swing.JMenu jMenuEdit;
    private javax.swing.JMenu jMenuFile;
    private javax.swing.JMenu jMenuImport;
    private javax.swing.JMenuItem jMenuItemDCT;
    private javax.swing.JMenuItem jMenuItemFOAF;
    private javax.swing.JMenuItem jMenuItemImportFromFile;
    private javax.swing.JMenuItem jMenuItemLoad;
    private javax.swing.JMenuItem jMenuItemNew;
    private javax.swing.JMenuItem jMenuItemNewClass;
    private javax.swing.JMenuItem jMenuItemNewProp;
    private javax.swing.JMenuItem jMenuItemQuit;
    private javax.swing.JMenuItem jMenuItemResetDomain;
    private javax.swing.JMenuItem jMenuItemResetRange;
    private javax.swing.JMenuItem jMenuItemSave;
    private javax.swing.JMenuItem jMenuItemXSD;
    private javax.swing.JPanel jPanelBottom;
    private javax.swing.JPanel jPanelDomain;
    private javax.swing.JPanel jPanelProperties;
    private javax.swing.JPanel jPanelRange;
    private javax.swing.JPanel jPanelTop;
    private javax.swing.JScrollPane jScrollPaneComment;
    private javax.swing.JScrollPane jScrollPaneDomain;
    private javax.swing.JScrollPane jScrollPaneProperties;
    private javax.swing.JScrollPane jScrollPaneRange;
    private javax.swing.JPopupMenu.Separator jSeparator1;
    private javax.swing.JPopupMenu.Separator jSeparator2;
    private javax.swing.JPopupMenu.Separator jSeparator3;
    private javax.swing.JTextArea jTextAreaComment;
    private javax.swing.JTextField jTextFieldDomainFilter;
    private javax.swing.JTextField jTextFieldLabel;
    private javax.swing.JTextField jTextFieldLocalname;
    private javax.swing.JTextField jTextFieldOntoURI;
    private javax.swing.JTextField jTextFieldPrefix;
    private javax.swing.JTextField jTextFieldPropertiesFilter;
    private javax.swing.JTextField jTextFieldRangeFilter;
    private javax.swing.JTree jTreeDomain;
    private javax.swing.JTree jTreeProperties;
    private javax.swing.JTree jTreeRange;
    // End of variables declaration//GEN-END:variables
}
