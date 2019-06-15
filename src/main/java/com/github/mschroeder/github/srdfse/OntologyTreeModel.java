package com.github.mschroeder.github.srdfse;

import java.util.ArrayList;
import java.util.List;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

/**
 *
 * @author Markus Schr&ouml;der
 */
public class OntologyTreeModel implements TreeModel {

    private List<TreeModelListener> listeners;

    private List<Ontology> ontologies;
    private Resource.Type type;
    private boolean showDatatype;
    
    private static Object root = new Object();

    public OntologyTreeModel(List<Ontology> ontologies, Resource.Type type, boolean showDatatype) {
        listeners = new ArrayList<>();
        this.ontologies = ontologies;
        this.type = type;
        this.showDatatype = showDatatype;
    }

    @Override
    public Object getRoot() {
        return root;
    }
    
    @Override
    public Object getChild(Object parent, int index) {
        return toList(parent).get(index);
    }

    @Override
    public int getIndexOfChild(Object parent, Object child) {
        return toList(parent).indexOf(child);
    }
    
    @Override
    public int getChildCount(Object parent) {
        return toList(parent).size();
    }

    @Override
    public boolean isLeaf(Object node) {
        return toList(node).isEmpty();
    }
    
    private List toList(Object node) {
        if(node == root) {
            return ontologies;
        }
        
        if(node instanceof Ontology) {
            List l = null;
            if(type == Resource.Type.Class) {
                l = new ArrayList<>(((Ontology)node).getRootClasses());
                
                if(!showDatatype) {
                    l.removeIf(obj -> ((Resource)obj).getType() == Resource.Type.Datatype);
                }
                
            } else if (type == Resource.Type.Property) {
                l =  ((Ontology)node).getRootProperties();
            }
            //Collections.sort(l);
            return l;
        }
        
        if(node instanceof Resource) {
            List l = ((Resource)node).getChildren();
            //Collections.sort(l);
            return l;
        }
        
        throw new RuntimeException("no children for " + node);
    }
    
    public TreeModelEvent toEvent(Object node) {
        //path to parent (with root)
        //child index (from parent)
        //child object (what was modified)
        
        if(node instanceof Ontology) {
            return new TreeModelEvent(this, new Object[] { root }, new int[] { ontologies.indexOf(node) }, new Object[] { node });
        }
        
        if(node instanceof Resource) {
            Resource res = (Resource) node;
            
            List parentPath = res.getParentPathWithoutThis();
            boolean isRoot = parentPath.isEmpty();
            
            Ontology ontology = res.isImported() ? ontologies.get(0) : res.getOntology();
            
            parentPath.add(0, ontology);
            parentPath.add(0, root);
            
            if(!isRoot) {
                Resource parent = (Resource) parentPath.get(parentPath.size()-1);
                return new TreeModelEvent(this, parentPath.toArray(), new int[] { parent.getChildren().indexOf(node) }, new Object[] { node });
            } else {
                List l = type == Resource.Type.Class ? ontology.getRootClasses() : ontology.getRootProperties();
                return new TreeModelEvent(this, parentPath.toArray(), new int[] { l.indexOf(node) }, new Object[] { node });
            }
        }
        
        throw new RuntimeException("no event for " + node);
    }

    
    @Override
    public void valueForPathChanged(TreePath path, Object newValue) {
        
    }


    @Override
    public void addTreeModelListener(TreeModelListener l) {
        listeners.add(l);
    }

    @Override
    public void removeTreeModelListener(TreeModelListener l) {
        listeners.remove(l);
    }

    
    public enum Modification {
        Changed,
        Inserted,
        Removed,
        Structure
    }
    
    public void fireEvent(Modification mod, Object node) {
        TreeModelEvent e = toEvent(node);
        //System.out.println(e);
        fireEvent(mod, e);
    }
    
    public void fireEvent(Modification mod, TreeModelEvent e) {
        listeners.forEach(l -> { 
            switch(mod) {
                case Changed: l.treeNodesChanged(e); break;
                case Inserted: l.treeNodesInserted(e); break;
                case Removed: l.treeNodesRemoved(e); break;
                case Structure: l.treeStructureChanged(e); break;
            }
        });
    }
    
}
