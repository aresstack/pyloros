package com.aresstack.pyloros.steroidprobe;

final class InlineMethodSteroidProbe {
    String render(String name) {
        return greeting(name);
    }

    private String greeting(String name) {
        return "Hello, " + name;
    }
}
