package com.aresstack.pyloros;

import com.aresstack.pyloros.config.LoadedMcpJsonConfig;
import com.aresstack.pyloros.config.McpJsonConfigLoader;
import com.aresstack.pyloros.config.PylorosConfig;
import com.aresstack.pyloros.http.HealthRoutes;
import com.aresstack.pyloros.http.McpRoutes;
import com.aresstack.pyloros.http.MetadataRoutes;
import com.aresstack.pyloros.http.OAuthRoutes;
import com.aresstack.pyloros.oauth.OAuthService;
import com.aresstack.pyloros.provider.ProviderRegistry;
import com.aresstack.pyloros.tool.PylorosPingToolProvider;
import com.aresstack.pyloros.tool.ToolCatalog;
import com.aresstack.pyloros.tool.ToolProvider;
import com.aresstack.pyloros.tool.ToolRouter;
import com.aresstack.pyloros.upstream.github.GitHubToolProvider;
import com.aresstack.pyloros.upstream.idea.IdeaMcpClient;
import com.aresstack.pyloros.upstream.idea.IdeaMcpConfig;
import com.aresstack.pyloros.upstream.idea.IdeaToolProvider;
import com.aresstack.pyloros.upstream.intellijindex.IntellijIndexToolProvider;
import com.aresstack.pyloros.upstream.mcp.GenericMcpToolProvider;
import com.aresstack.pyloros.upstream.mcp.McpUpstreamClient;
import com.aresstack.pyloros.upstream.mcp.McpUpstreamClients;
import com.aresstack.pyloros.upstream.mcp.McpUpstreamConfig;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class PylorosApplication extends AbstractVerticle {

    private static final Logger log = LoggerFactory.getLogger(PylorosApplication.class);

    private final String[] launchArgs;

    public PylorosApplication() {
        this(new String[0]);
    }

    public PylorosApplication(String[] launchArgs) {
        this.launchArgs = launchArgs == null ? new String[0] : launchArgs.clone();
    }

    public static void main(String[] args) {
        Vertx.vertx().deployVerticle(new PylorosApplication(args))
                .onFailure(error -> {
                    log.error("Failed to deploy Pyloros", error);
                    System.exit(1);
                });
    }

    @Override
    public void start() {
        PylorosConfig config = PylorosConfig.load();
        OAuthService oauthService = new OAuthService(config);

        List<ToolProvider> providers = new ArrayList<>();
        providers.add(new PylorosPingToolProvider());
        registerExternalProviders(providers);

        ProviderRegistry providerRegistry = new ProviderRegistry(providers);
        ToolCatalog toolCatalog = new ToolCatalog(providerRegistry);
        ToolRouter toolRouter = new ToolRouter(providerRegistry, toolCatalog);

        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        new MetadataRoutes(config).mount(router);
        new HealthRoutes().mount(router);
        new OAuthRoutes(oauthService).mount(router);
        new McpRoutes(config, oauthService, toolCatalog, toolRouter).mount(router);

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

    private void registerExternalProviders(List<ToolProvider> providers) {
        McpJsonConfigLoader loader = new McpJsonConfigLoader();
        Optional<LoadedMcpJsonConfig> loaded = loader.load(launchArgs);
        if (loaded.isEmpty()) {
            log.info("[MCP-CONFIG] no mcp.json found; starting without external MCP upstreams");
            return;
        }

        LoadedMcpJsonConfig mcpJson = loaded.get();
        log.info("[MCP-CONFIG] loaded mcp.json path={} serverCount={}",
                mcpJson.path(),
                mcpJson.config().servers() == null ? 0 : mcpJson.config().servers().size());

        for (McpUpstreamConfig upstream : loader.resolveUpstreams(mcpJson)) {
            ToolProvider provider = createProvider(upstream);
            if (provider != null) {
                providers.add(provider);
            }
        }
    }

    private ToolProvider createProvider(McpUpstreamConfig upstream) {
        log.info("[MCP-UPSTREAM] provider={} transport={} url={}",
                upstream.providerId(), upstream.transport(), upstream.url());

        if ("intellij".equals(upstream.providerId()) && "sse".equalsIgnoreCase(upstream.transport())) {
            IdeaMcpConfig ideaConfig = IdeaMcpConfig.from(upstream);
            IdeaMcpClient ideaClient = new IdeaMcpClient(vertx, ideaConfig);
            ideaClient.start();
            return new IdeaToolProvider(ideaConfig, ideaClient);
        }

        if ("github".equals(upstream.providerId())) {
            return new GitHubToolProvider(vertx, upstream);
        }

        if ("intellij-index".equals(upstream.providerId())) {
            return new IntellijIndexToolProvider(vertx, upstream);
        }

        McpUpstreamClient client = McpUpstreamClients.create(vertx, upstream);
        client.start();
        return new GenericMcpToolProvider(upstream, client);
    }
}
