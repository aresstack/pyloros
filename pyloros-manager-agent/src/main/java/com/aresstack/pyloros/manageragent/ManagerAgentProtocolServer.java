package com.aresstack.pyloros.manageragent;

import com.agentclientprotocol.sdk.error.AcpErrorCodes;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

final class ManagerAgentProtocolServer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final LineDelimitedJsonRpcTransport transport;
    private final ManagerAgentJsonRpcDispatcher dispatcher;

    ManagerAgentProtocolServer(InputStream inputStream, OutputStream outputStream) {
        this(new LineDelimitedJsonRpcTransport(OBJECT_MAPPER, inputStream, outputStream),
                new ManagerAgentJsonRpcDispatcher(new ManagerAgentHandshakeHandler(OBJECT_MAPPER, new ManagerAgentSessionState())));
    }

    ManagerAgentProtocolServer(LineDelimitedJsonRpcTransport transport, ManagerAgentJsonRpcDispatcher dispatcher) {
        this.transport = Objects.requireNonNull(transport, "transport must not be null");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher must not be null");
    }

    void run() throws IOException {
        String line;
        while ((line = transport.readLine()) != null) {
            handleLine(line);
        }
    }

    private void handleLine(String line) throws IOException {
        JsonNode request;
        try {
            request = OBJECT_MAPPER.readTree(line);
        } catch (JsonProcessingException exception) {
            transport.sendError(null, AcpErrorCodes.PARSE_ERROR, "Parse error");
            return;
        }
        dispatcher.dispatch(request, transport);
    }
}
