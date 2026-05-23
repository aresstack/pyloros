package com.aresstack.pyloros.http;

import io.vertx.ext.web.Router;

import java.util.Map;

public final class HealthRoutes {

    public void mount(Router router) {
        router.get("/health").handler(context -> HttpJson.send(context, 200, Map.of("status", "ok")));
    }
}

