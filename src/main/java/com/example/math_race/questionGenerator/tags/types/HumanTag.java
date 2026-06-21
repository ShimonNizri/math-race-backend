package com.example.math_race.questionGenerator.tags.types;

import com.example.math_race.entities.dictionary.HumanEntity;
import com.example.math_race.questionGenerator.question.WarningContext;
import com.example.math_race.questionGenerator.tags.core.MatchableTag;
import com.example.math_race.questionGenerator.tags.enums.Gender;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

import static com.example.math_race.questionGenerator.tags.types.TagUtils.matchComplexExpression;
import static com.example.math_race.questionGenerator.tags.types.TagUtils.matchComplexStringExpression;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HumanTag implements MatchableTag {
    private String name;
    private Gender gender;

    public HumanTag(HumanEntity entity) {
        this.name = entity.getName();
        this.gender = entity.getGender();
    }

    @Override
    public String getProperty(String key) {
        if (key == null || key.isEmpty()) return name;
        if (key.equals("*")) return "";

        String normalizedKey = key.trim().toLowerCase();

        return switch (normalizedKey) {
            case "n", "name" -> name;
            case "g", "gender" -> gender.name();
            case "he_she" -> gender == Gender.MALE ? "הוא" : "היא";
            case "his_hers" -> gender == Gender.MALE ? "שלו" : "שלה";
            case "to_him_her" -> gender == Gender.MALE ? "לו" : "לה";
            case "one" -> gender == Gender.MALE ? "אחד" : "אחת";
            case "loves", "likes" -> gender == Gender.MALE ? "אוהב" : "אוהבת";
            case "from_him_her" -> gender == Gender.MALE ? "ממנו" : "ממנה";
            default -> {
                WarningContext.addWarning("\u001B[31m" + "Warning: Unrecognized property key in HumanTag.getProperty: '" + key + "'\u001B[0m");
                yield name;
            }
        };
    }

    @Override
    public boolean matches(Map<String, String> constraints) {
        String reqGender = null;
        String reqName = null;

        for (Map.Entry<String, String> entry : constraints.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;

            String key = entry.getKey().trim().toLowerCase();
            String value = entry.getValue().trim();

            if (value.equals("?")) continue;

            switch (key) {
                case "g", "gender" -> reqGender = value;
                case "n", "name" -> reqName = value;
                default -> WarningContext.addWarning("\u001B[31m" + "Warning: Unrecognized constraint key in HumanTag.matches: " + key + "\u001B[0m");
            }
        }

        if (reqName != null) {
            if (!matchComplexStringExpression(reqName.toUpperCase(), java.util.Collections.singleton(this.name.toUpperCase()))) {
                return false;
            }
        }

        if (reqGender != null) {
            String expr = reqGender.toUpperCase();

            if (expr.equals("M") || expr.equals("!M")) expr = expr.replace("M", "MALE");
            if (expr.equals("F") || expr.equals("!F")) expr = expr.replace("F", "FEMALE");

            return matchComplexExpression(expr, java.util.Collections.singleton(this.gender));
        }

        return true;
    }
}
