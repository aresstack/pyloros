package com.aresstack.pyloros.extension;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class PylorosUserDirectory {

    private static final String DIRECTORY_NAME = ".pyloros";

    private PylorosUserDirectory() {
    }

    public static Path resolve(String fileName) {
        return Paths.get(System.getProperty("user.home"), DIRECTORY_NAME).resolve(fileName);
    }
}
