package com.example.quizpeek;

import java.util.ArrayList;
import java.util.List;

public class AnswerSet {
   private String name;
   private List<String> answers;
   private String correctAnswer;
   private boolean active;

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

   public String getName() {
      return this.name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public List<String> getAnswers() {
      return this.answers;
   }

   public void setAnswers(List<String> answers) {
      this.answers = answers != null ? new ArrayList<>(answers) : new ArrayList<>();
   }

   public String getCorrectAnswer() {
      return this.correctAnswer;
   }

   public void setCorrectAnswer(String correctAnswer) {
      this.correctAnswer = correctAnswer != null ? correctAnswer : "";
   }

   public boolean isActive() {
      return this.active;
   }

   public void setActive(boolean active) {
      this.active = active;
   }

   @Override
   public String toString() {
      return this.name + (this.active ? " (đang dùng)" : "");
   }
}
