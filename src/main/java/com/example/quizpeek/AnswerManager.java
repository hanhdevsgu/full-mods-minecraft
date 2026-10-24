package com.example.quizpeek;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AnswerManager {
    private static final Logger LOGGER = LogManager.getLogger("quizpeek");
    private static final String CONFIG_FILE = "quizpeek_answers.json";
    private List<AnswerSet> sets = new ArrayList<>();
    private AnswerSet activeSet = null;

    private static AnswerManager instance;

    public static AnswerManager getInstance() {
        if (instance == null) {
            instance = new AnswerManager();
        }
        return instance;
    }

    private AnswerManager() {
        load();
    }

    public List<AnswerSet> getSets() {
        return sets;
    }

    public AnswerSet getActiveSet() {
        return activeSet;
    }

    public void addSet(AnswerSet set) {
        if (set != null && set.getName() != null && !set.getName().isEmpty()) {
            sets.add(set);
            save();
        }
    }

    public void removeSet(AnswerSet set) {
        if (sets.remove(set)) {
            if (activeSet == set) {
                activeSet = null;
            }
            save();
        }
    }

    public void setActive(AnswerSet set) {
        // không dùng, giữ để tương thích
    }

    public void save() {
        File file = new File(Minecraft.getMinecraft().mcDataDir, CONFIG_FILE);
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Type type = new TypeToken<List<AnswerSet>>() {
            }.getType();
            gson.toJson(sets, writer);
            LOGGER.info("Đã lưu {} bộ đáp án vào {}", sets.size(), file.getAbsolutePath());
        } catch (IOException e) {
            LOGGER.error("Không thể lưu file đáp án", e);
        }
    }

    /**
     * Tao ra TOAN BO 31 bo cau hoi mac dinh (hard-code trong code) -
     * khi cai mod lan dau, file JSON chua ton tai thi tu dong tao ra
     * day du cau hoi giong het file hien tai cua chu mod, KHONG can
     * copy file JSON qua bang tay nua.
     */
    private List<AnswerSet> createDefaultSets() {
        List<AnswerSet> defaults = new ArrayList<>();
        defaults.add(new AnswerSet("So6", Arrays.asList("4", "7", "8"), "7"));
        defaults.add(new AnswerSet("2vs5", Arrays.asList("27", "17", "7"), "n7"));
        defaults.add(new AnswerSet("min", Arrays.asList("15", "9", "17"), "9"));
        defaults.add(new AnswerSet("min2", Arrays.asList("27", "17", "7"), "7"));
        defaults.add(new AnswerSet("cat", Arrays.asList("quy", "ca", "rang"), "ca"));
        defaults.add(new AnswerSet("nameserver", Arrays.asList("Anh", "Em", "Toi"), "Toi"));
        defaults.add(new AnswerSet("max", Arrays.asList("5", "2", "8"), "8"));
        defaults.add(new AnswerSet("sauA", Arrays.asList("Z", "B", "C"), "B"));
        defaults.add(new AnswerSet("RANK", Arrays.asList("Vang", "Tim", "Xanh"), "Vang"));
        defaults.add(new AnswerSet("9/3", Arrays.asList("3", "2", "4"), "3"));
        defaults.add(new AnswerSet("maxx", Arrays.asList("5", "6", "2"), "6"));
        defaults.add(new AnswerSet("snow", Arrays.asList("trang", "tim", "vang"), "trang"));
        defaults.add(new AnswerSet("8/4", Arrays.asList("2", "1", "3"), "2"));
        defaults.add(new AnswerSet("baydc", Arrays.asList("chim", "truc", "meo"), "chim"));
        defaults.add(new AnswerSet("summer", Arrays.asList("khong", "nong", "lanh"), "nong"));
        defaults.add(new AnswerSet("fish", Arrays.asList("sa", "song", "nui"), "song"));
        defaults.add(new AnswerSet("bautroi", Arrays.asList("trang", "tim", "xanh"), "xanh"));
        defaults.add(new AnswerSet("10-4", Arrays.asList("7", "6", "3"), "6"));
        defaults.add(new AnswerSet("svdang", Arrays.asList("fac", "sky", "bed"), "fac"));
        defaults.add(new AnswerSet("traidat", Arrays.asList("vuong", "tron", "tamgiac"), "tron"));
        defaults.add(new AnswerSet("min3", Arrays.asList("16", "17", "27"), "16"));
        defaults.add(new AnswerSet("maxxx", Arrays.asList("4", "6", "7"), "7"));
        defaults.add(new AnswerSet("catt", Arrays.asList("o o o", "meo meo", "gau gau"), "meo meo"));
        defaults.add(new AnswerSet("even", Arrays.asList("3", "7", "4"), "4"));
        defaults.add(new AnswerSet("5-1", Arrays.asList("4", "3", "5"), "4"));
        defaults.add(new AnswerSet("dog", Arrays.asList("cho", "meo", "ga"), "cho"));
        defaults.add(new AnswerSet("vuongortron", Arrays.asList("tron", "tam", "vuong"), "tron"));
        defaults.add(new AnswerSet("biennuocta", Arrays.asList("dong", "tay", "bac"), "dong"));
        defaults.add(new AnswerSet("5hay9", Arrays.asList("bang", "5", "9"), "9"));
        defaults.add(new AnswerSet("lacay", Arrays.asList("xanh", "den", "trang"), "xanh"));
        defaults.add(new AnswerSet("so10", Arrays.asList("12", "8", "9"), "9"));
        return defaults;
    }

    public void load() {
        File file = new File(Minecraft.getMinecraft().mcDataDir, CONFIG_FILE);
        if (!file.exists()) {
            LOGGER.info("Không tìm thấy file đáp án, tạo mới với {} bộ câu hỏi mặc định.", 31);
            sets = createDefaultSets();
            save();
            return;
        }

        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            Gson gson = new Gson();
            Type type = new TypeToken<List<AnswerSet>>() {
            }.getType();
            List<AnswerSet> loaded = gson.fromJson(reader, type);
            if (loaded != null) {
                sets = loaded;
                activeSet = null;
                for (AnswerSet set : sets) {
                    if (set.isActive()) {
                        activeSet = set;
                        break;
                    }
                }
                LOGGER.info("Đã tải {} bộ đáp án, active: {}", sets.size(),
                        activeSet != null ? activeSet.getName() : "không");
            } else {
                sets = new ArrayList<>();
            }
        } catch (IOException e) {
            LOGGER.error("Không thể đọc file đáp án", e);
        }
    }

    public AnswerSet findSetByName(String name) {
        for (AnswerSet set : sets) {
            if (set.getName().equalsIgnoreCase(name)) {
                return set;
            }
        }
        return null;
    }
}