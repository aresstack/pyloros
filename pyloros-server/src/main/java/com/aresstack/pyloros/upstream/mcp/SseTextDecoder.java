package com.aresstack.pyloros.upstream.mcp;

import io.vertx.core.buffer.Buffer;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Stateful UTF-8 decoder for an SSE byte stream, with stateful line-ending normalization.
 *
 * <p>A TCP stream gives no guarantee that a multi-byte UTF-8 character arrives inside a single
 * network buffer. Decoding each buffer independently with {@code buffer.toString(UTF_8)} turns a
 * character split across two buffers into replacement characters, corrupting any JSON-RPC payload
 * containing umlauts, typographic punctuation or emoji.
 *
 * <p>The same applies to {@code \r\n} line endings: a buffer ending in {@code \r} followed by a
 * buffer starting with {@code \n} normalizes to two newlines when each is handled on its own, which
 * fabricates an event boundary and splits one SSE event into two unparseable halves.
 *
 * <p>This decoder carries both kinds of partial state across calls. It is not thread-safe; use one
 * instance per stream, which is how SSE response handlers are invoked.
 */
public final class SseTextDecoder {

    private final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE);

    /** Bytes of an incomplete character held back from the previous chunk. */
    private byte[] carry = new byte[0];

    /** True when the previous chunk ended in a carriage return whose pairing is not yet known. */
    private boolean pendingCarriageReturn;

    /**
     * Decodes the next chunk of the stream and normalizes {@code \r\n} and lone {@code \r} to
     * {@code \n}. Incomplete trailing input is retained for the following call.
     */
    public String decode(Buffer chunk) {
        return decode(chunk == null ? new byte[0] : chunk.getBytes());
    }

    public String decode(byte[] bytes) {
        return normalizeLineEndings(decodeBytes(bytes == null ? new byte[0] : bytes));
    }

    private String decodeBytes(byte[] bytes) {
        ByteBuffer input;
        if (carry.length == 0) {
            input = ByteBuffer.wrap(bytes);
        } else {
            input = ByteBuffer.allocate(carry.length + bytes.length);
            input.put(carry).put(bytes).flip();
        }

        // Worst case one char per input byte; surrogate pairs come from 4-byte sequences, so this
        // is always large enough.
        CharBuffer output = CharBuffer.allocate(input.remaining() + 1);
        CoderResult result = decoder.decode(input, output, false);
        if (result.isError()) {
            // REPLACE is configured for malformed and unmappable input, so this is unreachable in
            // practice; dropping the offending byte keeps the stream alive either way.
            input.position(Math.min(input.position() + 1, input.limit()));
        }

        carry = new byte[input.remaining()];
        input.get(carry);

        output.flip();
        return output.toString();
    }

    private String normalizeLineEndings(String raw) {
        if (raw.isEmpty() && !pendingCarriageReturn) {
            return raw;
        }

        StringBuilder out = new StringBuilder(raw.length() + 1);
        int index = 0;

        if (pendingCarriageReturn) {
            pendingCarriageReturn = false;
            out.append('\n');
            // The held-back \r was either a lone \r or the first half of \r\n; in both cases it
            // contributes exactly one newline, so swallow a leading \n here.
            if (!raw.isEmpty() && raw.charAt(0) == '\n') {
                index = 1;
            }
        }

        for (; index < raw.length(); index++) {
            char character = raw.charAt(index);
            if (character != '\r') {
                out.append(character);
                continue;
            }
            if (index + 1 >= raw.length()) {
                // Defer: the pairing \n may arrive in the next chunk.
                pendingCarriageReturn = true;
                break;
            }
            out.append('\n');
            if (raw.charAt(index + 1) == '\n') {
                index++;
            }
        }

        return out.toString();
    }
}
