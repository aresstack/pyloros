package com.aresstack.pyloros.extension;

public final class UserSkillEntry {

    private String id;
    private String title;
    private String description;
    private String text;
    private String moduleId;
    private String createdAt;
    private String updatedAt;

    public UserSkillEntry() {
    }

    public UserSkillEntry(String id, String title, String description, String text, String moduleId, String createdAt, String updatedAt) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.text = text;
        this.moduleId = moduleId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getModuleId() {
        return moduleId;
    }

    public void setModuleId(String moduleId) {
        this.moduleId = moduleId;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
