package com.example.math_race.questionGenerator.tags.types;

import com.example.math_race.entities.dictionary.RoleEntity;
import com.example.math_race.questionGenerator.question.WarningContext;
import com.example.math_race.questionGenerator.tags.core.MatchableTag;
import com.example.math_race.questionGenerator.tags.enums.RoleType;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.example.math_race.questionGenerator.tags.types.TagUtils.matchComplexExpression;
import static com.example.math_race.questionGenerator.tags.types.TagUtils.matchComplexStringExpression;

public class RoleTag implements MatchableTag {
    private String id;
    private String singularMale;
    private String pluralMale;
    private String singularFemale;
    private String pluralFemale;
    private RoleType roleType;
    private Set<String> validPlaceIds;

    public RoleTag(String id, String singularMale, String pluralMale,
                   String singularFemale, String pluralFemale,
                   RoleType roleType, String... allowedPlaceIds) {
        this.id = id;
        this.singularMale = singularMale;
        this.pluralMale = pluralMale;
        this.singularFemale = singularFemale;
        this.pluralFemale = pluralFemale;
        this.roleType = roleType;
        this.validPlaceIds = new HashSet<>(Arrays.asList(allowedPlaceIds));
    }

    public RoleTag(RoleEntity entity){
        this.id = entity.getRoleId();
        this.singularMale = entity.getSingularMale();
        this.pluralMale = entity.getPluralMale();
        this.singularFemale = entity.getSingularFemale();
        this.pluralFemale = entity.getPluralFemale();
        this.roleType = entity.getRoleType();
        this.validPlaceIds = entity.getValidPlaceIds();
    }


    @Override
    public String getProperty(String key) {
        if (key == null || key.isEmpty()) return singularMale;
        if (key.equals("*")) return "";

        String normalizedKey = key.trim().toLowerCase();

        return switch (normalizedKey) {
            case "id" -> id;
            case "rt", "r_t","role_type" -> roleType.name();
            case "vp", "valid_places" -> String.join("|", validPlaceIds);
            case "m_s", "male_s", "m_singular" -> singularMale;
            case "m_p", "male_p", "m_plural" -> pluralMale;
            case "f_s", "female_s", "f_singular" -> singularFemale;
            case "f_p", "female_p", "f_plural" -> pluralFemale;
            default -> {
                WarningContext.addWarning("\u001B[31m" + "Warning: Unrecognized property key in RoleTag.getProperty: '" + key + "'\u001B[0m");
                yield singularMale;
            }
        };
    }

    @Override
    public boolean matches(Map<String, String> constraints) {
        String reqId = null;
        String reqRoleType = null;
        String reqPlaceId = null;

        for (Map.Entry<String, String> entry : constraints.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;

            String key = entry.getKey().trim().toLowerCase();
            String value = entry.getValue().trim();

            if (value.equals("?")) continue;

            switch (key) {
                case "id" -> reqId = value;
                case "rt", "r_t", "role_type" -> reqRoleType = value;
                case "vp", "v_p", "valid_places", "place_id" -> reqPlaceId = value;
                default -> WarningContext.addWarning("\u001B[31m" + "Warning: Unrecognized constraint key in RoleTag.matches: '" + key + "'\u001B[0m");
            }
        }

        if (reqId != null) {
            if (!matchComplexStringExpression(reqId.toUpperCase(), java.util.Collections.singleton(this.id.toUpperCase()))) {
                return false;
            }
        }

        if (reqRoleType != null) {
            if (!matchComplexExpression(reqRoleType, java.util.Collections.singleton(this.roleType))) {
                return false;
            }
        }

        if (reqPlaceId != null) {
            return matchComplexStringExpression(reqPlaceId, this.validPlaceIds);
        }

        return true;
    }
}
