package eu.bebendorf.bgplog;

import com.lumaserv.bgp.BGPServer;
import com.lumaserv.bgp.BGPSession;
import com.lumaserv.bgp.BGPSessionConfiguration;
import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.ValidationOptions;
import lombok.Getter;
import org.bson.Document;
import org.javawebstack.webutils.config.Config;
import org.javawebstack.webutils.config.EnvFile;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
public class BGPLog {

    public static BGPLog INSTANCE;

    public static void main(String[] args) {
        INSTANCE = new BGPLog();
        INSTANCE.start();
    }

    final Config config;
    final MongoCollection<Document> sessions;
    final MongoCollection<Document> routes;
    final Map<BGPSession, String> sessionIds = new HashMap<>();
    final Set<Integer> hiddenAsns;

    public BGPLog() {
        config = new Config().add(new EnvFile(new File(".env")).withVariables(), new HashMap<String, String>() {{
            put("MONGODB_URL", "mongodb.url");
            put("MONGODB_DATABASE", "mongodb.database");
            put("BGP_PORT", "bgp.port");
            put("BGP_HIDE_AS", "bgp.hide_as");
            put("BGP_LOCAL_AS", "bgp.local.as");
            put("BGP_LOCAL_ROUTER_ID", "bgp.local.router_id");
            put("BGP_PEER_NAME", "bgp.peer.name");
            put("BGP_PEER_AS", "bgp.peer.as");
            put("BGP_PEER_ROUTER_ID", "bgp.peer.router_id");
        }});
        ConnectionString connectionString = new ConnectionString(config.get("mongodb.url"));
        MongoClient client = MongoClients.create(connectionString);
        MongoDatabase database = client.getDatabase(config.get("mongodb.database", "bgp-log"));
        sessions = database.getCollection("sessions");
        routes = database.getCollection("routes");
        hiddenAsns = config.get("bgp.hide_as", "").length() > 0 ? Stream.of(config.get("bgp.hide_as").split(",")).map(Integer::parseInt).collect(Collectors.toSet()) : new HashSet<>();
    }

    private void cleanup() {
        List<String> ids = new ArrayList<>();
        sessions.find(new Document()
                .append("peer", config.get("bgp.peer.name", ""))
                .append("closed_at", null)
        ).map(d -> d.getObjectId("_id").toString()).into(ids);
        Date date = Date.from(Instant.now());
        sessions.updateMany(new Document()
                .append("_id", new Document()
                        .append("$in", ids)
                ),
                new Document().append("$set", new Document()
                        .append("closed_at", date)
                )
        );
        routes.updateMany(new Document()
                .append("session_id", new Document()
                        .append("$in", ids)
                ),
                new Document().append("$set", new Document()
                        .append("withdrawn_at", date)
                )
        );
    }

    public void start() {
        cleanup();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Date date = Date.from(Instant.now());
            sessions.updateMany(new Document()
                    .append("id", new Document()
                            .append("$in", sessionIds.values())
                    ),
                    new Document().append("$set", new Document()
                            .append("closed_at", date)
                    )
            );
            routes.updateMany(new Document()
                    .append("session_id", new Document()
                            .append("$in", sessionIds.values())
                    ),
                    new Document().append("$set", new Document()
                            .append("withdrawn_at", date)
                    )
            );
        }));
        try {
            BGPServer server = new BGPServer(config.getInt("bgp.port", 179));
            server.getSessionConfigurations().add(new BGPSessionConfiguration(
                    config.get("bgp.peer.name", ""),
                    config.getInt("bgp.local.as", 0),
                    AddressHelper.stringToAddress(config.get("bgp.local.router_id")),
                    config.getInt("bgp.peer.as", 0),
                    AddressHelper.stringToAddress(config.get("bgp.peer.router_id")),
                    new BGPLogListener(this)
            ));
            server.run();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
