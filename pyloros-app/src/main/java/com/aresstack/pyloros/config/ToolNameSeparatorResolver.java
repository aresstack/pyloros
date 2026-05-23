package com.aresstack.pyloros.config;

import com.aresstack.pyloros.tool.ToolNameFormatter;

public final class ToolNameSeparatorResolver {

    private static final String CLI_PREFIX = "--tool-name-separator=";
    private static final String PROPERTY_NAME = "mcp.tool-name-separator";

    public String resolve(String[] args) {
        String fromArgs = fromArgs(args);
        if (fromArgs != null) {
            return fromArgs;
        }

        String fromProperty = System.getProperty(PROPERTY_NAME);
        if (fromProperty != null && !fromProperty.isBlank()) {
            return fromProperty;
        }

        return ToolNameFormatter.DEFAULT_SEPARATOR;
    }

    private String fromArgs(String[] args) {
        if (args == null) {
            return null;
        }
        for (String arg : args) {
            if (arg != null && arg.startsWith(CLI_PREFIX)) {
                String value = arg.substring(CLI_PREFIX.length());
                if (!value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }
}

