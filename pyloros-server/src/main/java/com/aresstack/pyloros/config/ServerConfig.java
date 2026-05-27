package com.aresstack.pyloros.config;
    
    public interface ServerConfig {
    
        String mcpPublicPath();
    
        String mcpProtocolVersion();

        /**
         * Shared secret used by Pyloros to authorize injected non-public tool-view requests
         * (e.g. the manager-agent calling back through the MCP endpoint with {@code ?view=agent}).
         * <p>
         * The token must be unguessable and never exposed to external clients. When the value is
         * blank, McpRoutes refuses every non-public view, regardless of any other request header.
         */
        default String mcpInjectedViewToken() {
            return "";
        }
    }
    