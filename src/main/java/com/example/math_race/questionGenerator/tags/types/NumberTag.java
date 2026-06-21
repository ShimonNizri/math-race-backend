package com.example.math_race.questionGenerator.tags.types;

import com.example.math_race.questionGenerator.question.WarningContext;
import com.example.math_race.questionGenerator.tags.core.TemplateTag;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class NumberTag implements TemplateTag {
    private int value;
    private int min;
    private int max;

    public NumberTag(String valStr, int min, int max) {
        this.min = min;
        this.max = max;

        if (valStr == null || valStr.equals("?")) {
            this.value = java.util.concurrent.ThreadLocalRandom.current().nextInt(this.min, this.max + 1);
        } else if (valStr.startsWith("!")) {
            try {
                int forbiddenValue = Integer.parseInt(valStr.substring(1));
                if (this.min == this.max && this.min == forbiddenValue) {
                    this.value = this.min;
                } else {
                    int randomNumber;
                    do {
                        randomNumber = java.util.concurrent.ThreadLocalRandom.current().nextInt(this.min, this.max + 1);
                    } while (randomNumber == forbiddenValue);
                    this.value = randomNumber;
                }
            } catch (NumberFormatException e) {
                WarningContext.addWarning("\u001B[31m" + "Warning: Invalid forbidden value format ('" + valStr + "'). Generating random number." + "\u001B[0m");
                this.value = java.util.concurrent.ThreadLocalRandom.current().nextInt(this.min, this.max + 1);
            }
        } else {
            try {
                this.value = Integer.parseInt(valStr);
            } catch (NumberFormatException e) {
                WarningContext.addWarning("\u001B[31m" + "Warning: Invalid value format ('" + valStr + "'). Generating random number." + "\u001B[0m");
                this.value = java.util.concurrent.ThreadLocalRandom.current().nextInt(this.min, this.max + 1);
            }
        }
    }

    @Override
    public String getProperty(String key) {
        if (key == null || key.trim().isEmpty()) return String.valueOf(value);
        if (key.equals("*")) return "";

        String normalizedKey = key.trim().toLowerCase();

        switch (normalizedKey) {
            case "min" -> { return String.valueOf(min); }
            case "max" -> { return String.valueOf(max); }
            case "abs" -> { return String.valueOf(Math.abs(value)); }
            case "value", "v" -> { return String.valueOf(value); }
        }

        try {
            if (normalizedKey.startsWith("mul_")) {
                int factor = Integer.parseInt(normalizedKey.substring(4));
                return String.valueOf(value * factor);
            }
            if (normalizedKey.startsWith("add_")) {
                int bonus = Integer.parseInt(normalizedKey.substring(4));
                return String.valueOf(value + bonus);
            }
            if (normalizedKey.startsWith("sub_")) {
                int bonus = Integer.parseInt(normalizedKey.substring(4));
                return String.valueOf(value - bonus);
            }
            if (normalizedKey.startsWith("div_")) {
                int divisor = Integer.parseInt(normalizedKey.substring(4));
                if (divisor != 0) {
                    return String.valueOf(value / divisor);
                } else {
                    WarningContext.addWarning("\u001B[31m" + "Warning: Division by zero requested in NumberTag: '" + key + "'\u001B[0m");
                    return String.valueOf(value);
                }
            }
            if (normalizedKey.startsWith("mod_")) {
                int divisor = Integer.parseInt(normalizedKey.substring(4));
                if (divisor != 0) {
                    return String.valueOf(value % divisor);
                } else {
                    WarningContext.addWarning("\u001B[31m" + "Warning: Modulo by zero requested in NumberTag: '" + key + "'\u001B[0m");
                    return String.valueOf(value);
                }
            }
        } catch (NumberFormatException e) {
            WarningContext.addWarning("\u001B[31m" + "Warning: Invalid number in operation in NumberTag.getProperty: '" + key + "'\u001B[0m");
            return String.valueOf(value);
        }

        WarningContext.addWarning("\u001B[31m" + "Warning: Unrecognized property key in NumberTag.getProperty: '" + key + "'\u001B[0m");
        return String.valueOf(value);
    }
}
