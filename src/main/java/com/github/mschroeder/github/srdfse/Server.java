package com.github.mschroeder.github.srdfse;

import freemarker.cache.ClassTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.TemplateExceptionHandler;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiConsumer;
import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.servlet.http.Part;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.RandomStringUtils;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.json.JSONObject;
import spark.ModelAndView;
import spark.Request;
import spark.Response;
import spark.template.freemarker.FreeMarkerEngine;

/**
 *
 * @author Markus Schr&ouml;der
 */
public class Server {

    public static final boolean DEBUG = false;
    
    //(R)dfs (E)ditor
    public static final int PORT = (int) 'R' * 100 + (int) 'E';

    private FreeMarkerEngine freeMarkerEngine;

    private Map<String, EditorFrame> session2context;

    private String serverHost;

    private CommandLine cmd;
    public static final Options options = new Options();
    public static final CommandLineParser parser = new DefaultParser();
    
    private SRDFSEWebSocket webSocket;

    static {
        options.addOption("h", "help", false, "prints this help");
        options.addOption("H", "host", true, "");
    }

    public Server(String[] args) {
        try {
            //parse it
            cmd = parser.parse(options, args);
        } catch (ParseException ex) {
            throw new RuntimeException(ex);
        }

        //help
        if (cmd.hasOption("h")) {
            new HelpFormatter().printHelp("sRDFSe", options);
            System.exit(0);
        }

        session2context = new HashMap<>();
        initFreemarker();
        initRoutes();

        serverHost = cmd.getOptionValue("host", "http://localhost:" + PORT);
    }

    /**
     * Inits freemarker template engine.
     */
    private void initFreemarker() {
        //template (freemarker)
        Configuration freemarkerConfig = new Configuration(Configuration.VERSION_2_3_26);
        freemarkerConfig.setDefaultEncoding("UTF-8");
        freemarkerConfig.setTemplateExceptionHandler(TemplateExceptionHandler.HTML_DEBUG_HANDLER);
        freemarkerConfig.setLogTemplateExceptions(false);
        freemarkerConfig.setTemplateLoader(new ClassTemplateLoader(Server.class, "/web/tmpl"));
        freeMarkerEngine = new FreeMarkerEngine(freemarkerConfig);
    }

    /**
     * Inits alls routes that can be used via HTTP REST.
     */
    private void initRoutes() {
        System.out.println("port: " + PORT);
        
        spark.Spark.port(PORT);

        webSocket = new SRDFSEWebSocket(this);
        spark.Spark.webSocket("/websocket", webSocket);

        spark.Spark.exception(Exception.class, (exception, request, response) -> {
            exception.printStackTrace();
            response.body(exception.getMessage());
        });

        //serve js css html
        spark.Spark.staticFiles.location("/web");

        spark.Spark.get("/", this::getRoot);
        spark.Spark.post("/sessions", this::postSession);
        spark.Spark.get("/sessions/:id", this::getSession);
        spark.Spark.post("/upload/:id", this::postUpload);
        spark.Spark.post("/import/:id", this::postImport);
        spark.Spark.get("/download/:id", this::getDownload);
        spark.Spark.delete("/sessions/:id", this::deleteSession);
    }

    private Object getRoot(Request req, Response resp) {
        Map<String, Object> m = getDefaultModel(req);
        m.put("nickname", req.queryParams("nickname"));
        m.put("session_closed", req.queryParams("session_closed") != null);
        m.put("session_not_found", req.queryParams("session_not_found") != null);
        return render(m, "root.html");
    }

    private Object postSession(Request req, Response resp) {
        String sessionId = RandomStringUtils.randomAlphanumeric(8);
        
        EditorFrame editor = new EditorFrame();
        editor.getUserOntology().setPrefix(sessionId);
        editor.getUserOntology().setUri(serverHost + "/sessions/" + sessionId + "/"); //with ending '/'
        session2context.put(sessionId, editor);

        resp.redirect("/sessions/" + sessionId);
        return null;
    }

    private Object postUpload(Request req, Response resp) throws IOException, ServletException {
        return postUploadOrImport(req, resp, true);
    }
    
    private Object postImport(Request req, Response resp) throws IOException, ServletException {
        return postUploadOrImport(req, resp, false);
    }
    
    private Object postUploadOrImport(Request req, Response resp, boolean upload) throws IOException, ServletException {
        String id = req.params("id");
        
        if (!session2context.containsKey(id)) {
            resp.status(404);
            return "";
        }
        
        EditorFrame editor = session2context.get(id);
        
        req.attribute("org.eclipse.jetty.multipartConfig", new MultipartConfigElement("/tmp"));
        Part part = req.raw().getPart("file");
        try (InputStream is = part.getInputStream()) {
            
            Ontology onto = Ontology.loadTTL(part.getSubmittedFileName(), is);
            
            if(upload) {
                editor.loadUserOntology(onto);
            } else {
                editor.importOntology(onto);
            }
            
            //notify all
            SessionRoom room = webSocket.getSessionById(id);
            if(room != null) {
                webSocket.sendInit(room, editor);
            }
        }
        return "";
    }
    
    private Object getSession(Request req, Response resp) {
        String id = req.params("id");

        if (!session2context.containsKey(id)) {
            resp.redirect("/?session_not_found");
            return "";
        }

        Map<String, Object> m = getDefaultModel(req);
        m.put("sessionId", id);

        return render(m, "session.html");
    }
    
    private Object getDownload(Request req, Response resp) {
        String id = req.params("id");

        if (!session2context.containsKey(id)) {
            resp.redirect("/?session_not_found");
            return "";
        }
        
        EditorFrame editor = session2context.get(id);
        
        String ttl = editor.getUserOntology().toTTL();
        
        resp.header("Content-Type", "text/turtle");
        resp.header("Content-Disposition", "attachment; filename=\""+ editor.getUserOntology().getPrefix() +".ttl\"");
        
        return ttl;
    }

    private Object deleteSession(Request req, Response resp) {
        String id = req.params("id");

        if (!session2context.containsKey(id)) {
            resp.status(404);
            return "";
        }
        
        //get by id and close it
        SessionRoom room = webSocket.getSessionById(id);
        webSocket.closeSessionRoom(room);
        
        session2context.remove(id);

        resp.status(204);
        return "";
    }
    
    public String render(Map<String, Object> m, String templateName) {
        return freeMarkerEngine.render(new ModelAndView(m, templateName));
    }

    public Map<String, Object> getDefaultModel(Request req) {
        Map<String, Object> m = new HashMap<>();
        m.put("selfhref", req.url());
        return m;
    }

    @WebSocket
    public class SRDFSEWebSocket {

        private Server server;

        // Store sessions if you want to, for example, broadcast a message to all users
        private Queue<Session> sessions = new ConcurrentLinkedQueue<>();

        //method name to actual java method
        private Map<String, BiConsumer<Session, JSONObject>> method2consumer;

        //websocket session to session room of sRDFSe
        private Map<Session, SessionRoom> session2room;

        public SRDFSEWebSocket(Server server) {
            this.server = server;

            //collect all sessions
            sessions = new ConcurrentLinkedQueue<>();
            //session to room assignment (1:1)
            session2room = new HashMap<>();

            //methods
            method2consumer = new HashMap<>();
            method2consumer.put("init", this::messageInit);
            method2consumer.put("uri", this::messageUri);
            method2consumer.put("prefix", this::messagePrefix);
            method2consumer.put("createResource", this::messageCreateResource);
            method2consumer.put("removeResource", this::messageRemoveResource);
            method2consumer.put("changed", this::messageChanged);
            method2consumer.put("dragAndDrop", this::messageDragAndDrop);
            method2consumer.put("reset", this::messageReset);
            method2consumer.put("importPreset", this::messageImportPreset);
        }

        @OnWebSocketConnect
        public void connected(Session session) throws IOException {
            sessions.add(session);
            if(DEBUG)
                System.out.println("(C connected)");
        }

        @OnWebSocketClose
        public void closed(Session session, int statusCode, String reason) {
            if(DEBUG)
                System.out.println("(C disconnected) " + statusCode + " " + reason);

            removeSessionFromRoom(session);
            sessions.remove(session);
            //https://docs.oracle.com/javaee/7/api/javax/websocket/CloseReason.CloseCodes.html
            //UNEXPECTED_CONDITION
            //1011 indicates that a server is terminating the connection because it encountered an unexpected condition that prevented it from fulfilling the request.
        }

        @OnWebSocketMessage
        public void message(Session session, String message) throws IOException {
            if(DEBUG)
                System.out.println("C: " + message);

            JSONObject json;
            try {
                json = new JSONObject(message);
            } catch (Exception e) {
                if(DEBUG)
                    System.err.println(e.getMessage());
                return;
            }

            if (!json.has("method")) {
                if(DEBUG)
                    System.err.println("no method found");
                return;
            }

            String method = json.getString("method");

            if (!method2consumer.containsKey(method)) {
                if(DEBUG)
                    System.err.println("method not registered");
                return;
            }

            try {
                method2consumer.get(method).accept(session, json);
            } catch(Throwable t) {
                t.printStackTrace();
                new RuntimeException(t);
            }
        }

        private void messageInit(Session session, JSONObject data) {
            //connect session with this session id
            String sessionId = data.getString("sessionId");
            addSessionToRoom(session, sessionId);
            EditorFrame editor = server.session2context.get(sessionId);

            send(session, "init",
                    "state", editor.toJSON()
            );
        }

        private void messageUri(Session session, JSONObject data) {
            SessionRoom room = getRoomOfSession(session);
            EditorFrame editor = getEditor(session);
            editor.getUserOntology().setUri(data.getString("value"));

            send(room, "ontology",
                    "prefix", editor.getUserOntology().getPrefix(),
                    "uri", editor.getUserOntology().getUri()
            );
        }

        private void messagePrefix(Session session, JSONObject data) {
            SessionRoom room = getRoomOfSession(session);
            EditorFrame editor = getEditor(session);
            editor.getUserOntology().setPrefix(data.getString("value"));

            send(room, "ontology",
                    "prefix", editor.getUserOntology().getPrefix(),
                    "uri", editor.getUserOntology().getUri()
            );
        }

        private void messageCreateResource(Session session, JSONObject data) {
            JSONObject resObj = data.getJSONObject("resource");
            SessionRoom room = getRoomOfSession(session);
            EditorFrame editor = getEditor(session);

            Resource resource = Resource.fromJSON(editor.getUserOntology(), resObj);

            if (resource.getType() == Resource.Type.Class) {
                editor.getUserOntology().getRootClasses().add(resource);
            } else if (resource.getType() == Resource.Type.Property) {
                editor.getUserOntology().getRootProperties().add(resource);
            }

            //notify what hashCode the resource has
            send(session, "created",
                    "resource", resource.toJSON(0)
            );

            //notify all because something changed in the tree
            send(room, "init",
                    "state", editor.toJSON()
            );
        }

        private void messageChanged(Session session, JSONObject data) {
            JSONObject resObj = data.getJSONObject("resource");
            SessionRoom room = getRoomOfSession(session);
            EditorFrame editor = getEditor(session);

            Resource changeTo = Resource.fromJSON(editor.getUserOntology(), resObj);

            editor.getUserOntology().changeResource(resObj.getInt("hashCode"), changeTo, data.getString("what"), data.getString("lang"));

            //notify all that something changed in the tree
            send(room, "init",
                    "state", editor.toJSON()
            );
        }

        private void messageRemoveResource(Session session, JSONObject data) {
            SessionRoom room = getRoomOfSession(session);
            EditorFrame editor = getEditor(session);

            JSONObject resObj = data.getJSONObject("resource");
            if (resObj.has("hashCode")) {
                Object obj = editor.getObjectByHashCode(resObj.getInt("hashCode"));
                
                if(obj instanceof Ontology) {
                    editor.removeOntology((Ontology)obj);
                } else if(obj instanceof Resource) {
                    //((Resource)obj)
                    editor.getUserOntology().removeResource(resObj.getInt("hashCode"));
                }
                
                JSONObject hashCodeObj = new JSONObject();
                hashCodeObj.put("hashCode", resObj.getInt("hashCode"));
                
                //notify all because resource was removed
                send(room, "removed",
                        "resource", hashCodeObj
                );

                //notify all because something changed in the tree
                send(room, "init",
                        "state", editor.toJSON()
                );
            }
        }

        private void messageDragAndDrop(Session session, JSONObject data) {
            SessionRoom room = getRoomOfSession(session);
            EditorFrame editor = getEditor(session);

            editor.dragAndDrop(
                    data.getString("srcTreeType"), data.getInt("srcHashCode"),
                    data.getString("dstTreeType"), data.getInt("dstHashCode")
            );

            //notify all because something changed in the tree
            send(room, "init",
                    "state", editor.toJSON()
            );
        }

        private void messageReset(Session session, JSONObject data) {
            SessionRoom room = getRoomOfSession(session);
            EditorFrame editor = getEditor(session);
            
            String what = data.getString("what");
            int hashCode = data.getInt("hashCode");
            
            Resource res = editor.getUserOntology().findByHashCode(hashCode);
            if(res != null && res.getType() == Resource.Type.Property) {
                switch(what) {
                    case "Domain": res.setDomain(null); break;
                    case "Range": res.setRange(null); break;
                }
                
                //notify all because something changed in the tree
                send(room, "init",
                        "state", editor.toJSON()
                );
            }
        }
        
        private void messageImportPreset(Session session, JSONObject data) {
            
            SessionRoom room = getRoomOfSession(session);
            EditorFrame editor = getEditor(session);
            
            String preset = data.getString("preset");
            
            editor.importOntologyFromResource(preset);
            
            send(room, "init",
                    "state", editor.toJSON()
            );
        }
        
        //======================================================================
        
        private void sendInit(SessionRoom room, EditorFrame editor) {
            send(room, "init",
                    "state", editor.toJSON()
            );
        }
        
        private void send(SessionRoom room, String method, Object... keyValueParams) {
            for (Session session : room.sessions) {
                send(session, method, keyValueParams);
            }
        }

        private void send(Session session, String method, Object... keyValueParams) {
            JSONObject json = new JSONObject();
            json.put("method", method);
            for (int i = 0; i < keyValueParams.length; i += 2) {
                json.put((String) keyValueParams[i], keyValueParams[i + 1]);
            }
            if(DEBUG)
                System.out.println("S: " + json.toString());
            try {
                session.getRemote().sendString(json.toString());
            } catch (IOException ex) {
                session.close();
            }
        }

        //room management
        private SessionRoom addSessionToRoom(Session session, String id) {
            SessionRoom foundRoom = null;

            for (SessionRoom room : session2room.values()) {
                if (room.sessionId.equals(id)) {
                    foundRoom = room;
                    break;
                }
            }

            if (foundRoom == null) {
                foundRoom = new SessionRoom(id);
            }

            session2room.put(session, foundRoom);
            foundRoom.sessions.add(session);

            //TODO update user list for other sessions in room
            return foundRoom;
        }

        private void removeSessionFromRoom(Session session) {
            //TODO update user list for other sessions in room

            getRoomOfSession(session).sessions.remove(session);
        }

        private SessionRoom getRoomOfSession(Session session) {
            return session2room.get(session);
        }

        private EditorFrame getEditor(Session session) {
            SessionRoom room = getRoomOfSession(session);
            return server.session2context.get(room.sessionId);
        }

        private SessionRoom getSessionById(String id) {
            for (SessionRoom room : session2room.values()) {
                if (room.sessionId.equals(id)) {
                    return room;
                }
            }
            return null;
        }
        
        private void closeSessionRoom(SessionRoom room) {
            send(room, "closed");
        }
        
    }

    private class SessionRoom {

        /*package*/ String sessionId;
        /*package*/ List<Session> sessions;

        public SessionRoom(String sessionId) {
            this.sessionId = sessionId;
            this.sessions = new ArrayList<>();
        }

    }

}
