package com.example.math_race.questionGenerator;

import com.example.math_race.json.loader.JsonOnlyDictionaryProvider;
import com.example.math_race.json.models.seeders.DictionaryJsonSeeder;
import com.example.math_race.questionGenerator.dictionary.DictionaryProvider;
import com.example.math_race.questionGenerator.question.MathQuestion;
import com.example.math_race.questionGenerator.question.QuestionTemplate;
import com.example.math_race.questionGenerator.question.TemplateResult;
import com.example.math_race.questionGenerator.question.WarningContext;
import com.example.math_race.questionGenerator.tags.core.MatchableTag;
import com.example.math_race.questionGenerator.tags.core.TemplateTag;
import com.example.math_race.questionGenerator.tags.core.TagInfo;
import com.example.math_race.questionGenerator.tags.types.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static com.example.math_race.questionGenerator.tags.core.TagInfo.smartSplit;

@Component
public class QuestionEngine {

    private final DictionaryProvider dictionaryProvider;

    private List<HumanTag> humans;
    private List<ItemTag> items;
    private List<VerbTag> verbs;
    private List<PlaceTag> places;
    private List<AdjectiveTag> adjectives;
    private List<UnitTag> units;
    private List<RoleTag> roles;

    @Autowired
    public QuestionEngine(DictionaryProvider dictionaryProvider){
        this.dictionaryProvider = dictionaryProvider;
    }

    public QuestionEngine(){
        DictionaryJsonSeeder seeder = new DictionaryJsonSeeder();
        this.dictionaryProvider = new JsonOnlyDictionaryProvider(seeder);
        initDictionaryCache();
    }

    @PostConstruct
    public void initDictionaryCache() {
        this.humans = dictionaryProvider.loadHumanTags();
        this.items = dictionaryProvider.loadItemTag();
        this.verbs = dictionaryProvider.loadVerbTag();
        this.places = dictionaryProvider.loadPlaceTag();
        this.adjectives = dictionaryProvider.loadAdjectiveTag();
        this.units = dictionaryProvider.loadUnitTag();
        this.roles = dictionaryProvider.loadRoleTag();
    }

    public TemplateResult<MathQuestion> processTemplate(QuestionTemplate questionTemplate) {
        WarningContext.enter();
        try {
            Map<String, TemplateTag> memory = new HashMap<>();

            String questionText = evaluateTemplate(questionTemplate.questionTemplate(), memory);
            String correctAnswer = evaluateTemplate(questionTemplate.answerTemplate(), memory);

            List<String> options = new ArrayList<>();
            options.add(correctAnswer);

            if (questionTemplate.distractorsTemplates() != null) {
                for (String distractor : questionTemplate.distractorsTemplates()) {

                    String distractorValue = evaluateTemplate(distractor, memory);
                    while (options.contains(distractorValue)) {
                        try {
                            int val = Integer.parseInt(distractorValue);
                            distractorValue = String.valueOf(val + 1);
                        } catch (NumberFormatException e) {
                            distractorValue = distractorValue + " ";
                            break;
                        }
                    }

                    options.add(distractorValue);
                }
            }

            Collections.shuffle(options);

            MathQuestion mathQuestion = new MathQuestion();
            mathQuestion.setTemplateId(questionTemplate.id());
            mathQuestion.setQuestionId(UUID.randomUUID().toString());
            mathQuestion.setExpression(questionText + " תשובה נכונה  : " + correctAnswer);
            mathQuestion.setCorrectAnswer(correctAnswer);
            mathQuestion.setOptions(options);
            mathQuestion.setHint(evaluateTemplate(questionTemplate.hintTemplate(), memory));


            List<String> errors = WarningContext.exit();
            return new TemplateResult<>(mathQuestion, errors,memory);

        }catch (Exception e) {
            WarningContext.exit();
            throw e;
        }
    }

    public TemplateResult<String> evaluateTemplateExternal(String template) {
        return evaluateTemplateExternal(template,new HashMap<>());
    }

    public TemplateResult<String> evaluateTemplateExternal(String template, Map<String, TemplateTag> memory) {
        WarningContext.enter();
        try {
            String resultText = evaluateTemplate(template, memory);

            List<String> errors = WarningContext.exit();
            return new TemplateResult<>(resultText, errors,memory);

        } catch (Exception e) {
            WarningContext.exit();
            throw e;
        }
    }

    private String evaluateTemplate(String template, Map<String, TemplateTag> memory) {
        Set<String> tags = extractUniqueTags(template);

        if (memory == null) {
            memory = new HashMap<>();
        }
        String result = template;

        for (String tag : tags) {

            if (tag.startsWith("[IF:")) {
                result = processIfTag(tag, result, memory);
                continue;
            }

            TagInfo info = TagInfo.parse(tag);
            if (!tag.startsWith("[#")) {
                Map<String, String> resolvedConstraints = new HashMap<>();

                for (Map.Entry<String, String> entry : info.getConstraints().entrySet()) {
                    resolvedConstraints.put(entry.getKey(), resolveValue(entry.getValue(), memory));
                }

                TemplateTag chosen = switch (info.getType().toUpperCase()) {
                    case "HUMAN" -> getRandomMatch(humans,resolvedConstraints, HumanTag.class);
                    case "ITEM" -> getRandomMatch(items,resolvedConstraints, ItemTag.class);
                    case "NUM" -> findNumber(resolvedConstraints);
                    case "VERB" -> getRandomMatch(verbs,resolvedConstraints, VerbTag.class);
                    case "PLACE" -> getRandomMatch(places,resolvedConstraints, PlaceTag.class);
                    case "ADJ" -> getRandomMatch(adjectives,resolvedConstraints, AdjectiveTag.class);
                    case "UNIT" -> getRandomMatch(units,resolvedConstraints, UnitTag.class);
                    case "TIME" -> findTime(resolvedConstraints);
                    case "ROLE" -> getRandomMatch(roles,resolvedConstraints, RoleTag.class);
                    default -> null;
                };

                if (chosen != null) {
                    String tagId = info.getId();
                    boolean isTemp = false;

                    if (tagId == null || tagId.isEmpty()) {
                        tagId = "#TEMP_" + java.util.UUID.randomUUID().toString().substring(0, 6);
                        isTemp = true;
                    }

                    memory.put(tagId, chosen);
                    String resolvedProp = resolveValue("("+ tagId + ":" + info.getProperty() + ")", memory);

                    result = result.replace(tag, resolvedProp);

                    if (isTemp) memory.remove(tagId);
                } else {
                    WarningContext.addWarning("\u001B[31m" + "Warning: No match found for type [" + info.getType() + "] with constraints: " + resolvedConstraints + "\u001B[0m");
                }
            } else {
                if (memory.containsKey(info.getId())) {

                    String resolvedProp = resolveValue("(" + tag.substring(1, tag.length() - 1) + ")", memory);
                    result = result.replace(tag, resolvedProp);
                }
            }
        }
        return result;
    }

    private Set<String> extractUniqueTags(String template) {
        Set<String> tags = new LinkedHashSet<>();

        int indexStart = -1;
        int depth = 0;

        for (int i = 0; i < template.length(); i++) {
            char c = template.charAt(i);

            if (c == '[') {
                if (depth == 0) {
                    indexStart = i;
                }
                depth++;

            } else if (c == ']') {
                if (depth > 0) {
                    depth--;

                    if (depth == 0) {
                        String tag = template.substring(indexStart, i + 1);
                        tags.add(tag);
                        indexStart = -1;
                    }
                }
            }
        }
        return tags;
    }

    private String resolveValue(String value, Map<String, TemplateTag> memory) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        String expression = value.trim();
        boolean isNot = false;

        if (expression.startsWith("!") && isFullyWrappedByParentheses(expression.substring(1))) {
            isNot = true;
            expression = expression.substring(2, expression.length() - 1);
        } else if (isFullyWrappedByParentheses(expression)) {
            expression = expression.substring(1, expression.length() - 1);
        } else {
            return value;
        }

        List<String> plusSplit = smartSplit(expression, '+');

        if (plusSplit.size() > 1) {
            StringBuilder combinedResult = new StringBuilder();
            for (String part : plusSplit) {
                combinedResult.append(resolveValue(part.trim(), memory));
            }
            return isNot ? "!" + combinedResult.toString() : combinedResult.toString();
        }

        String resolvedSingle = resolveValue(plusSplit.get(0).trim(), memory);

        if (resolvedSingle.startsWith("#")) {
            String[] parts = resolvedSingle.split(":", 2);
            String id = parts[0].trim();
            String property = (parts.length > 1) ? parts[1].trim() : "";

            if (memory.containsKey(id)) {

                String[] splitNewProperty = splitBeforeAndWithinParentheses(property);
                if (splitNewProperty != null) {

                    property = splitNewProperty[0] + resolveValue(splitNewProperty[1], memory);
                }

                String finalValue = memory.get(id).getProperty(property);
                if (finalValue == null) finalValue = "";

                return isNot ? "!" + finalValue : finalValue;
            } else {
                WarningContext.addWarning("\u001B[31m" + "the id : " + id + " does not exist in the memory" + "\u001B[0m");
            }
        }

        return isNot ? "!" + resolvedSingle : resolvedSingle;
    }

    private String processIfTag(String tag, String result, Map<String, TemplateTag> memory){
        try {
            int originalCondEnd = tag.indexOf(":<");
            if (originalCondEnd == -1) return result;

            String identifier = tag.substring(0, originalCondEnd + 2);
            int startIdx = result.indexOf(identifier);
            if (startIdx == -1) return result;

            int depth = 0;
            int endIdx = -1;
            for (int i = startIdx; i < result.length(); i++) {
                if (result.charAt(i) == '[') depth++;
                else if (result.charAt(i) == ']') {
                    depth--;
                    if (depth == 0) {
                        endIdx = i;
                        break;
                    }
                }
            }
            if (endIdx == -1) return result;

            String currentTag = result.substring(startIdx, endIdx + 1);
            int currentCondEnd = currentTag.indexOf(":<");

            int branchSplit = -1;
            int innerDepth = 0;
            for (int i = currentCondEnd + 2; i < currentTag.length(); i++) {
                if (currentTag.charAt(i) == '[') innerDepth++;
                else if (currentTag.charAt(i) == ']') innerDepth--;

                if (innerDepth == 0 && currentTag.startsWith(">:<", i)) {
                    branchSplit = i;
                    break;
                }
            }
            if (branchSplit == -1) return result;

            String conditionFromTag = currentTag.substring(4, currentCondEnd).trim();
            String trueOption = currentTag.substring(currentCondEnd + 2, branchSplit);
            String falseOption = currentTag.substring(branchSplit + 3, currentTag.length() - 2);

            String[] opInfo = findMainOperator(conditionFromTag);

            if (opInfo != null && opInfo.length == 2) {

                String operator =  opInfo[0];
                int opIndex =  Integer.parseInt(opInfo[1]);

                String leftSide = conditionFromTag.substring(0, opIndex).trim();
                leftSide = resolveValue(leftSide,memory);

                String rightSide = conditionFromTag.substring(opIndex + operator.length()).trim();
                rightSide = resolveValue(rightSide,memory);

                boolean conditionMet = false;
                if (operator.equals("=")) {
                    conditionMet = leftSide.equals(rightSide);
                } else if (operator.equals("!=")) {
                    conditionMet = !leftSide.equals(rightSide);
                } else {
                    try {
                        double actualNum = Double.parseDouble(leftSide);
                        double expectedNum = Double.parseDouble(rightSide);
                        conditionMet = switch (operator) {
                            case ">" -> actualNum > expectedNum;
                            case "<" -> actualNum < expectedNum;
                            case ">=" -> actualNum >= expectedNum;
                            case "<=" -> actualNum <= expectedNum;
                            default -> false;
                        };
                    } catch (NumberFormatException nfe) {
                        WarningContext.addWarning("\u001B[31m" + "Warning: Numeric comparison failed for actual: [" + leftSide + "] and expected: [" + rightSide + "]" + "\u001B[0m");
                    }
                }

                String chosenText = conditionMet ? trueOption : falseOption;
                String resolvedText = evaluateTemplate(chosenText, memory);

                result = result.replace(currentTag, resolvedText);
            }
        } catch (Exception e) {
            WarningContext.addWarning("\u001B[31m" + "Error parsing IF tag: " + tag + "\u001B[0m");
        }

        return result;
    }

    private <T extends MatchableTag> T getRandomMatch(List<T> dataList, Map<String, String> constraints, Class<T> clazz) {
        List<T> matches = dataList.stream()
                .filter(item -> item.matches(constraints))
                .toList();

        if (matches.isEmpty()) {
            WarningContext.addWarning("\u001B[31m" + "Warning: No " + clazz.getSimpleName() + " matches constraints: " + constraints + "\u001B[0m");
            return null;
        }

        return matches.get(ThreadLocalRandom.current().nextInt(matches.size()));
    }

    private NumberTag findNumber(Map<String, String> constraints) {
        int min = 1;
        int max = 100;
        String valStr = "?";

        for (Map.Entry<String, String> entry : constraints.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;

            String key = entry.getKey().toLowerCase();
            String value = entry.getValue().trim();

            if (value.equals("?")) continue;

            switch (key) {
                case "min" -> {
                    try {
                        min = Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        WarningContext.addWarning("\u001B[31m" + "Warning: Invalid min format." + "\u001B[0m");
                    }
                }
                case "max" -> {
                    try {
                        max = Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        WarningContext.addWarning("\u001B[31m" + "Warning: Invalid max format." + "\u001B[0m");
                    }
                }
                case "value", "v" -> valStr = value;
            }
        }

        if (min > max) {
            int temp = min;
            min = max;
            max = temp;
        }

        return new NumberTag(valStr, min, max);
    }

    private TimeTag findTime(Map<String, String> constraints) {
        int minMinutes = 0;    // 00:00
        int maxMinutes = 1439; // 23:59
        boolean round = true;
        String valStr = "?";

        for (Map.Entry<String, String> entry : constraints.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;

            String key = entry.getKey().toLowerCase();
            String value = entry.getValue().trim();

            if (value.equals("?")) continue;

            switch (key) {
                case "min" -> {
                    try {
                        minMinutes = TimeTag.parseTime(value);
                    } catch (Exception e) {
                        WarningContext.addWarning("\u001B[31m" + "Warning: Invalid min time format." + "\u001B[0m");
                    }
                }
                case "max" -> {
                    try {
                        maxMinutes = TimeTag.parseTime(value);
                    } catch (Exception e) {
                        WarningContext.addWarning("\u001B[31m" + "Warning: Invalid max time format." + "\u001B[0m");
                    }
                }
                case "round", "r" -> round = !value.equalsIgnoreCase("false");
                case "value", "v" -> valStr = value;
            }
        }

        if (minMinutes > maxMinutes) {
            int temp = minMinutes;
            minMinutes = maxMinutes;
            maxMinutes = temp;
        }

        return new TimeTag(valStr, minMinutes, maxMinutes, round);
    }

    private static boolean isFullyWrappedByParentheses(String s) {
        if (s == null || s.length() < 2) return false;
        if (s.charAt(0) != '(' || s.charAt(s.length() - 1) != ')') return false;

        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;

            if (depth == 0 && i < s.length() - 1) {
                return false;
            }
        }
        return depth == 0;
    }

    private static String[] splitBeforeAndWithinParentheses(String str) {
        if (str == null || !str.endsWith(")")) {
            return null;
        }

        int firstOpen = str.indexOf('(');

        if (firstOpen == -1) {
            return null;
        }

        String x = str.substring(0, firstOpen);
        String y = str.substring(firstOpen);

        return new String[]{x, y};
    }

    private static String[] findMainOperator(String condition) {
        String[] operators = {">=", "<=", "!=", ">", "<", "="};
        int depth = 0;

        for (int i = 0; i < condition.length(); i++) {
            char c = condition.charAt(i);

            if (c == '(') depth++;
            else if (c == ')') depth--;

            if (depth == 0) {
                for (String op : operators) {
                    if (condition.startsWith(op, i)) {
                        return new String[]{op, String.valueOf(i)};
                    }
                }
            }
        }
        return null;
    }
}
