package com.aresstack.pyloros;

import com.aresstack.pyloros.acp.AcpProviderConfiguration;
import com.aresstack.pyloros.acp.AcpVirtualToolProvider;
import com.aresstack.pyloros.acp.InMemoryAgentTaskRepository;
import com.aresstack.pyloros.acp.registry.AcpRegistryCache;
import com.aresstack.pyloros.acp.registry.AcpRegistryClient;
import com.aresstack.pyloros.acp.registry.AcpRegistryManagementToolProvider;
import com.aresstack.pyloros.acp.registry.AgentLifecycleService;
import com.aresstack.pyloros.acp.registry.DefaultAgentInstaller;
import com.aresstack.pyloros.acp.registry.DistributionResolver;
import com.aresstack.pyloros.acp.registry.InstalledAgentAcpProviderFactory;
import com.aresstack.pyloros.acp.registry.InstalledAgentStore;
import com.aresstack.pyloros.acp.registry.InstalledAgentStoreException;
import com.aresstack.pyloros.config.AcpProviderFactory;
import com.aresstack.pyloros.config.AcpProviderJsonConfig;
import com.aresstack.pyloros.config.LoadedMcpJsonConfig;
import com.aresstack.pyloros.config.McpJsonConfigLoader;
import com.aresstack.pyloros.config.PylorosConfig;
import com.aresstack.pyloros.config.ToolNameSeparatorResolver;
import com.aresstack.pyloros.extension.LoadedTargetPlatformModules;
import com.aresstack.pyloros.extension.TargetPlatformModuleLoader;
import com.aresstack.pyloros.extension.TargetPlatformSkillsToolProvider;
import com.aresstack.pyloros.http.HealthRoutes;
import com.aresstack.pyloros.http.McpRoutes;
import com.aresstack.pyloros.plugin.PluginActivationResolver;
import com.aresstack.pyloros.plugin.PluginLoadResult;
import com.aresstack.pyloros.plugin.PluginRegistry;
import com.aresstack.pyloros.plugin.PluginsConfig;
import com.aresstack.pyloros.plugin.PluginsConfigLoader;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
        loadPlugins(providers);
        registerExternalProviders(providers, config);
        registerAcpRegistryProviders(providers, config);

        ProviderRegistry providerRegistry = new ProviderRegistry(providers);
        ToolCatalog toolCatalog = new ToolCatalog(providerRegistry, toolNameFormatter);
        ToolRouter toolRouter = new ToolRouter(providerRegistry, toolCatalog,
                config.enforceToolScopes());

        Router router = createRouter(config, securityModule, toolCatalog, toolRouter);

        vertx.createHttpServer()
                .requestHandler(router)
                .listen(config.serverPort(), config.serverHost())
                .onSuccess(server -> log.info(
                        "Pyloros listening on port {} with public MCP URL {}/",
                        config.serverPort(),
                        config.publicOrigin()
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
        if ("central-oauth".equalsIgnoreCase(config.securityMode())) {
            log.info("[SECURITY] mode=central-oauth issuer={}", config.centralOauthIssuer());
            return new com.aresstack.pyloros.oauth.CentralOAuthSecurityModule(config);
        }
        throw new IllegalArgumentException("Unsupported security mode '" + config.securityMode()
                + "'. Use 'central-oauth' (requires CENTRAL_OAUTH_ISSUER) or 'none'.");
    }

    private void loadPlugins(List<ToolProvider> providers) {
        PluginRegistry registry = loadPluginRegistry();
        for (PluginLoadResult result : registry.results()) {
            log.info("[PLUGIN] id={} status={}", result.pluginId(), result.status());
        }
        if (!registry.contributedProviders().isEmpty()) {
            log.info("[PLUGIN] {} provider(s) contributed by plugins",
                    registry.contributedProviders().size());
            providers.addAll(registry.contributedProviders());
        }
    }

    PluginRegistry loadPluginRegistry() {
        PluginsConfig pluginsConfig = loadPluginsConfig();
        return PluginRegistry.load(new PluginActivationResolver(pluginsConfig));
    }

    private PluginsConfig loadPluginsConfig() {
        Optional<LoadedMcpJsonConfig> loaded = new McpJsonConfigLoader().load(launchArgs);
        if (loaded.isEmpty()) {
            return PluginsConfig.empty();
        }

        LoadedMcpJsonConfig mcpJson = loaded.get();
        PluginsConfigLoader loader = new PluginsConfigLoader();
        try (java.io.InputStream inputStream = java.nio.file.Files.newInputStream(mcpJson.path())) {
            return loader.loadFromStream(inputStream);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load plugin configuration from " + mcpJson.path(), exception);
        }
    }

    private void registerExternalProviders(List<ToolProvider> providers, PylorosConfig config) {
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

        List<ToolProvider> acpProviders = AcpProviderFactory.createProviders(
                mcpJson.config().acpProviders(), vertx, config.mcpInjectedViewToken());
        providers.addAll(acpProviders);
    }

    private void registerAcpRegistryProviders(List<ToolProvider> providers, PylorosConfig config) {
        try {
            Path dataDir = Paths.get("data");
            Files.createDirectories(dataDir);

            AcpRegistryCache registryCache = new AcpRegistryCache(dataDir.resolve("acp-registry-cache.json"));
            AcpRegistryClient registryClient = new AcpRegistryClient(AcpRegistryClient.defaultRegistryUrl(), registryCache);
            InstalledAgentStore store = new InstalledAgentStore(dataDir);
            DistributionResolver resolver = new DistributionResolver();
            Path agentInstallDir = dataDir.resolve("agents");
            Files.createDirectories(agentInstallDir);
            DefaultAgentInstaller installer = new DefaultAgentInstaller(agentInstallDir);
            AgentLifecycleService lifecycleService = new AgentLifecycleService(store, resolver, installer);

            providers.add(new AcpRegistryManagementToolProvider(registryClient, lifecycleService, store));
            log.info("[ACP-MGMT] registered ACP registry management tools");

            registerInstalledAcpAgents(providers, config, store);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to initialize ACP registry providers", exception);
        }
    }

    private void registerInstalledAcpAgents(List<ToolProvider> providers, PylorosConfig config, InstalledAgentStore store) {
        List<AcpProviderJsonConfig> externalAcpProviders = loadConfiguredAcpProviders();
        InstalledAgentAcpProviderFactory.Result result;
        try {
            result = InstalledAgentAcpProviderFactory.createConfigurations(
                    store.listAll(),
                    collectAcpProviderIds(externalAcpProviders),
                    collectAcpProviderIdsByExposedView(externalAcpProviders),
                    config.publicOrigin() + "/",
                    config.mcpInjectedViewToken()
            );
        } catch (InstalledAgentStoreException exception) {
            log.warn("[ACP-REGISTRY-PROVIDER] skipped installed registry agents because the installed-agent store could not be read: {}",
                    exception.getMessage());
            return;
        }

        for (AcpProviderConfiguration providerConfiguration : result.configurations()) {
            providers.add(new AcpVirtualToolProvider(
                    vertx,
                    providerConfiguration,
                    new InMemoryAgentTaskRepository(),
                    config.mcpInjectedViewToken()
            ));
        }
        for (InstalledAgentAcpProviderFactory.AgentFailure failure : result.failures()) {
            log.warn("[ACP-REGISTRY-PROVIDER] skipped installed agent={} reason={}",
                    failure.agentId(), failure.reason());
        }
        if (!result.configurations().isEmpty()) {
            log.info("[ACP-REGISTRY-PROVIDER] registered {} installed ACP registry provider(s)",
                    result.configurations().size());
        }
    }

    private List<AcpProviderJsonConfig> loadConfiguredAcpProviders() {
        Optional<LoadedMcpJsonConfig> loaded = new McpJsonConfigLoader().load(launchArgs);
        if (loaded.isEmpty() || loaded.get().config().acpProviders() == null) {
            return List.of();
        }
        return loaded.get().config().acpProviders();
    }

    private static Set<String> collectAcpProviderIds(List<AcpProviderJsonConfig> configs) {
        Set<String> ids = new HashSet<>();
        for (AcpProviderJsonConfig config : configs == null ? List.<AcpProviderJsonConfig>of() : configs) {
            if (config != null
                    && config.id() != null
                    && !config.id().isBlank()
                    && config.type() != null
                    && config.type().equalsIgnoreCase("acp")
                    && config.process() != null
                    && config.process().command() != null
                    && !config.process().command().isBlank()) {
                ids.add(config.id().trim());
            }
        }
        return ids;
    }

    private static Map<String, Set<String>> collectAcpProviderIdsByExposedView(List<AcpProviderJsonConfig> configs) {
        Map<String, Set<String>> idsByView = new HashMap<>();
        for (AcpProviderJsonConfig config : configs == null ? List.<AcpProviderJsonConfig>of() : configs) {
            if (config == null || config.id() == null || config.id().isBlank()) {
                continue;
            }
            String providerId = config.id().trim();
            List<String> views = config.exposeInViews() != null ? config.exposeInViews() : List.of("public");
            for (String view : views) {
                if (view != null && !view.isBlank()) {
                    idsByView.computeIfAbsent(view.trim(), ignored -> new HashSet<>()).add(providerId);
                }
            }
        }
        return idsByView;
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
