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
import java.util.stream.Collectors;

public final class UserSkillStore {

    private static final String STORAGE_FILE_NAME = "skills.json";

    private final ObjectMapper objectMapper;
    private final Path storageFile;

    public UserSkillStore() {
        this(PylorosUserDirectory.resolve(STORAGE_FILE_NAME));
    }

    public UserSkillStore(Path storageFile) {
        this.storageFile = storageFile;
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public Path storageFile() {
        return storageFile;
    }

    public synchronized List<TargetPlatformSkill> loadSkills() {
        return readDocument().getSkills().stream()
                .map(entry -> new TargetPlatformSkill(
                        entry.getId(),
                        entry.getTitle(),
                        entry.getDescription(),
                        entry.getText(),
                        entry.getModuleId()
                ))
                .collect(Collectors.toList());
    }

    public synchronized UserSkillEntry saveSkill(String moduleId, String id, String title, String description, String text) {
        UserSkillDocument document = readDocument();
        List<UserSkillEntry> updatedSkills = new ArrayList<>();
        String now = Instant.now().toString();
        UserSkillEntry savedSkill = null;

        for (UserSkillEntry existingSkill : document.getSkills()) {
            if (sameSkill(existingSkill, moduleId, id)) {
                savedSkill = new UserSkillEntry(
                        id,
                        title,
                        description,
                        text,
                        moduleId,
                        existingSkill.getCreatedAt() == null || existingSkill.getCreatedAt().isBlank() ? now : existingSkill.getCreatedAt(),
                        now
                );
                updatedSkills.add(savedSkill);
            } else {
                updatedSkills.add(existingSkill);
            }
        }

        if (savedSkill == null) {
            savedSkill = new UserSkillEntry(id, title, description, text, moduleId, now, now);
            updatedSkills.add(savedSkill);
        }

        writeDocument(new UserSkillDocument(updatedSkills));
        return savedSkill;
    }

    private boolean sameSkill(UserSkillEntry existingSkill, String moduleId, String id) {
        return existingSkill != null
                && Objects.equals(existingSkill.getModuleId(), moduleId)
                && Objects.equals(existingSkill.getId(), id);
    }

    private UserSkillDocument readDocument() {
        if (!Files.exists(storageFile)) {
            return new UserSkillDocument();
        }

        try {
            UserSkillDocument document = objectMapper.readValue(storageFile.toFile(), UserSkillDocument.class);
            return document == null ? new UserSkillDocument() : document;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read user skills", exception);
        }
    }

    private void writeDocument(UserSkillDocument document) {
        try {
            Path parent = storageFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            objectMapper.writeValue(storageFile.toFile(), document == null ? new UserSkillDocument() : document);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write user skills", exception);
        }
    }
}
