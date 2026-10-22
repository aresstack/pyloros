package com.aresstack.pyloros.upstream.mcp;

import io.vertx.core.buffer.Buffer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that SSE text survives arbitrary network buffer boundaries.
 *
 * <p>The previous per-buffer {@code toString(UTF_8)} corrupted any multi-byte character split
 * across two buffers, and normalizing {@code \r\n} per buffer fabricated an event boundary when the
 * {@code \r} and {@code \n} landed in different buffers.
 */
class SseTextDecoderTest {

    private static final String PAYLOAD =
            "event: message\ndata: {\"text\":\"Grüße, Köln — 😀 \\u00fcber\"}\n\n";

    @Test
    void decodesAnUnsplitChunkUnchanged() {
        SseTextDecoder decoder = new SseTextDecoder();
        assertEquals(PAYLOAD, decoder.decode(Buffer.buffer(PAYLOAD)));
    }

    @Test
    void reassemblesMultiByteCharactersSplitAtEveryPossibleOffset() {
        byte[] bytes = PAYLOAD.getBytes(StandardCharsets.UTF_8);

        for (int split = 0; split <= bytes.length; split++) {
            SseTextDecoder decoder = new SseTextDecoder();
            StringBuilder out = new StringBuilder();
            out.append(decoder.decode(slice(bytes, 0, split)));
            out.append(decoder.decode(slice(bytes, split, bytes.length)));

            assertEquals(PAYLOAD, out.toString(), "corrupted when split at byte " + split);
            assertFalse(out.toString().contains("\uFFFD"), "replacement char at split " + split);
        }
    }

    @Test
    void reassemblesMultiByteCharactersAcrossSingleByteChunks() {
        // The pathological case: every byte arrives in its own buffer.
        byte[] bytes = PAYLOAD.getBytes(StandardCharsets.UTF_8);
        SseTextDecoder decoder = new SseTextDecoder();
        StringBuilder out = new StringBuilder();
        for (byte value : bytes) {
            out.append(decoder.decode(new byte[]{value}));
        }
        assertEquals(PAYLOAD, out.toString());
    }

    @Test
    void doesNotFabricateAnEventBoundaryWhenCrLfIsSplit() {
        SseTextDecoder decoder = new SseTextDecoder();

        // "data: a\r" + "\ndata: b\r\n\r\n" is one event, not two.
        String first = decoder.decode(Buffer.buffer("data: a\r"));
        String second = decoder.decode(Buffer.buffer("\ndata: b\r\n\r\n"));
        String combined = first + second;

        assertEquals("data: a\ndata: b\n\n", combined);
        assertEquals(1, countEventBlocks(combined).size());
        assertEquals("data: a\ndata: b", countEventBlocks(combined).get(0));
    }

    @Test
    void normalizesCrLfAndLoneCrToLf() {
        SseTextDecoder decoder = new SseTextDecoder();
        assertEquals("a\nb\nc\n\n", decoder.decode(Buffer.buffer("a\r\nb\rc\r\n\r\n")));
    }

    @Test
    void holdsBackATrailingCarriageReturnUntilItsPairingIsKnown() {
        SseTextDecoder decoder = new SseTextDecoder();

        // A trailing \r must not be emitted yet: the next chunk decides \r\n versus lone \r.
        assertEquals("a", decoder.decode(Buffer.buffer("a\r")));
        assertEquals("\nb", decoder.decode(Buffer.buffer("b")));
    }

    @Test
    void emitsLoneCarriageReturnAsNewlineWhenNextChunkIsNotLineFeed() {
        SseTextDecoder decoder = new SseTextDecoder();
        String combined = decoder.decode(Buffer.buffer("data: x\r")) + decoder.decode(Buffer.buffer("\r"));
        assertEquals("data: x\n", combined);
    }

    @Test
    void handlesEmptyAndNullChunks() {
        SseTextDecoder decoder = new SseTextDecoder();
        assertEquals("", decoder.decode(Buffer.buffer("")));
        assertEquals("", decoder.decode((Buffer) null));
        assertEquals("ok", decoder.decode(Buffer.buffer("ok")));
    }

    @Test
    void splitPayloadStillYieldsParseableEventBlocks() {
        byte[] bytes = PAYLOAD.getBytes(StandardCharsets.UTF_8);

        for (int split = 1; split < bytes.length; split++) {
            SseTextDecoder decoder = new SseTextDecoder();
            String combined = decoder.decode(slice(bytes, 0, split))
                    + decoder.decode(slice(bytes, split, bytes.length));

            List<String> blocks = countEventBlocks(combined);
            assertEquals(1, blocks.size(), "event count wrong at split " + split);
            assertTrue(blocks.get(0).contains("Grüße, Köln"), "payload corrupted at split " + split);
            assertTrue(blocks.get(0).contains("😀"), "emoji corrupted at split " + split);
        }
    }

    private static List<String> countEventBlocks(String text) {
        List<String> blocks = new ArrayList<>();
        String remaining = text;
        int separator;
        while ((separator = remaining.indexOf("\n\n")) >= 0) {
            blocks.add(remaining.substring(0, separator));
            remaining = remaining.substring(separator + 2);
        }
        return blocks;
    }

    private static byte[] slice(byte[] source, int from, int to) {
        byte[] target = new byte[to - from];
        System.arraycopy(source, from, target, 0, target.length);
        return target;
    }
}
