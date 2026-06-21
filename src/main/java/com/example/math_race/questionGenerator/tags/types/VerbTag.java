package com.example.math_race.questionGenerator.tags.types;

import com.example.math_race.entities.dictionary.VerbEntity;
import com.example.math_race.questionGenerator.question.WarningContext;
import com.example.math_race.questionGenerator.tags.core.MatchableTag;
import com.example.math_race.questionGenerator.tags.enums.Gender;
import com.example.math_race.questionGenerator.tags.enums.Plurality;
import com.example.math_race.questionGenerator.tags.enums.Tense;

import java.util.HashMap;
import java.util.Map;

import static com.example.math_race.questionGenerator.tags.enums.Gender.MALE;
import static com.example.math_race.questionGenerator.tags.enums.Plurality.SINGULAR;
import static com.example.math_race.questionGenerator.tags.enums.Tense.PAST;
import static com.example.math_race.questionGenerator.tags.types.TagUtils.matchComplexStringExpression;

public class VerbTag implements MatchableTag {
    private String id;
    private Map<String, String> forms = new HashMap<>();

    public VerbTag(String id) {
        this.id = id;
    }

    public VerbTag(VerbEntity entity) {
        this.id = entity.getVerbId();
        this.forms = entity.getFormsAsMap();
    }

    @Override
    public String getProperty(String key) {
        if (key == null || key.isEmpty()) return getWord(PAST,MALE,SINGULAR);
        if (key.equals("*")) return "";

        String upperKey = key.trim().toUpperCase();

        if (upperKey.equals("ID")) return id;

        String value = forms.get(upperKey);
        if (value != null) {
            return value;
        }

        String newKey = switch (upperKey) {
            case "INF", "INFINITIVE" -> "INF_MALE_ANY";

            case "PAST_MALE_S", "PAST_M_SINGULAR", "PAST_M_S", "P_MALE_SINGULAR", "P_MALE_S", "P_M_SINGULAR", "P_M_S" -> "PAST_MALE_SINGULAR";
            case "PAST_MALE_P", "PAST_M_PLURAL", "PAST_M_P", "P_MALE_PLURAL", "P_MALE_P", "P_M_PLURAL", "P_M_P" -> "PAST_MALE_PLURAL";
            case "PAST_FEMALE_S", "PAST_F_SINGULAR", "PAST_F_S", "P_FEMALE_SINGULAR", "P_FEMALE_S", "P_F_SINGULAR", "P_F_S" -> "PAST_FEMALE_SINGULAR";
            case "PAST_FEMALE_P", "PAST_F_PLURAL", "PAST_F_P", "P_FEMALE_PLURAL", "P_FEMALE_P", "P_F_PLURAL", "P_F_P" -> "PAST_FEMALE_PLURAL";

            case "PRESENT_MALE_S", "PRESENT_M_SINGULAR", "PRESENT_M_S", "PR_MALE_SINGULAR", "PR_MALE_S", "PR_M_SINGULAR", "PR_M_S" -> "PRESENT_MALE_SINGULAR";
            case "PRESENT_MALE_P", "PRESENT_M_PLURAL", "PRESENT_M_P", "PR_MALE_PLURAL", "PR_MALE_P", "PR_M_PLURAL", "PR_M_P" -> "PRESENT_MALE_PLURAL";
            case "PRESENT_FEMALE_S", "PRESENT_F_SINGULAR", "PRESENT_F_S", "PR_FEMALE_SINGULAR", "PR_FEMALE_S", "PR_F_SINGULAR", "PR_F_S" -> "PRESENT_FEMALE_SINGULAR";
            case "PRESENT_FEMALE_P", "PRESENT_F_PLURAL", "PRESENT_F_P", "PR_FEMALE_PLURAL", "PR_FEMALE_P", "PR_F_PLURAL", "PR_F_P" -> "PRESENT_FEMALE_PLURAL";

            case "FUTURE_MALE_S", "FUTURE_M_SINGULAR", "FUTURE_M_S", "F_MALE_SINGULAR", "F_MALE_S", "F_M_SINGULAR", "F_M_S" -> "FUTURE_MALE_SINGULAR";
            case "FUTURE_MALE_P", "FUTURE_M_PLURAL", "FUTURE_M_P", "F_MALE_PLURAL", "F_MALE_P", "F_M_PLURAL", "F_M_P" -> "FUTURE_MALE_PLURAL";
            case "FUTURE_FEMALE_S", "FUTURE_F_SINGULAR", "FUTURE_F_S", "F_FEMALE_SINGULAR", "F_FEMALE_S", "F_F_SINGULAR", "F_F_S" -> "FUTURE_FEMALE_SINGULAR";
            case "FUTURE_FEMALE_P", "FUTURE_F_PLURAL", "FUTURE_F_P", "F_FEMALE_PLURAL", "F_FEMALE_P", "F_F_PLURAL", "F_F_P" -> "FUTURE_FEMALE_PLURAL";

            default -> upperKey;
        };

        if (forms.containsKey(newKey)) {
            return forms.get(newKey);
        } else {
            WarningContext.addWarning("\u001B[31m" + "Warning: Unrecognized property key in VerbTag.getProperty: '" + key + "'\u001B[0m");
            return id;
        }
    }

    public void addForm(Tense tense, Gender gender, Plurality plurality, String word) {
        String key = tense.name() + "_" + gender.name() + "_" + plurality.name();
        forms.put(key, word);
    }

    public String getWord(Tense tense, Gender gender, Plurality plurality) {
        String key = tense.name() + "_" + gender.name() + "_" + plurality.name();
        return forms.getOrDefault(key, id);
    }

    @Override
    public boolean matches(Map<String, String> constraints) {
        String reqId = null;

        for (Map.Entry<String, String> entry : constraints.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;

            String key = entry.getKey().trim().toLowerCase();
            String value = entry.getValue().trim();

            if (value.equals("?")) continue;

            if (key.equals("id")) {
                reqId = value;
            } else {
                WarningContext.addWarning("\u001B[31m" + "Warning: Unrecognized constraint key in VerbTag.matches: '" + key + "'\u001B[0m");
            }
        }

        if (reqId != null) {
            return matchComplexStringExpression(reqId.toUpperCase(), java.util.Collections.singleton(this.id.toUpperCase()));
        }

        return true;
    }
}
