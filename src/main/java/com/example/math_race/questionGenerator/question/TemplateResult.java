package com.example.math_race.questionGenerator.question;

import com.example.math_race.questionGenerator.tags.core.TemplateTag;

import java.util.List;
import java.util.Map;

public record TemplateResult<T>(
        T data,
        List<String> errors,
        Map<String, TemplateTag> memory
) {
    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }
    public boolean isSuccess() {
        return !hasErrors();
    }
}
