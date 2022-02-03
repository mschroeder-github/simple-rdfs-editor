package com.github.mschroeder.github.srdfse;

import java.awt.Color;
import java.awt.Component;
import static java.awt.EventQueue.invokeLater;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.LayoutManager;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import static java.awt.event.KeyEvent.VK_ENTER;
import static java.awt.event.KeyEvent.VK_TAB;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import static java.util.stream.Collectors.toList;
import javax.imageio.ImageIO;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JTree;
import javax.swing.ListModel;
import javax.swing.TransferHandler;
import static javax.swing.TransferHandler.MOVE;
import javax.swing.event.DocumentEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeSelectionEvent;
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
 * The editor gui logic in one panel.
 *
 * @author Markus Schr&ouml;der
 */
public class EditorPanel extends javax.swing.JPanel {

    private List<Ontology> ontologies;

    //one selected (focused) resource
    private Resource.Type selectedType;
    private Resource selected;

    //true in order to have not a recursive listening call between label and localname
    private boolean currentlySyncing;

    //used for drag and drop with alt
    private Object keyboardLock = new Object();
    private boolean isAltPressed = false;
    
    private Color notNewColor = new Color(245, 245, 255);
    private Color domainRangeColor = new Color(188, 223, 188);

    private final String EMPTY = "∅";
    //private File file;

    private static Icon ontologyIcon;
    private static Icon classIcon;
    private static Icon propertyIcon;
    private static Icon datatypeIcon;
    private static Icon instanceIcon;
    private static Icon literalIcon;

    private List<EditorListener> listeners;
    
    private Supplier<String> instanceLocalnameGenerator = () -> {
        //starts with letter
        return "I" + UUID.randomUUID().toString();
    };

    {
        try {
            ontologyIcon = new ImageIcon(ImageIO.read(EditorPanel.class.getResourceAsStream("/web/img/ontology.png")));
            classIcon = new ImageIcon(ImageIO.read(EditorPanel.class.getResourceAsStream("/web/img/class.png")));
            propertyIcon = new ImageIcon(ImageIO.read(EditorPanel.class.getResourceAsStream("/web/img/property.png")));
            datatypeIcon = new ImageIcon(ImageIO.read(EditorPanel.class.getResourceAsStream("/web/img/datatype.png")));
            instanceIcon = new ImageIcon(ImageIO.read(EditorPanel.class.getResourceAsStream("/web/img/instance.png")));
            literalIcon = new ImageIcon(ImageIO.read(EditorPanel.class.getResourceAsStream("/web/img/literal.png")));
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private enum TreeType {
        Domain,
        Property,
        Range
    }

    public EditorPanel() {
        initComponents();
        init();
    }

    @Override
    public void setLayout(LayoutManager mgr) {
        if (mgr == null) {
            return;
        }

        super.setLayout(mgr);
    }

    public void addListener(EditorListener listener) {
        listeners.add(listener);
    }
    
    public void removeListener(EditorListener listener) {
        listeners.remove(listener);
    }
    
    public void newOntology() {
        //an empty one which is created by the user
        updateSelected(null);
        //updateFile(null);
        Ontology ont = new Ontology();
        ont.setPrefix("ex");
        ont.setUri("http://example.com/ex");
        ont.setFragment("/");
        ont.setName("Example");
        ont.setInstanceNamespace("http://example.com/inst#");
        ont.setInstancePrefix("inst");
        loadUserOntology(ont);
    }

    public void loadUserOntology(Ontology onto) {
        newResource(Resource.Type.Class, noevt());

        //the one which is created by the user
        ontologies.clear();
        ontologies.add(onto);
        jTextFieldOntoURI.setText(getUserOntology().getUri());
        jTextFieldPrefix.setText(getUserOntology().getPrefix());
        jTextFieldName.setText(getUserOntology().getName());
        jTextFieldFragment.setText(getUserOntology().getFragment());
        jTextFieldInstNS.setText(getUserOntology().getInstanceNamespace());
        jTextFieldInstPrefix.setText(getUserOntology().getInstancePrefix());

        getModels().forEach(m -> m.fireEvent(OntologyTreeModel.Modification.Structure, onto));

        getTrees().forEach(jtree -> SwingUtility.expandAll(jtree));
    }

    public Ontology importOntologyFromResource(String resourcePath) {
        String basename = FilenameUtils.getBaseName(resourcePath);

        Model m = ModelFactory.createDefaultModel().read(
                EditorPanel.class.getResourceAsStream(resourcePath),
                null,
                "TTL"
        );

        Ontology onto = new Ontology();
        onto.setPrefix(basename);

        Ontology.loadTBox(onto, m);

        return importOntology(onto);
    }

    public Ontology ensureOntologyXSD() {
        for (int i = 0; i < ontologies.size(); i++) {
            if (ontologies.get(i).getPrefix().equals("xsd")) {
                return ontologies.get(i);
            }
        }

        return importOntologyFromResource("/vocab/xsd.ttl");
    }
    
    public Ontology ensureOntologyRDFS() {
        for (int i = 0; i < ontologies.size(); i++) {
            if (ontologies.get(i).getPrefix().equals("rdfs")) {
                return ontologies.get(i);
            }
        }

        return importOntologyFromResource("/vocab/rdfs.ttl");
    }

    public Ontology importOntology(Ontology onto) {
        //no duplicate
        for (int i = 0; i < ontologies.size(); i++) {
            if (ontologies.get(i).getPrefix().equals(onto.getPrefix())) {
                return ontologies.get(i);
            }
        }

        ontologies.add(onto);
        fireEvents(OntologyTreeModel.Modification.Inserted, onto);

        getTrees().forEach(jtree -> SwingUtility.expandAll(jtree));

        return onto;
    }

    public void newResource(Resource.Type type, ActionEvent evt) {
        updateTypeLabel(type);
        updateSelected(null);
        boolean ctrl = (evt.getModifiers() & ActionEvent.CTRL_MASK) == ActionEvent.CTRL_MASK;
        if (!ctrl) {
            jTextFieldLocalname.setText("");
            jTextFieldLabel.setText("");
            jTextAreaComment.setText("");
        }
        if (type == Resource.Type.Instance) {
            jTextFieldLocalname.setText(instanceLocalnameGenerator.get());
        }
        jTextFieldLabel.requestFocus();
        jTreeDomain.repaint();
        jTreeRange.repaint();
    }

    public void resetDomain() {
        if (hasSelected() && selected.getType() == Resource.Type.Property) {
            selected.setDomain(null);
            updateSelected();
        }
    }

    public void resetRange() {
        if (hasSelected() && selected.getType() == Resource.Type.Property) {
            selected.setRange(null);
            updateSelected();
        }
    }

    public void resetInverseOf() {
        Resource property = getSelectedProperty();
        if(property != null) {
            getUserOntology().removeInverseOf(property);
            fireEvents(OntologyTreeModel.Modification.Changed, property);
        }
    }
    
    public Ontology getUserOntology() {
        return ontologies.get(0);
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

    public void setOntoUriEditable(boolean editable) {
        jTextFieldOntoURI.setEditable(editable);
    }

    public Resource createInstance(Resource clazz) {
        Resource inst = new Resource(getUserOntology(), Resource.Type.Instance);
        inst.setLocalname(instanceLocalnameGenerator.get());
        clazz.addInstance(inst);
        return inst;
    }
    
    public Resource createInstance() {
        Resource inst = new Resource(getUserOntology(), Resource.Type.Instance);
        inst.setLocalname(instanceLocalnameGenerator.get());
        return inst;
    }
    
    /**
     * The one that is currently edited.
     * @return 
     */
    public Resource getFocusedResource() {
        return selected;
    }
    
    public Resource getSelectedDomain() {
        return getSelected(TreeType.Domain);
    }

    public Resource getSelectedRange() {
        return getSelected(TreeType.Range);
    }

    public Resource getSelectedProperty() {
        return getSelected(TreeType.Property);
    }

    public Resource getSelectedDomainInstance() {
        return jListDomainInstances.getSelectedValue();
    }

    public String getSelectedLanguage() {
        return (String) jComboBoxLabelLang.getSelectedItem();
    }
    
    public void setInstanceLocalnameGenerator(Supplier<String> instanceLocalnameGenerator) {
        this.instanceLocalnameGenerator = instanceLocalnameGenerator;
    }
    
    private Resource getSelected(TreeType type) {
        TreePath path = getTree(type).getSelectionPath();
        if (path == null) {
            return null;
        }

        Object obj = path.getLastPathComponent();

        if (obj instanceof Resource) {
            return (Resource) obj;
        }

        return null;
    }

    public Resource createResource(Resource.Type type, String localname, String label, String comment, String lang) {
        Resource res = new Resource(getUserOntology(), type);

        //label lang value
        res.setLocalname(localname);
        res.getLabel().put(lang, label);
        res.getComment().put(lang, comment);

        if (type == Resource.Type.Class) {
            getUserOntology().getRootClasses().add(res);

            fireEvents(OntologyTreeModel.Modification.Inserted, res);

        } else if (type == Resource.Type.Property) {
            getUserOntology().getRootProperties().add(res);

            fireEvents(OntologyTreeModel.Modification.Inserted, res);
            
        } else if (type == Resource.Type.Instance) {

            Resource selectedClass = getSelectedDomain();
            if (selectedClass == null) { //|| selectedClass.getOntology() != getUserOntology()) { //only imported ones?
                return null;
            }

            selectedClass.addInstance(res);
        }

        return res;
    }

    
    
    //private
    private void init() {
        listeners = new ArrayList<>();
        
        //to keep track if alt is down
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(new KeyEventDispatcher() {
            @Override
            public boolean dispatchKeyEvent(KeyEvent ke) {
                synchronized (keyboardLock) {
                    switch (ke.getID()) {
                    case KeyEvent.KEY_PRESSED:
                        if (ke.isAltDown()) {
                            isAltPressed = true;
                        }
                        break;

                    case KeyEvent.KEY_RELEASED:
                        if (!ke.isAltDown()) {
                            isAltPressed = false;
                        }
                        break;
                    }
                    return false;
                }
            }
        });
        
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
        jTextFieldName.getDocument().addDocumentListener(SwingUtility.getAllListener(c -> {
            getUserOntology().setName(jTextFieldName.getText());
        }));
        jTextFieldFragment.getDocument().addDocumentListener(SwingUtility.getAllListener(c -> {
            getUserOntology().setFragment(jTextFieldFragment.getText());
        }));
        jTextFieldInstNS.getDocument().addDocumentListener(SwingUtility.getAllListener(c -> {
            getUserOntology().setInstanceNamespace(jTextFieldInstNS.getText());
        }));
        jTextFieldInstPrefix.getDocument().addDocumentListener(SwingUtility.getAllListener(c -> {
            getUserOntology().setInstancePrefix(jTextFieldInstPrefix.getText());
        }));

        jTextFieldLocalname.getDocument().addDocumentListener(SwingUtility.getAllListener(this::localnameChanged));
        jTextFieldLabel.getDocument().addDocumentListener(SwingUtility.getAllListener(this::labelChanged));
        jTextAreaComment.getDocument().addDocumentListener(SwingUtility.getAllListener(this::commentChanged));

        updateTypeLabel(Resource.Type.Class);
        updateSelected();

        //initFilechooser();
        initTrees();
        initLists();

        //an empty one which is created by the user
        newOntology();

        //GUI state
        jTextFieldLabel.requestFocus();
        //setLocationRelativeTo(null);
    }

    private ActionEvent noevt() {
        return new ActionEvent(this, 0, null);
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
                    treeSelected(new TreeSelectionEvent(jtree, jtree.getSelectionPath(), false, jtree.getSelectionPath(), jtree.getSelectionPath()), e.isControlDown());
                }
            }
        }));

        OntologyCellRenderer r = new OntologyCellRenderer();
        getTrees().forEach(jtree -> {
            jtree.setCellRenderer(r);
        });
    }

    private void initLists() {
        getLists().forEach(jlist -> jlist.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!jlist.isSelectionEmpty() && !e.isControlDown() && !e.isShiftDown()) {
                    updateSelected((Resource) jlist.getSelectedValue());
                }
            }
        }));

        InstanceListCellRenderer renderer = new InstanceListCellRenderer();
        getLists().forEach(jlist -> jlist.setCellRenderer(renderer));

        ListSourceTransferHandler src = new ListSourceTransferHandler();
        jListRangeInstances.setTransferHandler(src);
        
        ListTargetTransferHandler trg = new ListTargetTransferHandler();
        jListObjects.setTransferHandler(trg);
        
        
        jListDomainInstances.setModel(new InstanceListModel(TreeType.Domain));
        jListRangeInstances.setModel(new InstanceListModel(TreeType.Range));
        jListObjects.setModel(new InstanceListModel(TreeType.Property));
        
    }

    private DataFlavor listTransferFlavor = createFlavor(ListTransfer.class);

    private class ListTransfer implements Transferable {

        List<Resource> resources;

        public ListTransfer(List<Resource> res) {
            this.resources = res;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{listTransferFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return listTransferFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return resources;
        }
    }
    
    private class ListSourceTransferHandler extends TransferHandler {

        @Override
        public int getSourceActions(JComponent c) {
            return COPY;
        }

        @Override
        protected Transferable createTransferable(JComponent c) {
            List<Resource> selected = jListRangeInstances.getSelectedValuesList();
            return new ListTransfer(selected);
        }
    }

    private class ListTargetTransferHandler extends TransferHandler {

        @Override
        public boolean canImport(TransferHandler.TransferSupport support) {
            return support.isDataFlavorSupported(listTransferFlavor) && 
                   getSelectedDomainInstance() != null && 
                   getSelectedProperty() != null;
        }

        @Override
        public boolean importData(TransferHandler.TransferSupport support) {

            List<Resource> selected;
            try {
                selected = (List<Resource>) support.getTransferable().getTransferData(listTransferFlavor);
            } catch (UnsupportedFlavorException | IOException ex) {
                return false;
            }

            //need selected subject and selected property
            dragAndDropIntoObjectList(selected);

            return true;
        }
    }

    private void dragAndDropIntoObjectList(List<Resource> objects) {
        Resource subject = getSelectedDomainInstance();
        Resource predicate = getSelectedProperty();
        
        if(subject != null && predicate != null) {
            for(Resource object : objects) {
                predicate.addLink(subject, object);
            }
            
            jListObjects.updateUI();
        }
    }

    private class InstanceListModel implements ListModel<Resource> {

        private TreeType type;
        
        public InstanceListModel(TreeType type) {
            this.type = type;
        }
        
        private List<Resource> getList() {
            if(type == TreeType.Property) {
                Resource subject = getSelectedDomainInstance();
                Resource predicate = getSelectedProperty();
                if (subject != null && predicate != null) {
                    return predicate.getObjects(subject);
                }
                return Arrays.asList();
            }
            
            Resource selected = null;
            switch(type) {
                case Domain:   selected = getSelectedDomain(); break;
                case Range:    selected = getSelectedRange();  break;
            }
            
            if(selected != null)
                return selected.getInstances();
            
            return Arrays.asList();
        }
        
        @Override
        public int getSize() {
            return getList().size();
        }

        @Override
        public Resource getElementAt(int index) {
            List<Resource> l = getList();
            
            if(index < 0 || index >= l.size())
                return null;
            
            return l.get(index);
        }

        @Override
        public void addListDataListener(ListDataListener l) {
        }

        @Override
        public void removeListDataListener(ListDataListener l) {
        }
        
    }
    
    private Resource getSelectedResource() {
        return selected;
    }

    private class InstanceListCellRenderer extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            
            Resource res = (Resource) value;
            if(res.getType() == Resource.Type.Literal) {
                label.setText(res.getLiteral().get(""));
                label.setIcon(literalIcon);
                
            } else {
                String lbl = res.getLabel().get(getSelectedLanguage());
                if(lbl == null) {
                    lbl = res.getLocalname();
                }
                label.setText(lbl);
                label.setIcon(instanceIcon);
            }
            
            return label;
        }

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
                
                String extra = "";
                if(!resource.getInstances().isEmpty()) {
                    extra = " (" + resource.getInstances().size() + ")";
                }
                
                label.setText(resource.getLocalname(true) + extra);
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
                
                Resource inv = getUserOntology().getInverseOf(resource);
                if(inv != null) {
                    sb.append(" [");
                    sb.append(inv.getLocalname());
                    sb.append("]");
                }
                
                sb.append("</font>");

                if(!resource.getLinks().isEmpty()) {
                    sb.append(" (" + resource.getLinks().size() + ")");
                }
                
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

    private void treeSelected(TreeSelectionEvent e, boolean controlDown) {
        JTree jtree = (JTree) e.getSource();
        Object obj = e.getPath().getLastPathComponent();
        if (obj instanceof Resource && !controlDown) {
            updateSelected((Resource) obj);
        } else if (obj instanceof Ontology) {
            Ontology ont = (Ontology) obj;
            if (ont == getUserOntology()) {
                if (jtree == jTreeDomain || jtree == jTreeRange) {
                    newResource(Resource.Type.Class, new ActionEvent(jtree, 0, null));
                } else {
                    newResource(Resource.Type.Property, new ActionEvent(jtree, 0, null));
                }
            }
        }
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
            case Instance:
                jLabelType.setIcon(instanceIcon);
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
            jTextFieldLabel.setText(selected.getLabel().get(getSelectedLanguage()));
            jTextAreaComment.setText(selected.getComment().get(getSelectedLanguage()));

            jTreeDomain.repaint();
            jTreeRange.repaint();

            updateTypeLabel(selected.getType());

            if (selected.getOntology() != getUserOntology()) {
                jTextFieldLocalname.setEditable(false);
                jTextFieldLabel.setEditable(false);
                jTextAreaComment.setEditable(false);
            }
            
            listeners.forEach(l -> l.selected(selected));
        }

        updateInstanceList();
    }

    private void updateInstanceList() {
        getLists().forEach(jlist -> jlist.updateUI());
    }

    private void createResourceByEnter(KeyEvent evt, boolean needControl) {
        if (evt.getKeyCode() == VK_ENTER && (!needControl || evt.isControlDown()) && !hasSelected()) {
            createResource(evt, needControl);
        }
    }

    private void createResource(KeyEvent evt, boolean needControl) {

        /*
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
         */
        Resource res = createResource(selectedType, jTextFieldLocalname.getText(), jTextFieldLabel.getText(), jTextAreaComment.getText(), getSelectedLanguage());

        if (res == null) {
            return;
        }

        //expand
        expandPaths(getUserOntology());

        //what do next
        if (!needControl && evt.isControlDown()) {
            //reset
            newResource(selectedType, noevt());
        } else {
            if (jTextFieldLabel.hasFocus()) {
                jTextAreaComment.requestFocus();
            }
            updateSelected(res);
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

    private void localnameChanged(DocumentEvent e) {
        if (selectedType == Resource.Type.Instance) {
            if (hasSelected()) {
                selected.setLocalname(jTextFieldLocalname.getText());
                updateInstanceList();
            }
            return;
        }

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
        if (selectedType == Resource.Type.Instance) {
            if (hasSelected()) {
                selected.getLabel().put(getSelectedLanguage(), jTextFieldLabel.getText());
                updateInstanceList();
            }
            return;
        }

        if (jCheckBoxSync.isSelected() && !currentlySyncing && !hasSelected()) {
            invokeLater(() -> {
                currentlySyncing = true;
                jTextFieldLocalname.setText(label2localname(jTextFieldLabel.getText()));
                currentlySyncing = false;
            });
        }

        if (hasSelected()) {
            selected.getLabel().put(getSelectedLanguage(), jTextFieldLabel.getText());
            fireEvents(OntologyTreeModel.Modification.Changed, selected);
        }
    }

    private void commentChanged(DocumentEvent e) {
        if (hasSelected()) {
            selected.getComment().put(getSelectedLanguage(), jTextAreaComment.getText());
            fireEvents(OntologyTreeModel.Modification.Changed, selected);
        }
    }

    private String label2localname(String label) {
        return label2localname(label, selectedType);
    }

    public static String label2localname(String label, Resource.Type type) {
        return encodeURIComponent(CaseUtils.toCamelCase(label.trim(), type == Resource.Type.Class));
    }

    public static String localname2label(String localname) {
        return decodeURIComponent(splitCamelCaseString(localname.trim()));
    }

    private static String splitCamelCaseString(String s) {
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

    private JList getList(TreeType t) {
        switch (t) {
            case Domain:
                return jListDomainInstances;
            case Property:
                return jListObjects;
            case Range:
                return jListRangeInstances;
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

    private List<JList> getLists() {
        return Arrays.asList(
                getList(TreeType.Domain),
                getList(TreeType.Property),
                getList(TreeType.Range)
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
            property.getLabel().put(getSelectedLanguage(), "has " + range.getLocalname());

            property.setDomain(domain);
            property.setRange(range);

            getUserOntology().getRootProperties().add(property);

            if (guiEvents) {
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

                if (domain.getOntology() != getUserOntology()) {
                    property.setDomain(domain.copyOnlyRef());
                } else {
                    property.setDomain(domain);
                }

                if (guiEvents) {
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

                if (range.getOntology() != getUserOntology()) {
                    property.setRange(range.copyOnlyRef());
                } else {
                    property.setRange(range);
                }

                if (guiEvents) {
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

            if (guiEvents) {
                fireEvents(OntologyTreeModel.Modification.Inserted, srcR);
                expandPaths(srcR);
            }

        } //target is a resource
        else if (trg instanceof Resource
                && (((Resource) trg).getOntology() == getUserOntology() || ((Resource) trg).isImported())
                && sourceTree == targetTree) {
            Resource trgR = (Resource) trg;

            if(isAltPressed) {
                //for properties: inverseOf
                if(srcR.getType() == Resource.Type.Property && trgR.getType() == Resource.Type.Property) {
                    getUserOntology().addInverseOf(srcR, trgR);
                    
                    if (guiEvents) {
                        fireEvents(OntologyTreeModel.Modification.Changed, srcR);
                    }
                }
            } else {
                //as sub *
                removeResource(srcR, guiEvents);
                trgR.addChild(srcR);

                if (guiEvents) {
                    fireEvents(OntologyTreeModel.Modification.Inserted, srcR);
                }
            }
        }

        if (guiEvents) {
            expandPaths(trg);
        }
    }


    //called by Server Websocket messageDragAndDrop
    /*package*/ void dragAndDrop(String srcTreeType, int srcHashCode, String dstTreeType, int dstHashCode) {
        JTree sourceTree = getTree(TreeType.valueOf(srcTreeType));
        JTree targetTree = getTree(TreeType.valueOf(dstTreeType));

        Object src = getObjectByHashCode(srcHashCode);
        if (src == null) {
            throw new RuntimeException("No src object found for hashCode " + srcHashCode);
        }

        Object dst = getObjectByHashCode(dstHashCode);
        if (dst == null) {
            throw new RuntimeException("No dst object found for hashCode " + dstHashCode);
        }

        dragAndDrop(sourceTree, src, targetTree, dst, false);
    }

    /*package*/ Object getObjectByHashCode(int hashCode) {
        for (Ontology onto : ontologies) {
            if (onto.hashCode() == hashCode) {
                return onto;
            }

            Resource res = onto.findByHashCode(hashCode);
            if (res != null) {
                return res;
            }
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

        //because we can have two events: in case of 'class' we have domain and range
        if (guiEvents) {
            for (int i = 0; i < events.size(); i++) {
                sourceModels.get(i).fireEvent(OntologyTreeModel.Modification.Removed, events.get(i));
            }
        }
    }

    private void removeResource(KeyEvent evt) {
        if (evt.getKeyCode() == KeyEvent.VK_DELETE) {
            JTree jtree = (JTree) evt.getSource();
            TreePath selected = jtree.getSelectionPath();
            if (selected != null) {
                Object obj = selected.getLastPathComponent();
                if (obj instanceof Resource) {
                    Resource res = (Resource) obj;
                    //TODO if class remove links having instances
                    if (res == this.selected) {
                        newResource(res.getType(), noevt());
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
    }
    
    private void removeInstances(KeyEvent evt) {
        if (evt.getKeyCode() == KeyEvent.VK_DELETE) {
            JList<Resource> jlist = (JList) evt.getSource();
            if(!jlist.isSelectionEmpty()) {
                for(Resource inst : jlist.getSelectedValuesList()) {
                    inst.getParent().removeInstance(inst);
                    
                    //remove links where resource is part of it
                    getUserOntology().removeLinksHaving(inst);
                }
                jlist.updateUI();
            }
        }
    }
    
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

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
        jLabelName = new javax.swing.JLabel();
        jTextFieldName = new javax.swing.JTextField();
        jTextFieldFragment = new javax.swing.JTextField();
        jButtonNewInstance = new javax.swing.JButton();
        jLabelNamespace = new javax.swing.JLabel();
        jTextFieldInstNS = new javax.swing.JTextField();
        jLabelInstPrefix = new javax.swing.JLabel();
        jTextFieldInstPrefix = new javax.swing.JTextField();
        jSplitPaneTboxAbox = new javax.swing.JSplitPane();
        jPanelTBox = new javax.swing.JPanel();
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
        jPanelABox = new javax.swing.JPanel();
        jPanelSubjects = new javax.swing.JPanel();
        jLabelDomainInstances = new javax.swing.JLabel();
        jScrollPaneDomainInstances = new javax.swing.JScrollPane();
        jListDomainInstances = new javax.swing.JList<>();
        jPanelObjects = new javax.swing.JPanel();
        jLabelObjects = new javax.swing.JLabel();
        jScrollPaneObjects = new javax.swing.JScrollPane();
        jListObjects = new javax.swing.JList<>();
        jPanelRangeInstances = new javax.swing.JPanel();
        jLabelRangeInstances = new javax.swing.JLabel();
        jScrollPaneRangeInstance = new javax.swing.JScrollPane();
        jListRangeInstances = new javax.swing.JList<>();

        jButtonNewClass.setText("C");
        jButtonNewClass.setMargin(new java.awt.Insets(2, 2, 2, 2));
        jButtonNewClass.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonNewClassActionPerformed(evt);
            }
        });

        jButtonNewProp.setText("P");
        jButtonNewProp.setMargin(new java.awt.Insets(2, 2, 2, 2));
        jButtonNewProp.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonNewPropActionPerformed(evt);
            }
        });

        jLabelOntoURI.setText("Onto. URI");

        jTextFieldLocalname.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                jTextFieldLocalnameKeyPressed(evt);
            }
        });

        jComboBoxLabelLang.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "en", "de" }));

        jLabelLocalname.setText("Localname");

        jLabelLabel.setText("Label");

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
        jTextAreaComment.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                jTextAreaCommentKeyPressed(evt);
            }
        });
        jScrollPaneComment.setViewportView(jTextAreaComment);

        jLabelType.setText(" ");

        jCheckBoxSync.setSelected(true);
        jCheckBoxSync.setText("Sync");

        jLabel1.setText("Onto. Prefix");

        jLabelName.setText("Name");

        jTextFieldFragment.setToolTipText("Fragment");

        jButtonNewInstance.setText("I");
        jButtonNewInstance.setMargin(new java.awt.Insets(2, 2, 2, 2));
        jButtonNewInstance.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonNewInstanceActionPerformed(evt);
            }
        });

        jLabelNamespace.setText("Inst. NS");

        jLabelInstPrefix.setText("Inst. Prefix");

        javax.swing.GroupLayout jPanelTopLayout = new javax.swing.GroupLayout(jPanelTop);
        jPanelTop.setLayout(jPanelTopLayout);
        jPanelTopLayout.setHorizontalGroup(
            jPanelTopLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelTopLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelTopLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanelTopLayout.createSequentialGroup()
                        .addGroup(jPanelTopLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabelLocalname)
                            .addComponent(jLabelLabel)
                            .addComponent(jCheckBoxSync))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanelTopLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanelTopLayout.createSequentialGroup()
                                .addGroup(jPanelTopLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(jTextFieldLabel, javax.swing.GroupLayout.Alignment.TRAILING)
                                    .addComponent(jTextFieldLocalname, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 243, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(jPanelTopLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(jLabelOntoURI)
                                    .addComponent(jLabel1)))
                            .addGroup(jPanelTopLayout.createSequentialGroup()
                                .addComponent(jLabelType, javax.swing.GroupLayout.PREFERRED_SIZE, 127, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jButtonNewClass, javax.swing.GroupLayout.PREFERRED_SIZE, 29, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jButtonNewProp, javax.swing.GroupLayout.PREFERRED_SIZE, 31, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jButtonNewInstance, javax.swing.GroupLayout.PREFERRED_SIZE, 31, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jLabelName))))
                    .addGroup(jPanelTopLayout.createSequentialGroup()
                        .addGroup(jPanelTopLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(jLabelComment)
                            .addComponent(jComboBoxLabelLang, javax.swing.GroupLayout.PREFERRED_SIZE, 72, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jScrollPaneComment, javax.swing.GroupLayout.PREFERRED_SIZE, 243, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanelTopLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabelNamespace)
                            .addComponent(jLabelInstPrefix))))
                .addGroup(jPanelTopLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanelTopLayout.createSequentialGroup()
                        .addGap(19, 19, 19)
                        .addGroup(jPanelTopLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jTextFieldName)
                            .addGroup(jPanelTopLayout.createSequentialGroup()
                                .addComponent(jTextFieldOntoURI, javax.swing.GroupLayout.DEFAULT_SIZE, 71, Short.MAX_VALUE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jTextFieldFragment, javax.swing.GroupLayout.PREFERRED_SIZE, 24, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(jTextFieldPrefix)))
                    .addGroup(jPanelTopLayout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(jPanelTopLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jTextFieldInstPrefix, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(jTextFieldInstNS))))
                .addContainerGap())
        );
        jPanelTopLayout.setVerticalGroup(
            jPanelTopLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelTopLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelTopLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButtonNewProp)
                    .addComponent(jButtonNewClass)
                    .addComponent(jCheckBoxSync)
                    .addComponent(jLabelName)
                    .addComponent(jTextFieldName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabelType, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jButtonNewInstance))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanelTopLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jTextFieldLocalname, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabelLocalname)
                    .addComponent(jLabelOntoURI)
                    .addComponent(jTextFieldOntoURI, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jTextFieldFragment, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanelTopLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabelLabel)
                    .addGroup(jPanelTopLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(jLabel1)
                        .addComponent(jTextFieldPrefix, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(jTextFieldLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanelTopLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPaneComment)
                    .addGroup(jPanelTopLayout.createSequentialGroup()
                        .addGroup(jPanelTopLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanelTopLayout.createSequentialGroup()
                                .addComponent(jLabelComment)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jComboBoxLabelLang, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(jPanelTopLayout.createSequentialGroup()
                                .addGroup(jPanelTopLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                    .addComponent(jLabelNamespace)
                                    .addComponent(jTextFieldInstNS, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(jPanelTopLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                    .addComponent(jLabelInstPrefix)
                                    .addComponent(jTextFieldInstPrefix, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );

        jSplitPaneTboxAbox.setDividerLocation(300);
        jSplitPaneTboxAbox.setDividerSize(8);
        jSplitPaneTboxAbox.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
        jSplitPaneTboxAbox.setResizeWeight(1.0);

        jPanelTBox.setLayout(new java.awt.GridLayout(1, 3));

        jLabelDomain.setText("Domain");

        javax.swing.tree.DefaultMutableTreeNode treeNode1 = new javax.swing.tree.DefaultMutableTreeNode("root");
        jTreeDomain.setModel(new javax.swing.tree.DefaultTreeModel(treeNode1));
        jTreeDomain.setRootVisible(false);
        jTreeDomain.setShowsRootHandles(true);
        jTreeDomain.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                jTreeDomainjTreeKeyPressed(evt);
            }
        });
        jScrollPaneDomain.setViewportView(jTreeDomain);

        javax.swing.GroupLayout jPanelDomainLayout = new javax.swing.GroupLayout(jPanelDomain);
        jPanelDomain.setLayout(jPanelDomainLayout);
        jPanelDomainLayout.setHorizontalGroup(
            jPanelDomainLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jLabelDomain, javax.swing.GroupLayout.DEFAULT_SIZE, 188, Short.MAX_VALUE)
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
                .addComponent(jScrollPaneDomain, javax.swing.GroupLayout.DEFAULT_SIZE, 112, Short.MAX_VALUE)
                .addGap(0, 0, 0))
        );

        jPanelTBox.add(jPanelDomain);

        jLabelProperties.setText("Properties");

        treeNode1 = new javax.swing.tree.DefaultMutableTreeNode("root");
        jTreeProperties.setModel(new javax.swing.tree.DefaultTreeModel(treeNode1));
        jTreeProperties.setRootVisible(false);
        jTreeProperties.setShowsRootHandles(true);
        jTreeProperties.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                jTreePropertiesjTreeKeyPressed(evt);
            }
        });
        jScrollPaneProperties.setViewportView(jTreeProperties);

        javax.swing.GroupLayout jPanelPropertiesLayout = new javax.swing.GroupLayout(jPanelProperties);
        jPanelProperties.setLayout(jPanelPropertiesLayout);
        jPanelPropertiesLayout.setHorizontalGroup(
            jPanelPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jLabelProperties, javax.swing.GroupLayout.DEFAULT_SIZE, 188, Short.MAX_VALUE)
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
                .addComponent(jScrollPaneProperties, javax.swing.GroupLayout.DEFAULT_SIZE, 112, Short.MAX_VALUE))
        );

        jPanelTBox.add(jPanelProperties);

        jLabelRange.setText("Range");

        treeNode1 = new javax.swing.tree.DefaultMutableTreeNode("root");
        jTreeRange.setModel(new javax.swing.tree.DefaultTreeModel(treeNode1));
        jTreeRange.setRootVisible(false);
        jTreeRange.setShowsRootHandles(true);
        jTreeRange.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                jTreeRangejTreeKeyPressed(evt);
            }
        });
        jScrollPaneRange.setViewportView(jTreeRange);

        javax.swing.GroupLayout jPanelRangeLayout = new javax.swing.GroupLayout(jPanelRange);
        jPanelRange.setLayout(jPanelRangeLayout);
        jPanelRangeLayout.setHorizontalGroup(
            jPanelRangeLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jLabelRange, javax.swing.GroupLayout.DEFAULT_SIZE, 188, Short.MAX_VALUE)
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
                .addComponent(jScrollPaneRange, javax.swing.GroupLayout.DEFAULT_SIZE, 112, Short.MAX_VALUE))
        );

        jPanelTBox.add(jPanelRange);

        jSplitPaneTboxAbox.setLeftComponent(jPanelTBox);

        jPanelABox.setLayout(new java.awt.GridLayout(1, 3));

        jLabelDomainInstances.setText("Domain Instances");

        jListDomainInstances.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                jListDomainInstancesKeyPressed(evt);
            }
        });
        jScrollPaneDomainInstances.setViewportView(jListDomainInstances);

        javax.swing.GroupLayout jPanelSubjectsLayout = new javax.swing.GroupLayout(jPanelSubjects);
        jPanelSubjects.setLayout(jPanelSubjectsLayout);
        jPanelSubjectsLayout.setHorizontalGroup(
            jPanelSubjectsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jLabelDomainInstances, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(jScrollPaneDomainInstances, javax.swing.GroupLayout.DEFAULT_SIZE, 188, Short.MAX_VALUE)
        );
        jPanelSubjectsLayout.setVerticalGroup(
            jPanelSubjectsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelSubjectsLayout.createSequentialGroup()
                .addComponent(jLabelDomainInstances)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPaneDomainInstances, javax.swing.GroupLayout.DEFAULT_SIZE, 129, Short.MAX_VALUE))
        );

        jPanelABox.add(jPanelSubjects);

        jLabelObjects.setText("Objects");

        jListObjects.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                jListObjectsKeyPressed(evt);
            }
        });
        jScrollPaneObjects.setViewportView(jListObjects);

        javax.swing.GroupLayout jPanelObjectsLayout = new javax.swing.GroupLayout(jPanelObjects);
        jPanelObjects.setLayout(jPanelObjectsLayout);
        jPanelObjectsLayout.setHorizontalGroup(
            jPanelObjectsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jLabelObjects, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(jScrollPaneObjects, javax.swing.GroupLayout.DEFAULT_SIZE, 188, Short.MAX_VALUE)
        );
        jPanelObjectsLayout.setVerticalGroup(
            jPanelObjectsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelObjectsLayout.createSequentialGroup()
                .addComponent(jLabelObjects)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPaneObjects, javax.swing.GroupLayout.DEFAULT_SIZE, 129, Short.MAX_VALUE))
        );

        jPanelABox.add(jPanelObjects);

        jLabelRangeInstances.setText("Range Instances");

        jListRangeInstances.setDragEnabled(true);
        jListRangeInstances.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                jListRangeInstancesKeyPressed(evt);
            }
        });
        jScrollPaneRangeInstance.setViewportView(jListRangeInstances);

        javax.swing.GroupLayout jPanelRangeInstancesLayout = new javax.swing.GroupLayout(jPanelRangeInstances);
        jPanelRangeInstances.setLayout(jPanelRangeInstancesLayout);
        jPanelRangeInstancesLayout.setHorizontalGroup(
            jPanelRangeInstancesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jLabelRangeInstances, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(jScrollPaneRangeInstance, javax.swing.GroupLayout.DEFAULT_SIZE, 188, Short.MAX_VALUE)
        );
        jPanelRangeInstancesLayout.setVerticalGroup(
            jPanelRangeInstancesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanelRangeInstancesLayout.createSequentialGroup()
                .addComponent(jLabelRangeInstances)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPaneRangeInstance, javax.swing.GroupLayout.DEFAULT_SIZE, 129, Short.MAX_VALUE))
        );

        jPanelABox.add(jPanelRangeInstances);

        jSplitPaneTboxAbox.setRightComponent(jPanelABox);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanelTop, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(jSplitPaneTboxAbox, javax.swing.GroupLayout.DEFAULT_SIZE, 566, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jPanelTop, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jSplitPaneTboxAbox))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void jButtonNewClassActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonNewClassActionPerformed
        newResource(Resource.Type.Class, evt);
    }//GEN-LAST:event_jButtonNewClassActionPerformed

    private void jButtonNewPropActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonNewPropActionPerformed
        newResource(Resource.Type.Property, evt);
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

    private void jTreeDomainjTreeKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_jTreeDomainjTreeKeyPressed
        removeResource(evt);
    }//GEN-LAST:event_jTreeDomainjTreeKeyPressed

    private void jTreePropertiesjTreeKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_jTreePropertiesjTreeKeyPressed
        removeResource(evt);
    }//GEN-LAST:event_jTreePropertiesjTreeKeyPressed

    private void jTreeRangejTreeKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_jTreeRangejTreeKeyPressed
        removeResource(evt);
    }//GEN-LAST:event_jTreeRangejTreeKeyPressed

    private void jButtonNewInstanceActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonNewInstanceActionPerformed
        newResource(Resource.Type.Instance, evt);
    }//GEN-LAST:event_jButtonNewInstanceActionPerformed

    private void jListRangeInstancesKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_jListRangeInstancesKeyPressed
        removeInstances(evt);
    }//GEN-LAST:event_jListRangeInstancesKeyPressed

    private void jListObjectsKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_jListObjectsKeyPressed
        if (evt.getKeyCode() == KeyEvent.VK_DELETE) {
            JList jlist = (JList) evt.getSource();
            
            Resource subject = getSelectedDomainInstance();
            Resource predicate = getSelectedProperty();
            
            if(subject != null && predicate != null) {
                
                for(Object obj : jlist.getSelectedValuesList()) {
                    predicate.removeLink(subject, (Resource) obj);
                }
                
                jlist.updateUI();
            }
        }
    }//GEN-LAST:event_jListObjectsKeyPressed

    private void jListDomainInstancesKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_jListDomainInstancesKeyPressed
        removeInstances(evt);
    }//GEN-LAST:event_jListDomainInstancesKeyPressed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton jButtonNewClass;
    private javax.swing.JButton jButtonNewInstance;
    private javax.swing.JButton jButtonNewProp;
    private javax.swing.JCheckBox jCheckBoxSync;
    private javax.swing.JComboBox<String> jComboBoxLabelLang;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabelComment;
    private javax.swing.JLabel jLabelDomain;
    private javax.swing.JLabel jLabelDomainInstances;
    private javax.swing.JLabel jLabelInstPrefix;
    private javax.swing.JLabel jLabelLabel;
    private javax.swing.JLabel jLabelLocalname;
    private javax.swing.JLabel jLabelName;
    private javax.swing.JLabel jLabelNamespace;
    private javax.swing.JLabel jLabelObjects;
    private javax.swing.JLabel jLabelOntoURI;
    private javax.swing.JLabel jLabelProperties;
    private javax.swing.JLabel jLabelRange;
    private javax.swing.JLabel jLabelRangeInstances;
    private javax.swing.JLabel jLabelType;
    private javax.swing.JList<Resource> jListDomainInstances;
    private javax.swing.JList<Resource> jListObjects;
    private javax.swing.JList<Resource> jListRangeInstances;
    private javax.swing.JPanel jPanelABox;
    private javax.swing.JPanel jPanelDomain;
    private javax.swing.JPanel jPanelObjects;
    private javax.swing.JPanel jPanelProperties;
    private javax.swing.JPanel jPanelRange;
    private javax.swing.JPanel jPanelRangeInstances;
    private javax.swing.JPanel jPanelSubjects;
    private javax.swing.JPanel jPanelTBox;
    private javax.swing.JPanel jPanelTop;
    private javax.swing.JScrollPane jScrollPaneComment;
    private javax.swing.JScrollPane jScrollPaneDomain;
    private javax.swing.JScrollPane jScrollPaneDomainInstances;
    private javax.swing.JScrollPane jScrollPaneObjects;
    private javax.swing.JScrollPane jScrollPaneProperties;
    private javax.swing.JScrollPane jScrollPaneRange;
    private javax.swing.JScrollPane jScrollPaneRangeInstance;
    private javax.swing.JSplitPane jSplitPaneTboxAbox;
    private javax.swing.JTextArea jTextAreaComment;
    private javax.swing.JTextField jTextFieldDomainFilter;
    private javax.swing.JTextField jTextFieldFragment;
    private javax.swing.JTextField jTextFieldInstNS;
    private javax.swing.JTextField jTextFieldInstPrefix;
    private javax.swing.JTextField jTextFieldLabel;
    private javax.swing.JTextField jTextFieldLocalname;
    private javax.swing.JTextField jTextFieldName;
    private javax.swing.JTextField jTextFieldOntoURI;
    private javax.swing.JTextField jTextFieldPrefix;
    private javax.swing.JTextField jTextFieldPropertiesFilter;
    private javax.swing.JTextField jTextFieldRangeFilter;
    private javax.swing.JTree jTreeDomain;
    private javax.swing.JTree jTreeProperties;
    private javax.swing.JTree jTreeRange;
    // End of variables declaration//GEN-END:variables
}
