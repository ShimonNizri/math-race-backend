package com.example.math_race.questionGenerator.tags.types;

import com.example.math_race.entities.dictionary.ItemEntity;
import com.example.math_race.questionGenerator.question.WarningContext;
import com.example.math_race.questionGenerator.tags.core.MatchableTag;
import com.example.math_race.questionGenerator.tags.enums.Gender;
import com.example.math_race.questionGenerator.tags.enums.ItemCategory;
import com.example.math_race.questionGenerator.tags.enums.UnitType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.*;

import static com.example.math_race.questionGenerator.tags.types.TagUtils.matchComplexExpression;
import static com.example.math_race.questionGenerator.tags.types.TagUtils.matchComplexStringExpression;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItemTag implements MatchableTag {
    private String id;
    private String singular;
    private String plural;
    private Gender gender;
    private Set<ItemCategory> categories;
    private Set<UnitType> allowedUnits;

    public ItemTag(String id, String singular, String plural, Gender gender, Set<UnitType> allowedUnits, ItemCategory... categories) {
        this.id = id;
        this.singular = singular;
        this.plural = plural;
        this.gender = gender;
        this.allowedUnits = allowedUnits != null ? allowedUnits : new HashSet<>();
        this.categories = new HashSet<>(Arrays.asList(categories));
    }

    public ItemTag(ItemEntity entity) {
        this.id = entity.getItemId();
        this.singular = entity.getSingular();
        this.plural = entity.getPlural();
        this.gender = entity.getGender();
        this.categories = entity.getCategories();
        this.allowedUnits = entity.getAllowedUnits();
    }

    @Override
    public String getProperty(String key) {
        if (key == null || key.isEmpty()) return singular;
        if (key.equals("*")) return "";

        String normalizedKey = key.trim().toLowerCase();

        return switch (normalizedKey) {
            case "s","singular" -> singular;
            case "p", "plural" -> plural;
            case "g", "gender" -> gender.name();
            case "id" -> id;
            case "t", "type", "c", "categories" -> categories.stream()
                    .map(Enum::name)
                    .collect(java.util.stream.Collectors.joining("|"));
            case "one" -> gender == Gender.MALE ? "אחד" : "אחת";
            case "u", "unit", "unit_type", "allowed_unit" -> {
                if (allowedUnits.isEmpty()) yield "NONE";
                yield allowedUnits.stream()
                        .map(Enum::name)
                        .collect(java.util.stream.Collectors.joining("|"));
            }
            default -> {
                WarningContext.addWarning("\u001B[31m" + "Warning: Unrecognized property key in ItemTag.getProperty: '" + key + "'\u001B[0m");
                yield singular;
            }
        };
    }

    @Override
    public boolean matches(Map<String, String> constraints) {
        String reqType = null;
        String reqUnit = null;
        String reqId = null;

        for (Map.Entry<String, String> entry : constraints.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;

            String key = entry.getKey().trim().toLowerCase();
            String value = entry.getValue().trim();

            if (value.equals("?")) continue;

            switch (key) {
                case "t", "type", "c", "categories" -> reqType = value;
                case "u", "unit", "unit_type", "allowed_unit" -> reqUnit = value;
                case "id" -> reqId = value;
                default -> WarningContext.addWarning("\u001B[31m" + "Warning: Unrecognized constraint key in ItemTag.matches: '" + key + "'\u001B[0m");
            }
        }

        if (reqType != null) {
            if (!matchComplexExpression(reqType, this.categories)) return false;
        }

        if (reqUnit != null) {
            if (!matchComplexExpression(reqUnit, this.allowedUnits)) return false;
        }

        if (reqId != null) {
            return matchComplexStringExpression(reqId.toUpperCase(), Collections.singleton(this.id.toUpperCase()));
        }

        return true;
    }
}
