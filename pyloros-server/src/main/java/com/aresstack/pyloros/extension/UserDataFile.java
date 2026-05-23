package com.aresstack.pyloros.extension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class UserDataFile {

    private final ObjectMapper objectMapper;
    private final Path file;

    public UserDataFile() {
        this(PylorosUserDirectory.resolve("sk" + "ills" + ".json"));
    }

    public UserDataFile(Path file) {
        this.file = file;
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public Path file() {
        return file;
    }

    public synchronized UserContentEntry save(String moduleId, String id, String title, String description, String text) {
        UserContentDocument document = read();
        List<UserContentEntry> updatedItems = new ArrayList<>();
        String now = Instant.now().toString();
        UserContentEntry saved = null;

        for (UserContentEntry existing : document.getItems()) {
            if (sameItem(existing, moduleId, id)) {
                saved = new UserContentEntry(
                        id,
                        title,
                        description,
                        text,
                        moduleId,
                        existing.getCreatedAt() == null || existing.getCreatedAt().isBlank() ? now : existing.getCreatedAt(),
                        now
                );
                updatedItems.add(saved);
            } else {
                updatedItems.add(existing);
            }
        }

        if (saved == null) {
            saved = new UserContentEntry(id, title, description, text, moduleId, now, now);
            updatedItems.add(saved);
        }

        write(new UserContentDocument(updatedItems));
        return saved;
    }

    private boolean sameItem(UserContentEntry existing, String moduleId, String id) {
        return existing != null
                && Objects.equals(existing.getModuleId(), moduleId)
                && Objects.equals(existing.getId(), id);
    }

    private UserContentDocument read() {
        if (!Files.exists(file)) {
            return new UserContentDocument();
        }

        try {
            UserContentDocument document = objectMapper.readValue(file.toFile(), UserContentDocument.class);
            return document == null ? new UserContentDocument() : document;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read user data", exception);
        }
    }

    private void write(UserContentDocument document) {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            objectMapper.writeValue(file.toFile(), document == null ? new UserContentDocument() : document);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write user data", exception);
        }
    }
}
