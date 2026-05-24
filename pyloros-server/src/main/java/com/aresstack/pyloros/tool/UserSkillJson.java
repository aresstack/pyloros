package com.aresstack.pyloros.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class UserSkillJson {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private UserSkillJson() {
    }

    public static boolean remove(Path path, String moduleId, String id) {
        if (!Files.exists(path)) {
            return false;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(path.toFile());
            if (!(root instanceof ObjectNode document)) {
                return false;
            }
            JsonNode existingItems = document.get("items");
            if (!(existingItems instanceof ArrayNode items)) {
                return false;
            }

            ArrayNode remainingItems = OBJECT_MAPPER.createArrayNode();
            boolean removed = false;
            for (JsonNode item : items) {
                if (sameItem(item, moduleId, id)) {
                    removed = true;
                } else {
                    remainingItems.add(item);
                }
            }
            if (removed) {
                document.set("items", remainingItems);
                OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), document);
            }
            return removed;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not update user skills file " + path, exception);
        }
    }

    private static boolean sameItem(JsonNode item, String moduleId, String id) {
        return id.equals(item.path("id").asText()) && moduleId.equals(item.path("moduleId").asText());
    }
}
