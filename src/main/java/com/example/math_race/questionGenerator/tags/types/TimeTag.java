package com.example.math_race.questionGenerator.tags.types;

import com.example.math_race.questionGenerator.question.WarningContext;
import com.example.math_race.questionGenerator.tags.core.TemplateTag;

public class TimeTag implements TemplateTag {

    private int totalMinutes;
    private int minMinutes;
    private int maxMinutes;
    private  boolean round;

    public TimeTag(String valStr, int minMinutes, int maxMinutes, boolean round) {
        this.minMinutes = minMinutes;
        this.maxMinutes = maxMinutes;
        this.round = round;

        if (valStr == null || valStr.equals("?")) {
            this.totalMinutes = generateRandomTime(round, -1);
        } else if (valStr.startsWith("!")) {
            try {
                int forbiddenValue = parseTime(valStr.substring(1));
                if (this.minMinutes == this.maxMinutes && this.minMinutes == forbiddenValue) {
                    this.totalMinutes = this.minMinutes;
                } else {
                    this.totalMinutes = generateRandomTime(round, forbiddenValue);
                }
            } catch (Exception e) {
                WarningContext.addWarning("\u001B[31m" + "Warning: Invalid forbidden time format ('" + valStr + "'). Generating random time." + "\u001B[0m");
                this.totalMinutes = generateRandomTime(round, -1);
            }
        } else {
            try {
                this.totalMinutes = parseTime(valStr);
            } catch (Exception e) {
                WarningContext.addWarning("\u001B[31m" + "Warning: Invalid time format ('" + valStr + "'). Generating random time." + "\u001B[0m");
                this.totalMinutes = generateRandomTime(round, -1);
            }
        }
    }

    @Override
    public String getProperty(String key) {
        if (key == null || key.trim().isEmpty()) return formatTime(totalMinutes);
        if (key.equals("*")) return "";

        String normalizedKey = key.trim().toLowerCase();

        switch (normalizedKey) {
            case "min" -> { return formatTime(minMinutes); }
            case "max" -> { return formatTime(maxMinutes); }
            case "r","round" -> { return String.valueOf(round); }
            case "v", "val", "value", "t", "time" -> { return formatTime(totalMinutes); }
        }

        int currentResult = totalMinutes;

        try {
            if (normalizedKey.startsWith("add_m_")) {
                int add = Integer.parseInt(normalizedKey.substring(6));
                currentResult = (currentResult + add) % 1440;
                return formatTime(currentResult);

            } else if (normalizedKey.startsWith("sub_m_")) {
                int sub = Integer.parseInt(normalizedKey.substring(6));
                currentResult = (currentResult - sub) % 1440;
                if (currentResult < 0) currentResult += 1440;
                return formatTime(currentResult);

            } else if (normalizedKey.startsWith("add_h_")) {
                int add = Integer.parseInt(normalizedKey.substring(6));
                currentResult = (currentResult + add * 60) % 1440;
                return formatTime(currentResult);

            } else if (normalizedKey.startsWith("sub_h_")) {
                int sub = Integer.parseInt(normalizedKey.substring(6));
                currentResult = (currentResult - sub * 60) % 1440;
                if (currentResult < 0) currentResult += 1440;
                return formatTime(currentResult);
            }

        } catch (NumberFormatException e) {
            WarningContext.addWarning("\u001B[31m" + "Warning: Invalid number in time operation in TimeTag: '" + key + "'\u001B[0m");
            return formatTime(totalMinutes);
        }

        WarningContext.addWarning("\u001B[31m" + "Warning: Unrecognized property key in TimeTag.getProperty: '" + key + "'\u001B[0m");
        return formatTime(totalMinutes);
    }

    private int generateRandomTime(boolean round, int forbiddenValue) {
        int randomMins;
        int attempts = 0;
        do {
            randomMins = java.util.concurrent.ThreadLocalRandom.current().nextInt(minMinutes, maxMinutes + 1);

            if (round) {
                randomMins = Math.round(randomMins / 5.0f) * 5;
                if (randomMins > maxMinutes) randomMins = maxMinutes;
                if (randomMins < minMinutes) randomMins = minMinutes;
            }

            attempts++;
            if (attempts > 100) break;

        } while (randomMins == forbiddenValue);

        return randomMins;
    }

    public static int parseTime(String timeStr) {
        String[] parts = timeStr.trim().split("[:.]");
        int h = Integer.parseInt(parts[0]);
        int m = Integer.parseInt(parts[1]);
        return h * 60 + m;
    }

    public static String formatTime(int mins) {
        int h = (mins / 60) % 24;
        int m = mins % 60;
        return String.format("%02d:%02d", h, m);
    }
}
