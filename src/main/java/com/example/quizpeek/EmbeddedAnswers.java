package com.example.quizpeek;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Built-in QuizPeek question/answer database. No external answer JSON is created or read. */
public final class EmbeddedAnswers {
    private static final List<AnswerSet> SETS;

    static {
        List<AnswerSet> list = new ArrayList<>();
        list.add(new AnswerSet("So6", Arrays.asList("4", "7", "8"), "7"));
        list.add(new AnswerSet("2vs5", Arrays.asList("27", "17", "7"), "n7"));
        list.add(new AnswerSet("min", Arrays.asList("15", "9", "17"), "9"));
        list.add(new AnswerSet("min2", Arrays.asList("27", "17", "7"), "7"));
        list.add(new AnswerSet("cat", Arrays.asList("quy", "ca", "rang"), "ca"));
        list.add(new AnswerSet("nameserver", Arrays.asList("Anh", "Em", "Toi"), "Toi"));
        list.add(new AnswerSet("max", Arrays.asList("5", "2", "8"), "8"));
        list.add(new AnswerSet("sauA", Arrays.asList("Z", "B", "C"), "B"));
        list.add(new AnswerSet("RANK", Arrays.asList("Vang", "Tim", "Xanh"), "Vang"));
        list.add(new AnswerSet("9/3", Arrays.asList("3", "2", "4"), "3"));
        list.add(new AnswerSet("maxx", Arrays.asList("5", "6", "2"), "6"));
        list.add(new AnswerSet("snow", Arrays.asList("trang", "tim", "vang"), "trang"));
        list.add(new AnswerSet("8/4", Arrays.asList("2", "1", "3"), "2"));
        list.add(new AnswerSet("baydc", Arrays.asList("chim", "truc", "meo"), "chim"));
        list.add(new AnswerSet("summer", Arrays.asList("khong", "nong", "lanh"), "nong"));
        list.add(new AnswerSet("fish", Arrays.asList("sa", "song", "nui"), "song"));
        list.add(new AnswerSet("bautroi", Arrays.asList("trang", "tim", "xanh"), "xanh"));
        list.add(new AnswerSet("10-4", Arrays.asList("7", "6", "3"), "6"));
        list.add(new AnswerSet("svdang", Arrays.asList("fac", "sky", "bed"), "fac"));
        list.add(new AnswerSet("traidat", Arrays.asList("vuong", "tron", "tamgiac"), "tron"));
        list.add(new AnswerSet("min3", Arrays.asList("16", "17", "27"), "16"));
        list.add(new AnswerSet("maxxx", Arrays.asList("4", "6", "7"), "7"));
        list.add(new AnswerSet("catt", Arrays.asList("o o o", "meo meo", "gau gau"), "meo meo"));
        list.add(new AnswerSet("even", Arrays.asList("3", "7", "4"), "4"));
        list.add(new AnswerSet("5-1", Arrays.asList("4", "3", "5"), "4"));
        list.add(new AnswerSet("dog", Arrays.asList("cho", "meo", "ga"), "cho"));
        list.add(new AnswerSet("vuongortron", Arrays.asList("tron", "tam", "vuong"), "tron"));
        list.add(new AnswerSet("biennuocta", Arrays.asList("dong", "tay", "bac"), "dong"));
        list.add(new AnswerSet("5hay9", Arrays.asList("bang", "5", "9"), "9"));
        list.add(new AnswerSet("lacay", Arrays.asList("xanh", "den", "trang"), "xanh"));
        list.add(new AnswerSet("so10", Arrays.asList("12", "8", "9"), "9"));
        SETS = Collections.unmodifiableList(list);
    }

    private EmbeddedAnswers() {}

    public static List<AnswerSet> getAll() { return SETS; }
}
