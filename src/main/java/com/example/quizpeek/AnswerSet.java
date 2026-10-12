package com.example.quizpeek;

import java.util.ArrayList;
import java.util.List;

public class AnswerSet {
    private String name;
    private List<String> answers; // luôn có 3 phần tử
    private String correctAnswer;
    private boolean active; // giữ lại nhưng không dùng trong logic H

    public AnswerSet() {
        this.name = "";
        this.answers = new ArrayList<>();
        this.correctAnswer = "";
        this.active = false;
    }

    public AnswerSet(String name, List<String> answers, String correctAnswer) {
        this.name = name;
        this.answers = answers != null ? new ArrayList<>(answers) : new ArrayList<>();
        this.correctAnswer = correctAnswer != null ? correctAnswer : "";
        this.active = false;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<String> getAnswers() { return answers; }
    public void setAnswers(List<String> answers) { this.answers = answers != null ? new ArrayList<>(answers) : new ArrayList<>(); }
    public String getCorrectAnswer() { return correctAnswer; }
    public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer != null ? correctAnswer : ""; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    @Override
    public String toString() {
        return name + (active ? " (đang dùng)" : "");
    }
}