package com.aresstack.pyloros;

import com.aresstack.pyloros.config.AcpProviderFactory;
import com.aresstack.pyloros.config.LoadedMcpJsonConfig;
import com.aresstack.pyloros.config.McpJsonConfigLoader;
import com.aresstack.pyloros.config.PylorosConfig;
import com.aresstack.pyloros.config.ToolNameSeparatorResolver;
import com.aresstack.pyloros.extension.LoadedTargetPlatformModules;
import com.aresstack.pyloros.extension.TargetPlatformModuleLoader;
import com.aresstack.pyloros.extension.TargetPlatformSkillsToolProvider;
import com.aresstack.pyloros.http.HealthRoutes;
import com.aresstack.pyloros.http.McpRoutes;
import com.aresstack.pyloros.oauth.OAuthSecurityModule;
import com.aresstack.pyloros.provider.ProviderRegistry;
import com.aresstack.pyloros.security.NoSecurityModule;
import com.aresstack.pyloros.security.SecurityModule;
import com.aresstack.pyloros.tool.PylorosPingToolProvider;
import com.aresstack.pyloros.tool.ToolCatalog;
import com.aresstack.pyloros.tool.ToolNameFormatter;
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
        SecurityModule securityModule = createSecurityModule(config);

        String toolNameSeparator = new ToolNameSeparatorResolver().resolve(launchArgs);
        ToolNameFormatter toolNameFormatter = new ToolNameFormatter(toolNameSeparator);
        log.info("[MCP-TOOLS] external-name-separator={}", toolNameSeparator);

        List<ToolProvider> providers = new ArrayList<>();
        providers.add(new PylorosPingToolProvider());

        LoadedTargetPlatformModules targetPlatformModules = TargetPlatformModuleLoader.load(config.targetPlatformModules());
        providers.addAll(targetPlatformModules.toolProviders());
        providers.add(new TargetPlatformSkillsToolProvider(targetPlatformModules));

        providers.add(new com.aresstack.pyloros.tool.UserSkillDeleteToolProvider());
        registerExternalProviders(providers);

        ProviderRegistry providerRegistry = new ProviderRegistry(providers);
        ToolCatalog toolCatalog = new ToolCatalog(providerRegistry, toolNameFormatter);
        ToolRouter toolRouter = new ToolRouter(providerRegistry, toolCatalog);

        Router router = createRouter(config, securityModule, toolCatalog, toolRouter);

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

    private Router createRouter(PylorosConfig config, SecurityModule securityModule, ToolCatalog toolCatalog, ToolRouter toolRouter) {
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        new HealthRoutes().mount(router);
        securityModule.mountRoutes(router);
        new McpRoutes(config, securityModule.authenticator(), toolCatalog, toolRouter).mount(router);
        return router;
    }

    private SecurityModule createSecurityModule(PylorosConfig config) {
        if ("none".equalsIgnoreCase(config.securityMode())) {
            log.info("[SECURITY] mode=none");
            return new NoSecurityModule();
        }
        if ("oauth".equalsIgnoreCase(config.securityMode())) {
            log.info("[SECURITY] mode=oauth");
            return new OAuthSecurityModule(config);
        }
        throw new IllegalArgumentException("Unsupported security mode: " + config.securityMode());
    }

    private void registerExternalProviders(List<ToolProvider> providers) {
        McpJsonConfigLoader loader = new McpJsonConfigLoader();
        Optional<LoadedMcpJsonConfig> loaded = loader.load(launchArgs);
        if (loaded.isEmpty()) {
            log.info("[MCP-CONFIG] no mcp.json found; starting without external MCP upstreams");
            return;
        }

        LoadedMcpJsonConfig mcpJson = loaded.get();
        log.info("[MCP-CONFIG] loaded mcp.json path={} serverCount={} acpProviderCount={}",
                mcpJson.path(),
                mcpJson.config().servers() == null ? 0 : mcpJson.config().servers().size(),
                mcpJson.config().acpProviders() == null ? 0 : mcpJson.config().acpProviders().size());

        for (McpUpstreamConfig upstream : loader.resolveUpstreams(mcpJson)) {
            ToolProvider provider = createProvider(upstream);
            if (provider != null) {
                providers.add(provider);
            }
        }

        List<ToolProvider> acpProviders = AcpProviderFactory.createProviders(mcpJson.config().acpProviders(), vertx);
        providers.addAll(acpProviders);
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
