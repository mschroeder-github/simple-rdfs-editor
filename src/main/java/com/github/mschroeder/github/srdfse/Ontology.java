package com.github.mschroeder.github.srdfse;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import org.apache.commons.io.FilenameUtils;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 *
 * @author Markus Schr&ouml;der
 */
public class Ontology {

    private String uri;
    private String prefix;
    private String name;

    private List<Resource> rootClasses;
    private List<Resource> rootProperties;

    public Ontology() {
        this.prefix = "ex";
        this.uri = "http://example.com/ex/";

        rootClasses = new ArrayList<>();
        rootProperties = new ArrayList<>();
    }

    public List<Resource> getRoot(Resource.Type type) {
        if (type == Resource.Type.Class) {
            return getRootClasses();
        }
        if (type == Resource.Type.Property) {
            return getRootProperties();
        }

        throw new RuntimeException("no root list for type " + type);
    }

    public List<Resource> getRootClasses() {
        return rootClasses;
    }

    public List<Resource> getRootProperties() {
        return rootProperties;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
    
    @Override
    public String toString() {
        return prefix;
    }

    public static Ontology load(File file) {
        Ontology onto = new Ontology();

        Model m = ModelFactory.createDefaultModel();

        /*
         "RDF/XML", "N-TRIPLE", "TURTLE" (or "TTL") and "N3". null represents the default language, "RDF/XML". 
         "RDF/XML-ABBREV" is a synonym for "RDF/XML".
         */
        StringBuilder sb = new StringBuilder();

        boolean success = false;
        for (String type : Arrays.asList("TURTLE", "N-TRIPLE", "N3", "RDF/XML")) {

            //try read
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(file);
                m.read(fis, null, type);
                success = true;
            } catch (Exception e) {
                sb.append(type + ": ");
                sb.append(e.getMessage());
                sb.append("\n\n");
            } finally {
                try {
                    fis.close();
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }

            if (success) {
                break;
            }
        }

        if (!success) {
            throw new RuntimeException(sb.toString());
        }

        //guess prefix from file name
        onto.setPrefix(FilenameUtils.getBaseName(file.getName()));

        load(onto, m);

        return onto;
    }

    public static Ontology loadTTL(String filename, InputStream is) {
        Ontology onto = new Ontology();

        Model m = ModelFactory.createDefaultModel();
        
        m.read(is, null, "TURTLE");
        
        //guess prefix from file name
        onto.setPrefix(FilenameUtils.getBaseName(filename));

        load(onto, m);

        return onto;
    }
    
    public static void load(Ontology onto, Model m) {
        Map<String, String> prefixMap = m.getNsPrefixMap();
        //try find full uri in file
        String uri = prefixMap.get(onto.getPrefix());
        if (uri != null) {
            onto.setUri(uri);
        }
        Map<String, Ontology> prefix2onto = new HashMap<>();
        for (Entry<String, String> mapping : prefixMap.entrySet()) {
            Ontology other;
            if (mapping.getKey().equals(onto.getPrefix())) {
                other = onto;
            } else {
                other = new Ontology();
                other.setPrefix(mapping.getKey());
                other.setUri(mapping.getValue());
            }
            prefix2onto.put(mapping.getKey(), other);
        }

        Map<org.apache.jena.rdf.model.Resource, Resource> mapped = new HashMap<>();
        Set<Resource> rootClasses = new HashSet<>();
        Set<Resource> rootProps = new HashSet<>();

        addResouces(m, RDFS.Class, Resource.Type.Class, onto, mapped, rootClasses, prefix2onto);
        addResouces(m, RDFS.Datatype, Resource.Type.Datatype, onto, mapped, rootClasses, prefix2onto);
        addResouces(m, RDF.Property, Resource.Type.Property, onto, mapped, rootProps, prefix2onto);

        addRelations(m, RDFS.subClassOf, mapped, rootClasses, SpecialRelation.Sub);
        addRelations(m, RDFS.subPropertyOf, mapped, rootProps, SpecialRelation.Sub);
        addRelations(m, RDFS.domain, mapped, rootProps, SpecialRelation.Domain);
        addRelations(m, RDFS.range, mapped, rootProps, SpecialRelation.Range);

        onto.rootClasses.addAll(rootClasses);
        onto.rootProperties.addAll(rootProps);

        Collections.sort(onto.rootClasses);
        Collections.sort(onto.rootProperties);
    }

    private static void addResouces(Model m, org.apache.jena.rdf.model.Resource typeResource, Resource.Type type, Ontology onto, Map<org.apache.jena.rdf.model.Resource, Resource> mapped, Set<Resource> roots, Map<String, Ontology> prefix2onto) {
        for (org.apache.jena.rdf.model.Resource jena : m.listSubjectsWithProperty(RDF.type, typeResource).toList()) {
            Resource res = toResource(jena, m, onto, type, prefix2onto);
            roots.add(res);
            mapped.put(jena, res);
        }
    }

    private static void addRelations(Model m, Property p, Map<org.apache.jena.rdf.model.Resource, Resource> mapped, Set<Resource> root, SpecialRelation special) {
        for (Statement stmt : m.listStatements(null, p, (RDFNode) null).toList()) {
            if (!stmt.getObject().isURIResource()) {
                continue;
            }

            Resource s = mapped.get(stmt.getSubject());
            Resource o = mapped.get(stmt.getObject().asResource());

            if (s == null || o == null) {
                continue;
            }

            if (special == SpecialRelation.Sub) {
                o.addChild(s);
                root.remove(s);
            } else if (special == SpecialRelation.Domain) {
                s.setDomain(o);
            } else if (special == SpecialRelation.Range) {
                s.setRange(o);
            }
        }
    }

    private static enum SpecialRelation {
        Sub,
        Domain,
        Range
    }

    private static Resource toResource(org.apache.jena.rdf.model.Resource jenaRes, Model model, Ontology onto, Resource.Type type, Map<String, Ontology> prefix2onto) {
        String prefix = model.getNsURIPrefix(jenaRes.getNameSpace());

        Ontology trgOnto = prefix2onto.get(prefix);
        if (trgOnto == null) {
            trgOnto = onto;
        }

        Resource res = new Resource(trgOnto, type);
        res.setImported(trgOnto != onto);

        String localname = jenaRes.getLocalName();
        //sometimes getLocalName does not work; so fix it
        if (localname.isEmpty() && !trgOnto.getUri().isEmpty() && jenaRes.getURI().startsWith(trgOnto.getUri())) {
            localname = jenaRes.getURI().substring(trgOnto.getUri().length());
        }
        res.setLocalname(localname);

        for (Statement stmt : model.listStatements(jenaRes, RDFS.label, (RDFNode) null).toList()) {
            if (!stmt.getObject().isLiteral()) {
                continue;
            }

            Literal lit = stmt.getObject().asLiteral();
            res.getLabel().put(lit.getLanguage(), lit.getLexicalForm());
        }

        for (Statement stmt : model.listStatements(jenaRes, RDFS.comment, (RDFNode) null).toList()) {
            if (!stmt.getObject().isLiteral()) {
                continue;
            }

            Literal lit = stmt.getObject().asLiteral();
            res.getComment().put(lit.getLanguage(), lit.getLexicalForm());
        }

        return res;
    }

    public void save(File file) {
        Model m = getModel();

        try {
            m.write(new FileOutputStream(file), "TTL", uri);
        } catch (FileNotFoundException ex) {
            throw new RuntimeException(ex);
        }
    }

    public String toTTL() {
        Model m = getModel();

        StringWriter sw = new StringWriter();
        m.write(sw, "TTL", uri);

        return sw.toString();
    }

    public Model getModel() {
        Model m = ModelFactory.createDefaultModel();
        m.setNsPrefix(prefix, uri);
        m.setNsPrefix("rdf", RDF.getURI());
        m.setNsPrefix("rdfs", RDFS.getURI());

        //subClassOf
        createStatements(rootClasses, m, RDFS.subClassOf);
        createResources(resources(rootClasses), RDFS.Class, m);

        //subPropertyOf
        createStatements(rootProperties, m, RDFS.subPropertyOf);
        createResources(resources(rootProperties), RDF.Property, m);

        return m;
    }

    private void createStatements(List<Resource> l, Model m, org.apache.jena.rdf.model.Property p) {
        List<Map.Entry<Resource, Resource>> subOfs = subRelations(l);
        for (Map.Entry<Resource, Resource> e : subOfs) {
            m.add(
                    ResourceFactory.createResource(e.getKey().getURI()),
                    p,
                    ResourceFactory.createResource(e.getValue().getURI())
            );
        }
    }

    private void createResources(Set<Resource> s, org.apache.jena.rdf.model.Resource type, Model m) {
        for (Resource r : s) {
            //collect all referred ontologies
            m.setNsPrefix(r.getOntology().getPrefix(), r.getOntology().getUri());

            org.apache.jena.rdf.model.Resource jenaRes = ResourceFactory.createResource(r.getURI());
            m.add(jenaRes, RDF.type, type);

            for (Entry<String, String> e : r.getLabel().entrySet()) {
                if (!e.getValue().trim().isEmpty()) {
                    m.add(jenaRes, RDFS.label, e.getValue().trim(), e.getKey());
                }
            }
            for (Entry<String, String> e : r.getComment().entrySet()) {
                if (!e.getValue().trim().isEmpty()) {
                    m.add(jenaRes, RDFS.comment, e.getValue().trim(), e.getKey());
                }
            }

            if (type == RDF.Property) {
                if (r.hasDomain()) {
                    m.add(jenaRes, RDFS.domain, ResourceFactory.createResource(r.getDomain().getURI()));
                }
                if (r.hasRange()) {
                    m.add(jenaRes, RDFS.range, ResourceFactory.createResource(r.getRange().getURI()));
                }
            }
        }
    }

    private List<Map.Entry<Resource, Resource>> subRelations(List<Resource> inputList) {
        List<Map.Entry<Resource, Resource>> l = new ArrayList<>();

        Queue<Resource> q = new LinkedList<>();
        q.addAll(inputList);

        while (!q.isEmpty()) {
            Resource r = q.poll();
            for (Resource child : r.getChildren()) {
                l.add(new AbstractMap.SimpleEntry<>(child, r));
                q.add(child);
            }
        }

        return l;
    }

    private Set<Resource> resources(List<Resource> inputList) {
        Set<Resource> s = new HashSet<>();

        Queue<Resource> q = new LinkedList<>();
        q.addAll(inputList);

        while (!q.isEmpty()) {
            Resource r = q.poll();
            s.add(r);
            for (Resource child : r.getChildren()) {
                q.add(child);
            }
        }

        return s;
    }

    public JSONObject toJSON(int index) {
        JSONObject ontology = new JSONObject();
        ontology.put("uri", uri);
        ontology.put("prefix", prefix);
        ontology.put("hashCode", hashCode());
        ontology.put("index", index);

        for (List<Resource> tree : Arrays.asList(rootClasses, rootProperties)) {
            JSONObject root = new JSONObject();

            JSONArray children = new JSONArray();
            root.put("children", children);

            Collections.sort(tree);

            for (Resource res : tree) {
                children.put(res.toJSON(true, index));
            }

            ontology.put((tree == rootClasses ? "classes" : "properties"), root);
        }

        return ontology;
    }

    public void changeResource(int hashCode, Resource changeTo, String what, String lang) {
        Resource res = findByHashCode(hashCode);
        if (res != null) {
            res.change(changeTo, what, lang);
        }
    }

    public void removeResource(int hashCode) {
        Resource res = findByHashCode(hashCode);
        if (res != null) {
            if (res.hasParent()) {
                res.getParent().removeChild(res);
            } else {
                if(res.isImported()) {
                    this.getRoot(res.getType()).remove(res);
                } else {
                    res.getOntology().getRoot(res.getType()).remove(res);
                }
            }
        }
    }

    public Resource findByHashCode(int hashCode) {
        for (List<Resource> l : Arrays.asList(rootClasses, rootProperties)) {
            for (Resource r : l) {
                for (Resource desc : r.descendants()) {
                    if (desc.hashCode() == hashCode) {
                        return desc;
                    }
                }
            }
        }
        return null;
    }
}
