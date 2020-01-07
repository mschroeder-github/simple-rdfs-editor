package com.github.mschroeder.github.srdfse;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JFrame;

/**
 *
 * @author Markus Schr&ouml;der
 */
public class PreviewDialog extends javax.swing.JDialog {

    private Ontology ontology;
    
    public PreviewDialog(Ontology ontology, java.awt.Frame parent, boolean modal) {
        super(parent, modal);
        this.ontology = ontology;
        initComponents();
        
        setLocationRelativeTo(null);
        
        jTextAreaTTL.setText(ontology.toTTL());
        jTextAreaTTL.setCaretPosition(0);
    }

    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jScrollPane1 = new javax.swing.JScrollPane();
        jTextAreaTTL = new javax.swing.JTextArea();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Preview");

        jTextAreaTTL.setEditable(false);
        jTextAreaTTL.setColumns(20);
        jTextAreaTTL.setFont(new java.awt.Font("DialogInput", 0, 12)); // NOI18N
        jTextAreaTTL.setRows(5);
        jScrollPane1.setViewportView(jTextAreaTTL);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 495, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 401, Short.MAX_VALUE)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents
    
    public static void showGUI(JFrame parent, Ontology ontology) {
        //java.awt.EventQueue.invokeLater(new Runnable() {
        //    public void run() {
        PreviewDialog dialog = new PreviewDialog(ontology, new javax.swing.JFrame(), true);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                dialog.dispose();
            }
        });
        dialog.setVisible(true);
        //    }
        //});
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTextArea jTextAreaTTL;
    // End of variables declaration//GEN-END:variables
}
