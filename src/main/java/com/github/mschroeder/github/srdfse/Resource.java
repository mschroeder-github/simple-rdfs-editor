package com.github.mschroeder.github.srdfse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.XSD;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 *
 * @author Markus Schr&ouml;der
 */
public class Resource implements Comparable<Resource> {

    @Override
    public int compareTo(Resource o) {
        if(localname == null || o.getLocalname() == null)
            return 0;
        
        return localname.compareToIgnoreCase(o.getLocalname());
    }
    
    public enum Type {
        Class,
        Property,
        Datatype,
        Instance,
        Literal
    }
    
    private Type type;
    
    private String localname;
    private LangString label;
    private LangString comment;
    
    private Ontology ontology;
    private Resource parent;
    private List<Resource> children;
    
    private List<Resource> instances; //for class resource having instances
    private Set<Link> links; //for property assertions
    
    private Resource domain;
    private Resource range;
    private List<Statement> additionalDomainRangeStatements;
    
    
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
        this.instances = new ArrayList<>();
        this.links = new HashSet<>();
        this.additionalDomainRangeStatements = new ArrayList<>();
    }
    
    /**
     * Use this to refer to a localname.
     * This will be marked as imported.
     * @param ontology
     * @param type
     * @param localname 
     */
    public Resource(Ontology ontology, Type type, String localname) {
        this(ontology, type);
        this.localname = localname;
        this.imported = true;
    }
    
    public Resource copyOnlyRef() {
        Resource res = new Resource(ontology, type);
        res.setLocalname(localname);
        res.imported = true;
        
        //do not copy domain and range
        //res.domain = domain;
        //res.range = range;
        
        //do not copy literals because this is used to copy from foreign 
        //ontology to user ontology
        
        //main reason: it is already defined in the source ontology,
        //if you would copy everything this would be redundant
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

    public void addInstance(Resource instance) {
        instances.add(instance);
        instance.parent = this;
    }
    
    public void removeInstance(Resource instance) {
        instances.remove(instance);
        instance.parent = null;
    }
    
    public void addLink(Resource source, Resource target) {
        links.add(new Link(source, target));
        
        
    }
    
    public void removeLink(Resource source, Resource target) {
        links.remove(new Link(source, target));
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
    
    //we reuse comment
    public LangString getLiteral() {
        return comment;
    }

    @Override
    public String toString() {
        return label + " " + localname;
    }

    public String getURI() {
        if(type == Type.Instance) {
            return getOntology().getInstanceNamespace() + localname;
        }
        return getOntology().getUriWithFragment() + localname;
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
    
    public boolean isRangeXSD() {
        if(!hasRange())
            return false;
        
        return getRange().getURI().startsWith(XSD.getURI());
    }

    public List<Statement> getAdditionalDomainRangeStatements() {
        return additionalDomainRangeStatements;
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

    public List<Resource> getInstances() {
        return instances;
    }

    public Set<Link> getLinks() {
        return links;
    }
    
    public Set<Link> getLinksOfSubject(Resource subject) {
        return links.stream().filter(l -> l.getSource().equals(subject)).collect(toSet());
    }
    
    public Set<Link> getLinksOfObject(Resource object) {
        return links.stream().filter(l -> l.getTarget().equals(object)).collect(toSet());
    }
    
    public List<Resource> getObjects(Resource subject) {
        return getLinksOfSubject(subject).stream().map(l -> l.getTarget()).collect(toList());
    }
    
    public List<Resource> getSubjects(Resource object) {
        return getLinksOfObject(object).stream().map(l -> l.getSource()).collect(toList());
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
    
    
    public JSONObject toJSON(int ontologyIndex) {
        return toJSON(false, ontologyIndex);
    }
    
    public JSONObject toJSON(boolean recursive, int ontologyIndex) {
        JSONObject resObj = new JSONObject();
        resObj.put("hashCode", hashCode());
        resObj.put("type", type);
        resObj.put("imported", imported);
        resObj.put("ontologyIndex", ontologyIndex);
        if(imported) {
            resObj.put("prefix", ontology.getPrefix());
            resObj.put("uri", ontology.getUri());
        }
        resObj.put("localname", localname);
        resObj.put("label", label.toJSON());
        resObj.put("comment", comment.toJSON());
        
        if(hasParent())
            resObj.put("parent", parent.toJSON(ontologyIndex));
        
        if(hasDomain())
            resObj.put("domain", domain.toJSON(ontologyIndex));
        
        if(hasRange())
            resObj.put("range", range.toJSON(ontologyIndex));
        
        if(recursive && !children.isEmpty()) {
            Collections.sort(children);
            
            JSONArray childrenArray = new JSONArray();
            for(Resource res : children) {
                childrenArray.put(res.toJSON(true, ontologyIndex));
            }
            resObj.put("children", childrenArray);
        } else {
            resObj.put("children", new JSONArray());
        }
        
        return resObj;
    }
    
    public static Resource fromJSON(Ontology ontology, JSONObject resObj) {
        Resource res = new Resource(ontology, Type.valueOf(resObj.getString("type")));
        
        res.localname = resObj.getString("localname");
        
        JSONObject label = resObj.getJSONObject("label");
        for(String key : label.keySet()) {
            res.label.put(key, label.getString(key));
        }
        
        JSONObject comment = resObj.getJSONObject("comment");
        for(String key : comment.keySet()) {
            res.comment.put(key, comment.getString(key));
        }
        
        return res;
    }
    
    public List<Resource> descendants() {
        Queue<Resource> q = new LinkedList<>();
        q.add(this);
        List<Resource> descendants = new ArrayList<>(); 
        while(!q.isEmpty()) {
            Resource r = q.poll();
            descendants.add(r);
            q.addAll(r.children);
        }
        return descendants;
    }
    
    public void change(Resource changeTo, String what, String lang) {
        switch(what) {
            case "localname": this.localname = changeTo.localname; break;
            case "label": this.label.put(lang, changeTo.label.get(lang)); break;
            case "comment": this.comment.put(lang, changeTo.comment.get(lang)); break;
        }
    }
}
