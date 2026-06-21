package com.example.math_race.questionGenerator.tags.types;

import com.example.math_race.entities.dictionary.AdjectiveEntity;
import com.example.math_race.questionGenerator.question.WarningContext;
import com.example.math_race.questionGenerator.tags.core.MatchableTag;
import com.example.math_race.questionGenerator.tags.enums.AdjectiveType;
import com.example.math_race.questionGenerator.tags.enums.Gender;
import com.example.math_race.questionGenerator.tags.enums.Plurality;

import java.util.HashMap;
import java.util.Map;

import static com.example.math_race.questionGenerator.tags.enums.Gender.MALE;
import static com.example.math_race.questionGenerator.tags.enums.Plurality.SINGULAR;
import static com.example.math_race.questionGenerator.tags.types.TagUtils.matchComplexExpression;
import static com.example.math_race.questionGenerator.tags.types.TagUtils.matchComplexStringExpression;


public class AdjectiveTag implements MatchableTag {
    private String id;
    private AdjectiveType type;
    private Map<String, String> forms = new HashMap<>();

    public AdjectiveTag(String id, AdjectiveType type) {
        this.id = id;
        this.type = type;
    }

    public AdjectiveTag(AdjectiveEntity entity) {
        id = entity.getAdjectiveId();
        type = entity.getType();
        forms = entity.getFormsAsMap();
    }

    @Override
    public String getProperty(String key) {
        if (key == null || key.isEmpty()) return getWord(MALE, SINGULAR);
        if (key.equals("*")) return "";

        String upperKey = key.toUpperCase();

        if (upperKey.equals("ID")) return id;
        if (upperKey.equals("TYPE") || upperKey.equals("T")) return type.toString();

        String value = forms.get(upperKey);
        if (value != null) {
            return value;
        }

        String newKey = switch (upperKey) {
            case "M_S","MALE_S","M_SINGULAR" -> "MALE_SINGULAR";
            case "M_P","MALE_P","M_PLURAL" -> "MALE_PLURAL";
            case "F_S","FEMALE_S","F_SINGULAR" -> "FEMALE_SINGULAR";
            case "F_P","FEMALE_P","F_PLURAL" -> "FEMALE_PLURAL";
            default -> upperKey;
        };

        if (forms.containsKey(newKey)) {
            return forms.get(newKey);
        } else {
            WarningContext.addWarning("\u001B[31m" + "Warning: Unrecognized property key in AdjectiveTag.getProperty: '" + key + "'\u001B[0m");
            return id;
        }
    }

    public void addForm(Gender gender, Plurality plurality, String word) {
        String key = gender.name() + "_" + plurality.name();
        forms.put(key, word);
    }

    public String getWord(Gender gender, Plurality plurality) {
        String key = gender.name() + "_" + plurality.name();
        return forms.getOrDefault(key, id);
    }

    @Override
    public boolean matches(Map<String, String> constraints) {
        String reqId = null;
        String reqType = null;

        for (Map.Entry<String, String> entry : constraints.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;

            String key = entry.getKey().trim().toLowerCase();
            String value = entry.getValue().trim();

            if (value.equals("?")) continue;

            switch (key) {
                case "id" -> reqId = value;
                case "t", "type" -> reqType = value;
                default -> WarningContext.addWarning("\u001B[31m" + "Warning: Unrecognized constraint key in AdjectiveTag.matches: " + key + "\u001B[0m");
            }
        }

        if (reqId != null) {
            if (!matchComplexStringExpression(reqId.toUpperCase(), java.util.Collections.singleton(this.id.toUpperCase()))) {
                return false;
            }
        }

        if (reqType != null) {
            return matchComplexExpression(reqType, java.util.Collections.singleton(this.type));
        }

        return true;
    }
}
