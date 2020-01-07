package com.github.mschroeder.github.srdfse;

import java.awt.Image;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;

/**
 * A frame for {@link EditorPanel}.
 *
 * @author Markus Schr&ouml;der
 */
public class EditorPanelFrame extends javax.swing.JFrame {

    private File file;
    private final String TITLE = "Simple RDFS Editor";

    Image rdfIcon;

    {
        try {
            rdfIcon = ImageIO.read(EditorPanelFrame.class.getResourceAsStream("/web/img/rdf2.png"));
        } catch (IOException ex) {
            throw new RuntimeException("rdf icon", ex);
        }
    }

    public EditorPanelFrame(String[] args) {
        initComponents();
        initFilechooser();
        setTitle(TITLE);
        setIconImage(rdfIcon);
        setLocationRelativeTo(null);

        if (args.length > 0) {
            File f = new File(args[0]);
            if (f.exists()) {
                Ontology onto = Ontology.load(f);
                getEditorPanel().loadUserOntology(onto);
                updateFile(f);
            }
        }
    }

    public EditorPanel getEditorPanel() {
        return (EditorPanel) jPanelEditor;
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

    private void updateFile(File file) {
        this.file = file;
        setTitle((file != null ? (file.getName() + " - ") : "") + TITLE);
    }

    private ActionEvent noevt() {
        return new ActionEvent(this, 0, null);
    }

    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jFileChooserLoad = new javax.swing.JFileChooser();
        jFileChooserSave = new javax.swing.JFileChooser();
        jPanelEditor = new EditorPanel();
        jMenuBarMain = new javax.swing.JMenuBar();
        jMenuFile = new javax.swing.JMenu();
        jMenuItemNew = new javax.swing.JMenuItem();
        jMenuItemLoad = new javax.swing.JMenuItem();
        jMenuItemSave = new javax.swing.JMenuItem();
        jMenuItemPreview = new javax.swing.JMenuItem();
        jSeparator1 = new javax.swing.JPopupMenu.Separator();
        jMenuItemQuit = new javax.swing.JMenuItem();
        jMenuEdit = new javax.swing.JMenu();
        jMenuItemNewClass = new javax.swing.JMenuItem();
        jMenuItemNewProp = new javax.swing.JMenuItem();
        jMenuItemNewInstance = new javax.swing.JMenuItem();
        jSeparator3 = new javax.swing.JPopupMenu.Separator();
        jMenuItemResetDomain = new javax.swing.JMenuItem();
        jMenuItemResetRange = new javax.swing.JMenuItem();
        jMenuItemResetInverseOf = new javax.swing.JMenuItem();
        jMenuImport = new javax.swing.JMenu();
        jMenuItemImportFromFile = new javax.swing.JMenuItem();
        jSeparator2 = new javax.swing.JPopupMenu.Separator();
        jMenuItemXSD = new javax.swing.JMenuItem();
        jMenuItemFOAF = new javax.swing.JMenuItem();
        jMenuItemDCT = new javax.swing.JMenuItem();
        jMenuItemPimoThing = new javax.swing.JMenuItem();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("Simple RDFS Editor");

        jPanelEditor.setLayout(null);
        getContentPane().add(jPanelEditor, java.awt.BorderLayout.CENTER);

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

        jMenuItemPreview.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_W, java.awt.event.InputEvent.CTRL_MASK));
        jMenuItemPreview.setText("Preview");
        jMenuItemPreview.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemPreviewActionPerformed(evt);
            }
        });
        jMenuFile.add(jMenuItemPreview);
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

        jMenuItemNewProp.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_P, java.awt.event.InputEvent.CTRL_MASK));
        jMenuItemNewProp.setIcon(new javax.swing.ImageIcon(getClass().getResource("/web/img/property.png"))); // NOI18N
        jMenuItemNewProp.setText("New Property");
        jMenuItemNewProp.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemNewPropActionPerformed(evt);
            }
        });
        jMenuEdit.add(jMenuItemNewProp);

        jMenuItemNewInstance.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_I, java.awt.event.InputEvent.CTRL_MASK));
        jMenuItemNewInstance.setIcon(new javax.swing.ImageIcon(getClass().getResource("/web/img/instance.png"))); // NOI18N
        jMenuItemNewInstance.setText("New Instance");
        jMenuItemNewInstance.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemNewInstanceActionPerformed(evt);
            }
        });
        jMenuEdit.add(jMenuItemNewInstance);
        jMenuEdit.add(jSeparator3);

        jMenuItemResetDomain.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_D, java.awt.event.InputEvent.CTRL_MASK));
        jMenuItemResetDomain.setIcon(new javax.swing.ImageIcon(getClass().getResource("/web/img/domain.png"))); // NOI18N
        jMenuItemResetDomain.setText("Reset Domain");
        jMenuItemResetDomain.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemResetDomainActionPerformed(evt);
            }
        });
        jMenuEdit.add(jMenuItemResetDomain);

        jMenuItemResetRange.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_R, java.awt.event.InputEvent.CTRL_MASK));
        jMenuItemResetRange.setIcon(new javax.swing.ImageIcon(getClass().getResource("/web/img/range.png"))); // NOI18N
        jMenuItemResetRange.setText("Reset Range");
        jMenuItemResetRange.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemResetRangeActionPerformed(evt);
            }
        });
        jMenuEdit.add(jMenuItemResetRange);

        jMenuItemResetInverseOf.setText("Reset InverseOf");
        jMenuItemResetInverseOf.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemResetInverseOfActionPerformed(evt);
            }
        });
        jMenuEdit.add(jMenuItemResetInverseOf);

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

        jMenuItemPimoThing.setText("PIMO Thing");
        jMenuItemPimoThing.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemPimoThingActionPerformed(evt);
            }
        });
        jMenuImport.add(jMenuItemPimoThing);

        jMenuBarMain.add(jMenuImport);

        setJMenuBar(jMenuBarMain);

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void jMenuItemNewActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemNewActionPerformed
        getEditorPanel().newOntology();
    }//GEN-LAST:event_jMenuItemNewActionPerformed

    private void jMenuItemLoadActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemLoadActionPerformed
        if (jFileChooserLoad.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = jFileChooserLoad.getSelectedFile();
            Ontology onto = Ontology.load(f);
            getEditorPanel().loadUserOntology(onto);
            updateFile(f);
        }
    }//GEN-LAST:event_jMenuItemLoadActionPerformed

    private void jMenuItemSaveActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemSaveActionPerformed
        if (file != null) {
            getEditorPanel().getUserOntology().save(file);
        } else {
            //suggest
            jFileChooserSave.setSelectedFile(new File("./" + getEditorPanel().getUserOntology().getPrefix() + ".ttl"));
            //ask if ok
            if (jFileChooserSave.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                File file = jFileChooserSave.getSelectedFile();
                //if ext is missing: append
                if (!file.getName().endsWith("ttl")) {
                    file = new File(file.getAbsolutePath() + ".ttl");
                }
                updateFile(file);
                getEditorPanel().getUserOntology().save(file);
            }
        }
    }//GEN-LAST:event_jMenuItemSaveActionPerformed

    private void jMenuItemQuitActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemQuitActionPerformed
        this.dispose();
    }//GEN-LAST:event_jMenuItemQuitActionPerformed

    private void jMenuItemNewClassActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemNewClassActionPerformed
        getEditorPanel().newResource(Resource.Type.Class, noevt());
    }//GEN-LAST:event_jMenuItemNewClassActionPerformed

    private void jMenuItemNewPropActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemNewPropActionPerformed
        getEditorPanel().newResource(Resource.Type.Property, noevt());
    }//GEN-LAST:event_jMenuItemNewPropActionPerformed

    private void jMenuItemResetDomainActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemResetDomainActionPerformed
        getEditorPanel().resetDomain();
    }//GEN-LAST:event_jMenuItemResetDomainActionPerformed

    private void jMenuItemResetRangeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemResetRangeActionPerformed
        getEditorPanel().resetRange();
    }//GEN-LAST:event_jMenuItemResetRangeActionPerformed

    private void jMenuItemImportFromFileActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemImportFromFileActionPerformed
        if (jFileChooserLoad.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = jFileChooserLoad.getSelectedFile();
            Ontology onto = Ontology.load(f);
            getEditorPanel().importOntology(onto);
        }
    }//GEN-LAST:event_jMenuItemImportFromFileActionPerformed

    private void jMenuItemXSDActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemXSDActionPerformed
        getEditorPanel().importOntologyFromResource("/vocab/xsd.ttl");
    }//GEN-LAST:event_jMenuItemXSDActionPerformed

    private void jMenuItemFOAFActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemFOAFActionPerformed
        getEditorPanel().importOntologyFromResource("/vocab/foaf.ttl");
    }//GEN-LAST:event_jMenuItemFOAFActionPerformed

    private void jMenuItemDCTActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemDCTActionPerformed
        getEditorPanel().importOntologyFromResource("/vocab/dcterms.ttl");
    }//GEN-LAST:event_jMenuItemDCTActionPerformed

    private void jMenuItemPimoThingActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemPimoThingActionPerformed
        getEditorPanel().importOntologyFromResource("/vocab/thing.ttl");
    }//GEN-LAST:event_jMenuItemPimoThingActionPerformed

    private void jMenuItemNewInstanceActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemNewInstanceActionPerformed
        getEditorPanel().newResource(Resource.Type.Instance, noevt());
    }//GEN-LAST:event_jMenuItemNewInstanceActionPerformed

    private void jMenuItemPreviewActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemPreviewActionPerformed
        PreviewDialog.showGUI(this, getEditorPanel().getUserOntology());
    }//GEN-LAST:event_jMenuItemPreviewActionPerformed

    private void jMenuItemResetInverseOfActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemResetInverseOfActionPerformed
        getEditorPanel().resetInverseOf();
    }//GEN-LAST:event_jMenuItemResetInverseOfActionPerformed

    public static void showGUI(String[] args) {
        java.awt.EventQueue.invokeLater(() -> {
            new EditorPanelFrame(args).setVisible(true);
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JFileChooser jFileChooserLoad;
    private javax.swing.JFileChooser jFileChooserSave;
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
    private javax.swing.JMenuItem jMenuItemNewInstance;
    private javax.swing.JMenuItem jMenuItemNewProp;
    private javax.swing.JMenuItem jMenuItemPimoThing;
    private javax.swing.JMenuItem jMenuItemPreview;
    private javax.swing.JMenuItem jMenuItemQuit;
    private javax.swing.JMenuItem jMenuItemResetDomain;
    private javax.swing.JMenuItem jMenuItemResetInverseOf;
    private javax.swing.JMenuItem jMenuItemResetRange;
    private javax.swing.JMenuItem jMenuItemSave;
    private javax.swing.JMenuItem jMenuItemXSD;
    private javax.swing.JPanel jPanelEditor;
    private javax.swing.JPopupMenu.Separator jSeparator1;
    private javax.swing.JPopupMenu.Separator jSeparator2;
    private javax.swing.JPopupMenu.Separator jSeparator3;
    // End of variables declaration//GEN-END:variables
}
