package com.example.math_race.service;

import com.example.math_race.entities.templates.QuestionTemplateEntity;
import com.example.math_race.questionGenerator.question.QuestionTemplate;
import com.example.math_race.repositories.QuestionTemplatesRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
public class QuestionTemplateService {

    private final QuestionTemplatesRepository questionRepository;

    private Map<String, QuestionTemplateEntity> templatesByIdCache;
    private Map<String, List<QuestionTemplate>> templatesByDifficultyCache;

    @Autowired
    public QuestionTemplateService(QuestionTemplatesRepository questionRepository) {
        this.questionRepository = questionRepository;
    }

    @PostConstruct
    public void initTemplates() {
        List<QuestionTemplateEntity> entities = questionRepository.loadAllTemplates();

        this.templatesByIdCache = entities.stream()
                .collect(Collectors.toMap(QuestionTemplateEntity::getTemplateId, e -> e));

        this.templatesByDifficultyCache = entities.stream()
                .map(QuestionTemplate::new)
                .collect(Collectors.groupingBy(
                        this::extractDifficulty,
                        Collectors.toList()
                ));
    }

    private String extractDifficulty(QuestionTemplate template) {
        String id = template.id();
        if (id != null && id.contains("_")) {
            return id.split("_")[0].toLowerCase();
        }
        return "general";
    }

    public List<QuestionTemplate> getTemplatesByDifficulty(String difficulty) {
        return templatesByDifficultyCache.getOrDefault(difficulty.toLowerCase(), List.of());
    }

    public QuestionTemplate getTemplateByDifficulty(String difficulty) {
        List<QuestionTemplate> templates = getTemplatesByDifficulty(difficulty);

        if (templates == null || templates.isEmpty()) {
            return null;
        }

        int randomIndex = ThreadLocalRandom.current().nextInt(templates.size());
        return templates.get(randomIndex);
    }

    public QuestionTemplateEntity getTemplateById(String id) {
        return templatesByIdCache.get(id);
    }

    public void reloadTemplates() {
        initTemplates();
    }

    public void reloadSingleTemplate(String templateId) {
        QuestionTemplateEntity updatedEntity = questionRepository.loadByTemplateId(templateId);

        if (updatedEntity == null) {
            removeTemplateFromCache(templateId);
            return;
        }

        QuestionTemplateEntity oldEntity = templatesByIdCache.get(templateId);
        if (oldEntity != null) {
            QuestionTemplate oldTemplateRef = new QuestionTemplate(oldEntity);
            String oldDifficulty = extractDifficulty(oldTemplateRef);
            List<QuestionTemplate> oldList = templatesByDifficultyCache.get(oldDifficulty);
            if (oldList != null) {
                oldList.removeIf(t -> t.id().equals(templateId));
            }
        }

        templatesByIdCache.put(templateId, updatedEntity);

        QuestionTemplate newTemplate = new QuestionTemplate(updatedEntity);
        String newDifficulty = extractDifficulty(newTemplate);

        templatesByDifficultyCache
                .computeIfAbsent(newDifficulty, k -> new java.util.ArrayList<>())
                .add(newTemplate);
    }

    private void removeTemplateFromCache(String templateId) {
        QuestionTemplateEntity oldEntity = templatesByIdCache.remove(templateId);
        if (oldEntity != null) {
            QuestionTemplate oldTemplateRef = new QuestionTemplate(oldEntity);
            String difficulty = extractDifficulty(oldTemplateRef);
            List<QuestionTemplate> list = templatesByDifficultyCache.get(difficulty);
            if (list != null) {
                list.removeIf(t -> t.id().equals(templateId));
            }
        }
    }
}
