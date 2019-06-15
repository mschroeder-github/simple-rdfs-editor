package com.github.mschroeder.github.srdfse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author Markus Schr&ouml;der
 */
public class Resource implements Comparable<Resource> {

    @Override
    public int compareTo(Resource o) {
        if(localname == null || o.getLocalname() == null)
            return 0;
        
        return localname.compareTo(o.getLocalname());
    }
    
    public enum Type {
        Class,
        Property,
        Datatype
    }
    
    private Type type;
    
    private String localname;
    private LangString label;
    private LangString comment;
    
    private Ontology ontology;
    private Resource parent;
    private List<Resource> children;
    
    private Resource domain;
    private Resource range;
    
    private String seeAlso;
    private String isDefinedBy;
    
    private boolean imported;

    public Resource(Ontology ontology, Type type) {
        this.ontology = ontology;
        this.type = type;
        this.localname = "";
        this.label = new LangString();
        this.comment = new LangString();
        this.children = new ArrayList<>();
    }
    
    public Resource copyOnlyRef() {
        Resource res = new Resource(ontology, type);
        res.setLocalname(localname);
        res.imported = true;
        //do not copy literals because this is used to copy from foreign 
        //ontology to user ontology
        return res;
    }
    
    public void addChild(Resource child) {
        children.add(child);
        child.parent = this;
        //Collections.sort(children);
    }
    
    public void removeChild(Resource child) {
        children.remove(child);
        child.parent = null;
    }

    public String getLocalname() {
        return localname;
    }
    
    public String getLocalname(boolean prefixed) {
        if(prefixed && isImported()) {
            return getOntology().getPrefix() + ":" + getLocalname();
        }
        return getLocalname();
    }

    public void setLocalname(String localname) {
        this.localname = localname;
    }

    public LangString getLabel() {
        return label;
    }

    public LangString getComment() {
        return comment;
    }

    @Override
    public String toString() {
        return localname + " '" + label + "'";
    }

    public String getURI() {
        return getOntology().getUri() + localname;
    }

    public Resource getDomain() {
        return domain;
    }

    public void setDomain(Resource domain) {
        this.domain = domain;
    }

    public boolean hasDomain() {
        return domain != null;
    }
    
    public Resource getRange() {
        return range;
    }

    public void setRange(Resource range) {
        this.range = range;
    }

    public boolean hasRange() {
        return range != null;
    }
    
    public String getSeeAlso() {
        return seeAlso;
    }

    public void setSeeAlso(String seeAlso) {
        this.seeAlso = seeAlso;
    }

    public String getIsDefinedBy() {
        return isDefinedBy;
    }

    public void setIsDefinedBy(String isDefinedBy) {
        this.isDefinedBy = isDefinedBy;
    }

    public boolean isImported() {
        return imported;
    }

    public void setImported(boolean imported) {
        this.imported = imported;
    }
    
    /*
    @Override
    public int hashCode() {
        int hash = 7;
        hash = 17 * hash + Objects.hashCode(this.localname);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Resource other = (Resource) obj;
        if (!Objects.equals(this.localname, other.localname)) {
            return false;
        }
        return true;
    }

    */
    
    public Type getType() {
        return type;
    }

    public Resource getParent() {
        return parent;
    }

    public List<Resource> getChildren() {
        return children;
    }
    
    public List<Resource> getParentPathWithoutThis() {
        List<Resource> l = new ArrayList<>();
        //l.add(this);
        Resource p = this.parent;
        while(p != null) {
            l.add(p);
            p = p.parent;
        }
        Collections.reverse(l);
        return l;
    }

    public Ontology getOntology() {
        return ontology;
    }
    
    public boolean hasParent() {
        return parent != null;
    }

    public void setParent(Resource parent) {
        this.parent = parent;
    }
    
}
