package com.aresstack.pyloros.refactoring;

final class RefactoringSkillProbe {
    String formatGreeting(String name) {
        String normalized = name == null ? "anonymous" : name.trim();
        String prefix = "Hello, ";
        return prefix + normalized;
    }
}
