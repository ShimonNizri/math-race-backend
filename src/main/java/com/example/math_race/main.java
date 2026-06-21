package com.example.math_race;

import com.example.math_race.entities.QuestionErrorReportEntity;
import com.example.math_race.questionGenerator.QuestionEngine;
import com.example.math_race.questionGenerator.tags.core.TemplateTag;

import java.util.HashMap;

public class main {
    public static void main(String[] args) {
        QuestionEngine qe = new QuestionEngine();
        HashMap<String, TemplateTag> memory = new HashMap<>();


        String text = "[HUMAN:#A] [VERB:id=buy:(p_+(#A:g)+_s):#A1] [NUM:min=2;max=5:#num1] [PLACE:place_type=store:*:#A2][ITEM:type=(#A2:categories):p:#e] מה[#A2:s]" +
                " [NUM:min=0;max=1:*:#REN][IF:(#REN)!=(0):<what 1>:<what 2>]";
    }
}
