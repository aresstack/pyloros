package com.aresstack.pyloros.config;

import com.aresstack.pyloros.langchain.LangChainExecutionConfiguration;
import com.aresstack.pyloros.langchain.LangChainProviderConfiguration;
import com.aresstack.pyloros.langchain.LangChainToolViewValidator;
import com.aresstack.pyloros.langchain.OllamaConfiguration;
import com.aresstack.pyloros.provider.ProviderDescriptor;
import com.aresstack.pyloros.provider.ProviderType;
import com.aresstack.pyloros.tool.ToolView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Creates LangChain provider descriptors from mcp.json configuration.
 * Invalid configurations are logged but do not prevent server startup.
 */
public final class LangChainProviderFactory {

    private static final Logger log = LoggerFactory.getLogger(LangChainProviderFactory.class);

    private LangChainProviderFactory() {
    }

    public static List<LangChainProviderConfiguration> createConfigurations(List<LangChainProviderJsonConfig> configs) {
        if (configs == null || configs.isEmpty()) {
            return List.of();
        }

        List<LangChainProviderConfiguration> results = new ArrayList<>();
        Set<String> allProviderIds = collectProviderIds(configs);

        for (LangChainProviderJsonConfig jsonConfig : configs) {
            try {
                LangChainProviderConfiguration providerConfig = createConfiguration(jsonConfig, allProviderIds);
                results.add(providerConfig);
                log.info("[LANGCHAIN-PROVIDER] registered provider={} prefix={} llmAgentToolView={} model={}",
                        providerConfig.id(), providerConfig.prefix(), providerConfig.llmAgentToolView(),
                        providerConfig.ollama().model());
            } catch (Exception exception) {
                log.error("[LANGCHAIN-PROVIDER] failed to create provider={} reason={}",
                        jsonConfig.id(), exception.getMessage());
            }
        }

        return results;
    }

    public static List<ProviderDescriptor> createDescriptors(List<LangChainProviderConfiguration> configs) {
        List<ProviderDescriptor> descriptors = new ArrayList<>();
        for (LangChainProviderConfiguration config : configs) {
            List<ToolView> views = config.exposeInViews().stream()
                    .map(ToolView::named)
                    .toList();
            descriptors.add(new ProviderDescriptor(config.id(), ProviderType.LANGCHAIN, views, false));
        }
        return descriptors;
    }

    private static LangChainProviderConfiguration createConfiguration(LangChainProviderJsonConfig jsonConfig, Set<String> allProviderIds) {
        validateType(jsonConfig);
        LangChainProviderConfiguration providerConfig = toProviderConfiguration(jsonConfig);
        LangChainToolViewValidator.validate(providerConfig, allProviderIds);
        return providerConfig;
    }

    private static void validateType(LangChainProviderJsonConfig jsonConfig) {
        if (jsonConfig.type() == null || !jsonConfig.type().equalsIgnoreCase("langchain")) {
            throw new IllegalArgumentException("LangChain provider type must be 'langchain', got: " + jsonConfig.type());
        }
        if (jsonConfig.id() == null || jsonConfig.id().isBlank()) {
            throw new IllegalArgumentException("LangChain provider id must not be blank");
        }
        if (jsonConfig.llmAgentToolView() == null || jsonConfig.llmAgentToolView().isBlank()) {
            throw new IllegalArgumentException("LangChain provider llmAgentToolView is required");
        }
    }

    private static LangChainProviderConfiguration toProviderConfiguration(LangChainProviderJsonConfig jsonConfig) {
        OllamaConfiguration ollamaConfig = toOllamaConfig(jsonConfig.ollama());
        LangChainExecutionConfiguration executionConfig = toExecutionConfig(jsonConfig.execution());

        String prefix = jsonConfig.prefix() != null ? jsonConfig.prefix() : jsonConfig.id() + "/";
        List<String> exposeInViews = jsonConfig.exposeInViews() != null ? jsonConfig.exposeInViews() : List.of("public");

        return new LangChainProviderConfiguration(
                jsonConfig.id(),
                prefix,
                jsonConfig.llmAgentToolView(),
                exposeInViews,
                ollamaConfig,
                executionConfig
        );
    }

    private static OllamaConfiguration toOllamaConfig(LangChainProviderJsonConfig.OllamaJsonConfig ollama) {
        if (ollama == null) {
            return new OllamaConfiguration();
        }
        URI baseUrl = ollama.baseUrl() != null && !ollama.baseUrl().isBlank()
                ? URI.create(ollama.baseUrl())
                : OllamaConfiguration.DEFAULT_BASE_URL;
        String model = ollama.model();
        return new OllamaConfiguration(baseUrl, model);
    }

    private static LangChainExecutionConfiguration toExecutionConfig(LangChainProviderJsonConfig.LangChainExecutionJsonConfig exec) {
        if (exec == null) {
            return new LangChainExecutionConfiguration();
        }
        return new LangChainExecutionConfiguration(
                exec.maxToolCalls() != null ? exec.maxToolCalls() : 8,
                exec.maxRuntimeSeconds() != null ? exec.maxRuntimeSeconds() : 120,
                exec.maxToolResultChars() != null ? exec.maxToolResultChars() : 12000,
                exec.maxModelRetries() != null ? exec.maxModelRetries() : 1
        );
    }

    private static Set<String> collectProviderIds(List<LangChainProviderJsonConfig> configs) {
        Set<String> ids = new HashSet<>();
        for (LangChainProviderJsonConfig config : configs) {
            if (config.id() != null && !config.id().isBlank()) {
                ids.add(config.id());
            }
        }
        return ids;
    }
}
