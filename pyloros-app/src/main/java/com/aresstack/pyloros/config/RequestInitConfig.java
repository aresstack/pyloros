package com.aresstack.pyloros.config;

import java.util.Map;

public record RequestInitConfig(
        Map<String, String> headers
) {
}
