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
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
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
    private String fragment;
    private String instanceNamespace;
    private String instancePrefix;

    private List<Resource> rootClasses;
    private List<Resource> rootProperties;

    private List<Link> inverseOf;
    
    public Ontology() {
        this.prefix = "";
        this.uri = "";
        this.fragment = "";
        this.name = "";
        this.instanceNamespace = "";
        this.instancePrefix = "";

        rootClasses = new ArrayList<>();
        rootProperties = new ArrayList<>();
        inverseOf = new ArrayList<>();
    }

    public void addInverseOf(Resource source, Resource target) {
        inverseOf.add(new Link(source, target));
    }
    
    public void removeInverseOf(Resource res) {
        for(Link link : inverseOf.toArray(new Link[0])) {
            if(link.getSource().equals(res) || link.getTarget().equals(res)) {
                inverseOf.remove(link);
            }
        }
    }
    
    public Resource getInverseOf(Resource res) {
        for(Link link : inverseOf.toArray(new Link[0])) {
            if(link.getSource().equals(res)) {
                return link.getTarget();
            }
        }
        return null;
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

    public String getUriWithFragment() {
        return uri + getFragment();
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

    public String getFragment() {
        if (fragment == null) {
            return "";
        }
        return fragment;
    }

    public void setFragment(String fragment) {
        this.fragment = fragment;
    }

    public String getInstanceNamespace() {
        return instanceNamespace;
    }

    public void setInstanceNamespace(String instanceNamespace) {
        this.instanceNamespace = instanceNamespace;
    }

    public String getInstancePrefix() {
        return instancePrefix;
    }

    public void setInstancePrefix(String instancePrefix) {
        this.instancePrefix = instancePrefix;
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

        loadTBox(onto, m);
        loadABox(onto, m);

        return onto;
    }

    public static Ontology loadTTL(String filename, InputStream is) {
        Ontology onto = new Ontology();

        Model m = ModelFactory.createDefaultModel();

        m.read(is, null, "TURTLE");

        //guess prefix from file name
        onto.setPrefix(FilenameUtils.getBaseName(filename));

        loadTBox(onto, m);
        loadABox(onto, m);

        return onto;
    }

    //from model to onto
    public static void loadTBox(Ontology onto, Model m) {
        Map<String, String> prefixMap = m.getNsPrefixMap();
        //try find full uri in file
        String uri = prefixMap.get(onto.getPrefix());
        if (uri != null) {
            onto.setUri(uri);

            //cleanup fragment
            if (uri.endsWith("/") || uri.endsWith("#")) {
                onto.setUri(uri.substring(0, uri.length() - 1));
                onto.setFragment(uri.substring(uri.length() - 1, uri.length()));
            }
        }

        Map<org.apache.jena.rdf.model.Resource, Resource> mapped = new HashMap<>();

        //for each mapping
        Map<String, Ontology> prefix2onto = new HashMap<>();
        for (Entry<String, String> mapping : prefixMap.entrySet()) {

            //put prefix to onto
            Ontology other;
            if (mapping.getKey().equals(onto.getPrefix())) {
                other = onto;
            } else {
                //create for the prefix an ontology (e.g. xsd)
                other = new Ontology();
                other.setPrefix(mapping.getKey());
                other.setUri(mapping.getValue());

                if (other.getPrefix().equals("xsd")) {
                    initXSD(mapped, other);
                }
                if (other.getPrefix().equals("rdfs")) {
                    initRDFSLiteral(mapped, other);
                }
            }
            prefix2onto.put(mapping.getKey(), other);
        }

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
        
        loadInverseOf(onto, m);
    }

    //load tbox beforehand
    public static void loadABox(Ontology onto, Model m) {
        
        //List<org.apache.jena.rdf.model.Resource> jenaInstances = new ArrayList<>(); 
        
        Map<String, Resource> uri2resource = onto.getUri2ResourceMap();
        
        Map<org.apache.jena.rdf.model.Resource, Resource> jena2res = new HashMap<>();
        
        //types
        for(Statement stmt : m.listStatements(null, RDF.type, (RDFNode) null).toList()) {
            if(!stmt.getObject().isResource())
                continue;
            
            Resource clazz = uri2resource.get(stmt.getObject().asResource().getURI());
            if(clazz != null && clazz.getType() == Resource.Type.Class) {
                
                String ns = stmt.getSubject().getNameSpace();
                String instPrefix = m.getNsURIPrefix(ns);
                if(instPrefix != null) {
                    onto.setInstanceNamespace(ns);
                    onto.setInstancePrefix(instPrefix);
                }
                
                Resource inst = toResource(stmt.getSubject(), m, onto, Resource.Type.Instance, null);
                clazz.addInstance(inst);
                
                jena2res.put(stmt.getSubject(), inst);
            }
        }
        
        //for all collected instances
        for(org.apache.jena.rdf.model.Resource jenaInstance : jena2res.keySet()) {
            
            Resource subj = jena2res.get(jenaInstance);
            
            for(Statement triple : m.listStatements(jenaInstance, null, (RDFNode) null).toList()) {
                Resource prop = uri2resource.get(triple.getPredicate().getURI());
                
                if(prop == null)
                    continue;
                
                //literal case
                if(triple.getObject().isLiteral()) {
                    Resource literal = new Resource(onto, Resource.Type.Literal);
                    literal.getComment().put("", triple.getObject().asLiteral().getLexicalForm());
                    
                    prop.addLink(subj, literal);
                    continue;
                }

                //resource case
                if(!triple.getObject().isResource())
                    continue;
                
                Resource obj  = jena2res.get(triple.getObject().asResource());
                
                if(prop != null && obj != null) {
                    prop.addLink(subj, obj);
                }
            }
        }
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

        Ontology trgOnto = prefix2onto == null ? null : prefix2onto.get(prefix);
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

    private static void loadInverseOf(Ontology onto, Model m) {
        for(Statement stmt : m.listStatements(null, OWL.inverseOf, (RDFNode) null).toList()) {
            
            Resource subj = onto.findByUri(stmt.getSubject().getURI());
            Resource obj = onto.findByUri(stmt.getObject().asResource().getURI());
            
            if(subj != null && obj != null) {
                onto.addInverseOf(subj, obj);
            }
        }
    }
    
    private static void initXSD(Map<org.apache.jena.rdf.model.Resource, Resource> map, Ontology onto) {
        map.put(XSD.anyURI, new Resource(onto, Resource.Type.Class, "anyURI"));
        map.put(XSD.base64Binary, new Resource(onto, Resource.Type.Class, "base64Binary"));
        map.put(XSD.date, new Resource(onto, Resource.Type.Class, "date"));
        map.put(XSD.dateTime, new Resource(onto, Resource.Type.Class, "dateTime"));
        map.put(XSD.dateTimeStamp, new Resource(onto, Resource.Type.Class, "dateTimeStamp"));
        map.put(XSD.dayTimeDuration, new Resource(onto, Resource.Type.Class, "dayTimeDuration"));
        map.put(XSD.decimal, new Resource(onto, Resource.Type.Class, "decimal"));
        map.put(XSD.duration, new Resource(onto, Resource.Type.Class, "duration"));
        map.put(XSD.ENTITIES, new Resource(onto, Resource.Type.Class, "ENTITIES"));
        map.put(XSD.ENTITY, new Resource(onto, Resource.Type.Class, "ENTITY"));
        map.put(XSD.gDay, new Resource(onto, Resource.Type.Class, "gDay"));
        map.put(XSD.gMonth, new Resource(onto, Resource.Type.Class, "gMonth"));
        map.put(XSD.gMonthDay, new Resource(onto, Resource.Type.Class, "gMonthDay"));
        map.put(XSD.gYear, new Resource(onto, Resource.Type.Class, "gYear"));
        map.put(XSD.gYearMonth, new Resource(onto, Resource.Type.Class, "gYearMonth"));
        map.put(XSD.hexBinary, new Resource(onto, Resource.Type.Class, "hexBinary"));
        map.put(XSD.ID, new Resource(onto, Resource.Type.Class, "ID"));
        map.put(XSD.IDREF, new Resource(onto, Resource.Type.Class, "IDREF"));
        map.put(XSD.IDREFS, new Resource(onto, Resource.Type.Class, "IDREFS"));
        map.put(XSD.integer, new Resource(onto, Resource.Type.Class, "integer"));
        map.put(XSD.language, new Resource(onto, Resource.Type.Class, "language"));
        map.put(XSD.Name, new Resource(onto, Resource.Type.Class, "Name"));
        map.put(XSD.NCName, new Resource(onto, Resource.Type.Class, "NCName"));
        map.put(XSD.negativeInteger, new Resource(onto, Resource.Type.Class, "negativeInteger"));
        map.put(XSD.NMTOKEN, new Resource(onto, Resource.Type.Class, "NMTOKEN"));
        map.put(XSD.NMTOKENS, new Resource(onto, Resource.Type.Class, "NMTOKENS"));
        map.put(XSD.nonNegativeInteger, new Resource(onto, Resource.Type.Class, "nonNegativeInteger"));
        map.put(XSD.nonPositiveInteger, new Resource(onto, Resource.Type.Class, "nonPositiveInteger"));
        map.put(XSD.normalizedString, new Resource(onto, Resource.Type.Class, "normalizedString"));
        map.put(XSD.NOTATION, new Resource(onto, Resource.Type.Class, "NOTATION"));
        map.put(XSD.positiveInteger, new Resource(onto, Resource.Type.Class, "positiveInteger"));
        map.put(XSD.QName, new Resource(onto, Resource.Type.Class, "QName"));
        map.put(XSD.time, new Resource(onto, Resource.Type.Class, "time"));
        map.put(XSD.token, new Resource(onto, Resource.Type.Class, "token"));
        map.put(XSD.unsignedByte, new Resource(onto, Resource.Type.Class, "unsignedByte"));
        map.put(XSD.unsignedInt, new Resource(onto, Resource.Type.Class, "unsignedInt"));
        map.put(XSD.unsignedLong, new Resource(onto, Resource.Type.Class, "unsignedLong"));
        map.put(XSD.unsignedShort, new Resource(onto, Resource.Type.Class, "unsignedShort"));
        map.put(XSD.xboolean, new Resource(onto, Resource.Type.Class, "boolean"));
        map.put(XSD.xbyte, new Resource(onto, Resource.Type.Class, "byte"));
        map.put(XSD.xdouble, new Resource(onto, Resource.Type.Class, "double"));
        map.put(XSD.xfloat, new Resource(onto, Resource.Type.Class, "float"));
        map.put(XSD.xint, new Resource(onto, Resource.Type.Class, "int"));
        map.put(XSD.xlong, new Resource(onto, Resource.Type.Class, "long"));
        map.put(XSD.xshort, new Resource(onto, Resource.Type.Class, "short"));
        map.put(XSD.xstring, new Resource(onto, Resource.Type.Class, "string"));
        map.put(XSD.yearMonthDuration, new Resource(onto, Resource.Type.Class, "yearMonthDuration"));
    }
    
    private static void initRDFSLiteral(Map<org.apache.jena.rdf.model.Resource, Resource> map, Ontology onto) {
        map.put(RDFS.Literal, new Resource(onto, Resource.Type.Class, "Literal"));
    }

    public void save(File file) {
        Model union = getTBoxABoxModel();
        
        try {
            union.write(new FileOutputStream(file), "TTL", uri);
        } catch (FileNotFoundException ex) {
            throw new RuntimeException(ex);
        }
    }

    public String toTTL() {
        Model union = getTBoxABoxModel();

        StringWriter sw = new StringWriter();
        union.write(sw, "TTL", uri);

        return sw.toString();
    }

    public Model getTBoxModel() {
        Model m = ModelFactory.createDefaultModel();
        m.setNsPrefix(prefix, getUriWithFragment());
        m.setNsPrefix("rdf", RDF.getURI());
        m.setNsPrefix("rdfs", RDFS.getURI());

        //subClassOf
        createStatements(rootClasses, m, RDFS.subClassOf);
        createResources(resources(rootClasses), RDFS.Class, m);

        //subPropertyOf
        createStatements(rootProperties, m, RDFS.subPropertyOf);
        createResources(resources(rootProperties), RDF.Property, m);

        //inverseOf (use alt + drag&drop)
        for(Link link : inverseOf) {
            org.apache.jena.rdf.model.Resource a = ResourceFactory.createResource(link.getSource().getURI());
            org.apache.jena.rdf.model.Resource b = ResourceFactory.createResource(link.getTarget().getURI());
            m.add(a, OWL.inverseOf, b);
        }
        
        if(!inverseOf.isEmpty()) {
            m.setNsPrefix("owl", OWL.NS);
        }
        
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

    private List<org.apache.jena.rdf.model.Resource> createResources(Set<Resource> s, org.apache.jena.rdf.model.Resource type, Model m) {
        List<org.apache.jena.rdf.model.Resource> l = new ArrayList<>();
        for (Resource r : s) {
            //collect all referred ontologies
            m.setNsPrefix(r.getOntology().getPrefix(), r.getOntology().getUriWithFragment());

            //domain/range (collect all referred ontologies)
            if (r.getType() == Resource.Type.Property) {
                if (r.hasDomain()) {
                    m.setNsPrefix(r.getDomain().getOntology().getPrefix(), r.getDomain().getOntology().getUriWithFragment());
                }
                if (r.hasRange()) {
                    m.setNsPrefix(r.getRange().getOntology().getPrefix(), r.getRange().getOntology().getUriWithFragment());
                }
            }

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
            
            l.add(jenaRes);
        }
        return l;
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

    public Model getABoxModel() {
        Model m = ModelFactory.createDefaultModel();
        m.setNsPrefix(prefix, getUriWithFragment());
        m.setNsPrefix(instancePrefix, instanceNamespace);
        m.setNsPrefix("rdf", RDF.getURI());
        m.setNsPrefix("rdfs", RDFS.getURI());
        
        for (List<Resource> l : Arrays.asList(rootClasses)) {
            for (Resource r : l) {
                for (Resource clazz : r.descendants()) {
                    org.apache.jena.rdf.model.Resource jenaClass = ResourceFactory.createResource(clazz.getURI());
                    for(Resource inst : clazz.getInstances()) {
                        createResources(new HashSet<>(Arrays.asList(inst)), jenaClass, m).get(0);
                    }
                }
            }
        }
        
        for (List<Resource> l : Arrays.asList(rootProperties)) {
            for (Resource r : l) {
                for (Resource property : r.descendants()) {
                    
                    if(property.getLinks().isEmpty())
                        continue;
                    
                    org.apache.jena.rdf.model.Property jenaProperty = ResourceFactory.createProperty(property.getURI());
                    
                    for(Link link : property.getLinks()) {
                        org.apache.jena.rdf.model.Resource jenaInstA = ResourceFactory.createResource(link.getSource().getURI());
                        
                        if(link.getTarget().getType() == Resource.Type.Literal) {
                            
                            //untyped literal
                            String literalValue = link.getTarget().getLiteral().get("");
                            m.add(jenaInstA, jenaProperty, literalValue);
                            
                        } else {
                            org.apache.jena.rdf.model.Resource jenaInstB = ResourceFactory.createResource(link.getTarget().getURI());
                            m.add(jenaInstA, jenaProperty, jenaInstB);
                        }
                    }
                }
            }
        }
        
        return m;
    }
    
    public Model getTBoxABoxModel() {
        Model tbox = getTBoxModel();
        Model abox = getABoxModel();
        return ModelFactory.createUnion(tbox, abox);
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
                if (res.isImported()) {
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

    public Resource findClassByLocalname(String localname) {
        for (List<Resource> l : Arrays.asList(rootClasses)) {
            for (Resource r : l) {
                for (Resource desc : r.descendants()) {
                    if (desc.getLocalname().equals(localname)) {
                        return desc;
                    }
                }
            }
        }
        return null;
    }
    
    public Resource findByUri(String uri) {
        for (List<Resource> l : Arrays.asList(rootClasses, rootProperties)) {
            for (Resource r : l) {
                for (Resource desc : r.descendants()) {
                    
                    if (desc.getURI().equals(uri)) {
                        return desc;
                    }
                    
                    for(Resource inst : desc.getInstances()) {
                        if (inst.getURI().equals(uri)) {
                            return inst;
                        }
                    }
                    
                    for(Link link : desc.getLinks()) {
                        if (link.getSource().getURI().equals(uri)) {
                            return link.getSource();
                        }
                        if (link.getTarget().getURI().equals(uri)) {
                            return link.getTarget();
                        }
                    }
                    
                }
            }
        }
        return null;
    }
    
    public Map<String, Resource> getUri2ResourceMap() {
        Map<String, Resource> m = new HashMap<>();
        
        for (List<Resource> l : Arrays.asList(rootClasses, rootProperties)) {
            for (Resource r : l) {
                for (Resource desc : r.descendants()) {
                    
                    m.put(desc.getURI(), desc);
                    
                    for(Resource inst : desc.getInstances()) {
                        m.put(inst.getURI(), inst);
                    }
                    
                    for(Link link : desc.getLinks()) {
                        m.put(link.getSource().getURI(), link.getSource());
                        m.put(link.getTarget().getURI(), link.getTarget());
                    }
                }
            }
        }
        
        return m;
    }
    
    public void removeLinksHaving(Resource res) {
        for (List<Resource> l : Arrays.asList(rootProperties)) {
            for (Resource r : l) {
                for (Resource prop : r.descendants()) {
                    for(Link link : prop.getLinks().toArray(new Link[0])) {
                        if(link.getSource().equals(res) || link.getTarget().equals(res)) {
                            prop.getLinks().remove(link);
                        }
                    }
                }
            }
        }
    }
}
