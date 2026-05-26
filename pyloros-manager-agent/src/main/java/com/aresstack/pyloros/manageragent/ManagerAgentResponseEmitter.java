package com.aresstack.pyloros.manageragent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;

interface ManagerAgentResponseEmitter {

    void sendResult(JsonNode id, JsonNode result) throws IOException;

    void sendError(JsonNode id, int code, String message) throws IOException;

    void sendSessionUpdate(ObjectNode params) throws IOException;
}
