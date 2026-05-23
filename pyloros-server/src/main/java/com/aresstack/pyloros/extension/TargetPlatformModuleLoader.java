package com.aresstack.pyloros.extension;
    
    import org.slf4j.Logger;
    import org.slf4j.LoggerFactory;
    
    import java.util.LinkedHashSet;
    import java.util.List;
    import java.util.ServiceLoader;
    import java.util.Set;
    import java.util.stream.Collectors;
    
    public final class TargetPlatformModuleLoader {
    
        private static final Logger log = LoggerFactory.getLogger(TargetPlatformModuleLoader.class);
    
        private TargetPlatformModuleLoader() {
        }
    
        public static LoadedTargetPlatformModules load(String enabledModuleNames) {
            Set<String> enabled = parseEnabledModuleNames(enabledModuleNames);
            if (enabled.isEmpty()) {
                log.info("[TARGET-PLATFORMS] no target platform modules enabled");
                return new LoadedTargetPlatformModules(List.of());
            }
    
            List<TargetPlatformModule> modules = ServiceLoader.load(TargetPlatformModule.class).stream()
                    .map(ServiceLoader.Provider::get)
                    .filter(module -> enabled.contains(module.moduleId()))
                    .collect(Collectors.toList());
    
            log.info("[TARGET-PLATFORMS] enabled={} loaded={}", enabled, modules.stream()
                    .map(TargetPlatformModule::moduleId)
                    .collect(Collectors.toList()));
    
            return new LoadedTargetPlatformModules(modules);
        }
    
        private static Set<String> parseEnabledModuleNames(String enabledModuleNames) {
            LinkedHashSet<String> names = new LinkedHashSet<>();
            if (enabledModuleNames == null || enabledModuleNames.isBlank()) {
                return names;
            }
    
            for (String name : enabledModuleNames.split(",")) {
                String trimmed = name.trim();
                if (!trimmed.isBlank()) {
                    names.add(trimmed);
                }
            }
            return names;
        }
    }
    