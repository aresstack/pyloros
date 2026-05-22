package com.aresstack.pyloros;

import com.aresstack.pyloros.config.PylorosConfig;
import com.aresstack.pyloros.http.McpRoutes;
import com.aresstack.pyloros.http.MetadataRoutes;
import com.aresstack.pyloros.http.OAuthRoutes;
import com.aresstack.pyloros.oauth.OAuthService;
import com.aresstack.pyloros.tool.PylorosPingToolProvider;
import com.aresstack.pyloros.tool.ToolRegistry;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class PylorosApplication extends AbstractVerticle {

    private static final Logger log = LoggerFactory.getLogger(PylorosApplication.class);

    public static void main(String[] args) {
        Vertx.vertx().deployVerticle(new PylorosApplication())
                .onFailure(error -> {
                    log.error("Failed to deploy Pyloros", error);
                    System.exit(1);
                });
    }

    @Override
    public void start() {
        PylorosConfig config = PylorosConfig.load();
        OAuthService oauthService = new OAuthService(config);
        ToolRegistry toolRegistry = new ToolRegistry(List.of(new PylorosPingToolProvider()));

        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        new MetadataRoutes(config).mount(router);
        new OAuthRoutes(oauthService).mount(router);
        new McpRoutes(config, oauthService, toolRegistry).mount(router);

        vertx.createHttpServer()
                .requestHandler(router)
                .listen(config.serverPort())
                .onSuccess(server -> log.info(
                        "Pyloros listening on port {} with public URL {}{}",
                        config.serverPort(),
                        config.publicOrigin(),
                        config.mcpPublicPath()
                ))
                .onFailure(error -> log.error("Could not start HTTP server", error));
    }
}
