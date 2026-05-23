package com.aresstack.pyloros.extension;
    
    import com.aresstack.pyloros.tool.ToolProvider;
    
    import java.util.ArrayList;
    import java.util.List;
    
    public final class LoadedTargetPlatformModules {
    
        private final List<TargetPlatformModule> modules;
    
        public LoadedTargetPlatformModules(List<TargetPlatformModule> modules) {
            this.modules = List.copyOf(modules == null ? List.of() : modules);
        }
    
        public List<TargetPlatformModule> modules() {
            return modules;
        }
    
        public List<ToolProvider> toolProviders() {
            List<ToolProvider> providers = new ArrayList<>();
            for (TargetPlatformModule module : modules) {
                providers.addAll(module.toolProviders());
            }
            return List.copyOf(providers);
        }
    
        public List<TargetPlatformSkill> skills() {
            List<TargetPlatformSkill> skills = new ArrayList<>();
            for (TargetPlatformModule module : modules) {
                skills.addAll(module.skills());
            }
            return List.copyOf(skills);
        }
    }
    