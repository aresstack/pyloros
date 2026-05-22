package com.aresstack.pyloros.upstream.idea;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
/**
 * High-level client that coordinates SSE session and JSON-RPC client. 001-B provides skeleton functionality.
 */
public final class IdeaMcpClient {

    private static final Logger log = LoggerFactory.getLogger(IdeaMcpClient.class);

    private final Vertx vertx;
    private final IdeaMcpConfig config;

    private final IdeaSseSession sseSession;
    private final IdeaJsonRpcClient jsonRpcClient;
    private final AtomicReference<List<Map<String, Object>>> cachedTools = new AtomicReference<>();
    private volatile boolean initialized = false;

    public IdeaMcpClient(Vertx vertx, IdeaMcpConfig config) {
        this.vertx = vertx;
        this.config = config;
        this.sseSession = new IdeaSseSession(vertx, config);
        this.jsonRpcClient = new IdeaJsonRpcClient(vertx, config, sseSession);
    }

    public void start() {
        try {
            sseSession.start();
            log.info("IdeaMcpClient started (SSE session requested)");
        } catch (Exception ex) {
            log.debug("Failed to start IdeaMcpClient: {}", ex.getMessage());
        }
    }

    public boolean isReady() {
        return sseSession.isReady();
    }

    public Future<List<Map<String, Object>>> listTools() {
        if (!config.enabled()) {
            return Future.succeededFuture(List.of());
        }

        List<Map<String, Object>> cached = cachedTools.get();
        if (cached != null) {
            return Future.succeededFuture(cached);
        }

        if (!sseSession.isReady()) {
            return Future.succeededFuture(List.of());
        }

        Future<JsonObject> initFuture;
        if (!initialized) {
            initFuture = jsonRpcClient.postJsonRpc("initialize", new JsonObject());
        } else {
            initFuture = Future.succeededFuture(new JsonObject());
        }

        Promise<List<Map<String, Object>>> promise = Promise.promise();

        initFuture.compose(initResult -> {
            initialized = true;
            return jsonRpcClient.postJsonRpc("tools/list", new JsonObject());
        }).onSuccess(result -> {
            try {
                if (result == null) {
                    promise.complete(List.of());
                    return;
                }
                if (result.containsKey("tools")) {
                    JsonArray arr = result.getJsonArray("tools");
                    List<Map<String, Object>> tools = new ArrayList<>();
                    for (int i = 0; i < arr.size(); i++) {
                        Object item = arr.getValue(i);
                        if (item instanceof JsonObject jo) {
                            Map<String, Object> map = jo.getMap();
                            Map<String, Object> security = Map.of("type", "oauth2", "scopes", new String[]{"mcp"});
                            map.put("securitySchemes", new Object[]{security});
                            Object metaObj = map.get("_meta");
                            Map<String, Object> meta = (metaObj instanceof Map) ? (Map<String, Object>) metaObj : Map.of();
                            map.put("_meta", Map.of("securitySchemes", new Object[]{security}, "originalMeta", meta));
                            Object nameObj = map.get("name");
                            if (nameObj instanceof String name) {
                                map.put("name", config.toolPrefix() + name);
                            }
                            tools.add(map);
                        }
                    }
                    cachedTools.set(tools);
                    promise.complete(tools);
                    return;
                }
            } catch (Exception ex) {
                log.debug("Failed to parse tools/list result: {}", ex.getMessage());
            }
            promise.complete(List.of());
        }).onFailure(err -> {
            log.debug("tools/list failed: {}", err.getMessage());
            promise.complete(List.of());
        });

        return promise.future();
    }

    public String getEndpoint() {
        return sseSession.getEndpoint();
    }

    public Future<JsonObject> call(String method, JsonObject params) {
        return jsonRpcClient.postJsonRpc(method, params);
    }

    public Future<Void> stop() {
        return sseSession.stop();
    }
}

