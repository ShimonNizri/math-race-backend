package com.example.math_race.questionGenerator.question;

import java.util.ArrayList;
import java.util.List;

public class WarningContext {

    private static final ThreadLocal<List<String>> WARNINGS = ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    public static void addWarning(String warning) {
        WARNINGS.get().add(warning);
    }

    public static void enter() {
        DEPTH.set(DEPTH.get() + 1);
    }

    public static List<String> exit() {
        int currentDepth = DEPTH.get() - 1;
        DEPTH.set(currentDepth);

        if (currentDepth == 0) {
            List<String> collectedWarnings = new ArrayList<>(WARNINGS.get());

            DEPTH.remove();
            WARNINGS.remove();

            return collectedWarnings;
        }

        return null;
    }
}
