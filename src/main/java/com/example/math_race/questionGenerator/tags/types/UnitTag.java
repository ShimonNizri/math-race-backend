package com.example.math_race.questionGenerator.tags.types;

import com.example.math_race.entities.dictionary.UnitEntity;
import com.example.math_race.questionGenerator.question.WarningContext;
import com.example.math_race.questionGenerator.tags.core.MatchableTag;
import com.example.math_race.questionGenerator.tags.enums.Gender;
import com.example.math_race.questionGenerator.tags.enums.ItemCategory;
import com.example.math_race.questionGenerator.tags.enums.UnitType;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.example.math_race.questionGenerator.tags.types.TagUtils.matchComplexExpression;
import static com.example.math_race.questionGenerator.tags.types.TagUtils.matchComplexStringExpression;

public class UnitTag implements MatchableTag {
    private String id;
    private String singular;
    private String plural;
    private Gender gender;
    private UnitType type;
    private Set<ItemCategory> validItemCategories;

    public UnitTag(String id, String singular, String plural, Gender gender, UnitType type, ItemCategory... categories) {
        this.id = id;
        this.singular = singular;
        this.plural = plural;
        this.gender = gender;
        this.type = type;
        this.validItemCategories = new HashSet<>(Arrays.asList(categories));
    }

    public UnitTag(UnitEntity entity) {
        this.id = entity.getUnitId();
        this.singular = entity.getSingular();
        this.plural = entity.getPlural();
        this.gender = entity.getGender();
        this.type = entity.getType();
        this.validItemCategories = entity.getValidItemCategories();
    }

    @Override
    public String getProperty(String key) {
        if (key == null || key.isEmpty()) return singular;
        if (key.equals("*")) return "";

        String normalizedKey = key.trim().toLowerCase();

        return switch (normalizedKey) {
            case "id" -> id;
            case "s", "singular" -> singular;
            case "p", "plural" -> plural;
            case "g", "gender" -> gender.name();
            case "t", "type" -> type.name();
            case "one" -> gender == Gender.MALE ? "אחד" : "אחת";
            case "vic", "valid_ic", "valid_item_categories" -> validItemCategories.stream()
                    .map(Enum::name)
                    .collect(java.util.stream.Collectors.joining("|"));
            default -> {
                WarningContext.addWarning("\u001B[31m" + "Warning: Unrecognized property key in UnitTag.getProperty: '" + key + "'\u001B[0m");
                yield singular;
            }
        };
    }

    @Override
    public boolean matches(Map<String, String> constraints) {
        String reqId = null;
        String reqType = null;
        String reqItemCategory = null;

        for (Map.Entry<String, String> entry : constraints.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;

            String key = entry.getKey().trim().toLowerCase();
            String value = entry.getValue().trim();

            if (value.equals("?")) continue;

            switch (key) {
                case "id" -> reqId = value;
                case "t", "type" -> reqType = value;
                case "ic", "i_c", "item_category" -> reqItemCategory = value;
                default -> WarningContext.addWarning("\u001B[31m" + "Warning: Unrecognized constraint key in UnitTag.matches: '" + key + "'\u001B[0m");
            }
        }

        if (reqId != null) {
            if (!matchComplexStringExpression(reqId.toUpperCase(), java.util.Collections.singleton(this.id.toUpperCase()))) {
                return false;
            }
        }

        if (reqType != null) {
            if (!matchComplexExpression(reqType, java.util.Collections.singleton(this.type))) {
                return false;
            }
        }

        if (reqItemCategory != null) {
            return matchComplexExpression(reqItemCategory, this.validItemCategories);
        }

        return true;
    }
}
