package com.aresstack.pyloros.extension;

import java.util.ArrayList;
import java.util.List;

public final class UserContentDocument {

    private List<UserContentEntry> items = new ArrayList<>();

    public UserContentDocument() {
    }

    public UserContentDocument(List<UserContentEntry> items) {
        setItems(items);
    }

    public List<UserContentEntry> getItems() {
        return items;
    }

    public void setItems(List<UserContentEntry> items) {
        this.items = items == null ? new ArrayList<>() : new ArrayList<>(items);
    }
}
