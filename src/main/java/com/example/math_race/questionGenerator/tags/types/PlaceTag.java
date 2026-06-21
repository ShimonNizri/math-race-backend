package com.example.math_race.questionGenerator.tags.types;

import com.example.math_race.entities.dictionary.PlaceEntity;
import com.example.math_race.questionGenerator.question.WarningContext;
import com.example.math_race.questionGenerator.tags.core.MatchableTag;
import com.example.math_race.questionGenerator.tags.enums.Gender;
import com.example.math_race.questionGenerator.tags.enums.ItemCategory;
import com.example.math_race.questionGenerator.tags.enums.PlaceType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.*;

import static com.example.math_race.questionGenerator.tags.types.TagUtils.matchComplexExpression;
import static com.example.math_race.questionGenerator.tags.types.TagUtils.matchComplexStringExpression;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PlaceTag implements MatchableTag {
    private String id;
    private String singular;
    private String plural;
    private Gender gender;
    private PlaceType placeType;
    private Set<ItemCategory> availableItemCategories;


    public PlaceTag(String id, String singular, String plural, Gender gender, PlaceType placeType, ItemCategory... availableItemCategories) {
        this.id = id;
        this.singular = singular;
        this.plural = plural;
        this.gender = gender;
        this.placeType = placeType;
        this.availableItemCategories = new HashSet<>(Arrays.asList(availableItemCategories));
    }

    public PlaceTag(PlaceEntity entity) {
        this.id = entity.getPlaceId();
        this.singular = entity.getSingular();
        this.plural = entity.getPlural();
        this.gender = entity.getGender();
        this.placeType = entity.getPlaceType();
        this.availableItemCategories = entity.getAvailableItemCategories();
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
            case "t", "type", "c", "categories" -> availableItemCategories.stream()
                    .map(Enum::name)
                    .collect(java.util.stream.Collectors.joining("|"));
            case "pt", "place_type" -> placeType.name();
            case "he_she" -> gender == Gender.MALE ? "הוא" : "היא";
            case "his_hers" -> gender == Gender.MALE ? "שלו" : "שלה";
            case "to_him_her" -> gender == Gender.MALE ? "לו" : "לה";
            case "in_it" -> gender == Gender.MALE ? "בו" : "בה";
            case "to_it" -> gender == Gender.MALE ? "אליו" : "אליה";
            case "from_it", "from_him_her" -> gender == Gender.MALE ? "ממנו" : "ממנה";
            default -> {
                WarningContext.addWarning("\u001B[31m" + "Warning: Unrecognized property key in PlaceTag.getProperty: '" + key + "'\u001B[0m");
                yield singular;
            }
        };
    }

    @Override
    public boolean matches(Map<String, String> constraints) {
        String reqId = null;
        String reqPlaceType = null;
        String reqType = null;

        for (Map.Entry<String, String> entry : constraints.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;

            String key = entry.getKey().trim().toLowerCase();
            String value = entry.getValue().trim();

            if (value.equals("?")) continue;

            switch (key) {
                case "id" -> reqId = value;
                case "pt", "place_type" -> reqPlaceType = value;
                case "t", "type", "c", "categories" -> reqType = value;
                default -> WarningContext.addWarning("\u001B[31m" + "Warning: Unrecognized constraint key in PlaceTag.matches: '" + key + "'\u001B[0m");
            }
        }

        if (reqId != null) {
            if (!matchComplexStringExpression(reqId.toUpperCase(), java.util.Collections.singleton(this.id.toUpperCase()))) {
                return false;
            }
        }

        if (reqPlaceType != null) {
            if (!matchComplexExpression(reqPlaceType, java.util.Collections.singleton(this.placeType))) {
                return false;
            }
        }

        if (reqType != null) {
            return matchComplexExpression(reqType, this.availableItemCategories);
        }

        return true;
    }
}
