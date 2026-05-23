package com.aresstack.pyloros;

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
import com.aresstack.pyloros.upstream.github.GitHubMcpConfig;
import com.aresstack.pyloros.upstream.github.GitHubToolProvider;
import com.aresstack.pyloros.upstream.idea.IdeaMcpClient;
import com.aresstack.pyloros.upstream.idea.IdeaMcpConfig;
import com.aresstack.pyloros.upstream.idea.IdeaToolProvider;
import com.aresstack.pyloros.upstream.intellijindex.IntellijIndexToolProvider;
import com.aresstack.pyloros.upstream.mcp.McpUpstreamConfig;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
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

        IdeaMcpConfig ideaConfig = config.ideaMcpConfig();
        IdeaMcpClient ideaMcpClient = new IdeaMcpClient(vertx, ideaConfig);
        if (ideaConfig.enabled()) {
            ideaMcpClient.start();
            log.info("[MCP-UPSTREAM] provider=intellij transport=sse enabled endpoint=http://{}:{}{}",
                    ideaConfig.host(), ideaConfig.port(), ideaConfig.ssePath());
        } else {
            log.info("[MCP-UPSTREAM] provider=intellij disabled");
        }

        GitHubMcpConfig githubConfig = config.githubMcpConfig();
        ToolProvider githubProvider = new GitHubToolProvider(vertx, githubConfig);
        if (githubConfig.enabled() && githubConfig.token() != null && !githubConfig.token().isBlank()) {
            log.info("[MCP-UPSTREAM] provider=github transport=streamable-http enabled url={}", githubConfig.url());
        } else {
            log.info("[MCP-UPSTREAM] provider=github disabled (set PYLOROS_UPSTREAM_GITHUB_ENABLED=true and GITHUB_MCP_TOKEN)");
        }

        McpUpstreamConfig intellijIndexConfig = config.intellijIndexUpstreamConfig();
        ToolProvider intellijIndexProvider = new IntellijIndexToolProvider(vertx, intellijIndexConfig);
        if (intellijIndexConfig.enabled()) {
            log.info("[MCP-UPSTREAM] provider=intellij-index transport={} enabled url={}",
                    intellijIndexConfig.transport(), intellijIndexConfig.url());
        } else {
            log.info("[MCP-UPSTREAM] provider=intellij-index disabled");
        }

        List<ToolProvider> providers = new ArrayList<>();
        providers.add(new PylorosPingToolProvider());
        providers.add(new IdeaToolProvider(ideaConfig, ideaMcpClient));
        providers.add(githubProvider);
        providers.add(intellijIndexProvider);

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
}
