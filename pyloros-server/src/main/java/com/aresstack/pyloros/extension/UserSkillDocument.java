package com.aresstack.pyloros.extension;

import java.util.ArrayList;
import java.util.List;

public final class UserSkillDocument {

    private List<UserSkillEntry> skills = new ArrayList<>();

    public UserSkillDocument() {
    }

    public UserSkillDocument(List<UserSkillEntry> skills) {
        setSkills(skills);
    }

    public List<UserSkillEntry> getSkills() {
        return skills;
    }

    public void setSkills(List<UserSkillEntry> skills) {
        this.skills = skills == null ? new ArrayList<>() : new ArrayList<>(skills);
    }
}
