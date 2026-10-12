package com.example.quizpeek;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Luu/doc thoi gian cho (giay, cho phep so thap phan, vd 3.5) TRUOC KHI
 * TooltipDumpHandler tu dong click dap an dung - sua qua GUI X (GuiTimingConfig)
 * roi luu vao file quizpeek_timing.json, ap dung ngay lap tuc (khong can build lai jar).
 */
public class TimingConfig {
    private static final Logger LOGGER = LogManager.getLogger("quizpeek");
    private static final String CONFIG_FILE = "quizpeek_timing.json";

    public static final float DEFAULT_DELAY_SECONDS = 1.5f;
    public static final float MIN_DELAY_SECONDS = 0.0f;
    public static final float MAX_DELAY_SECONDS = 60.0f;

    private static TimingConfig instance;

    public static TimingConfig getInstance() {
        if (instance == null) {
            instance = new TimingConfig();
        }
        return instance;
    }

    private static class Data {
        float delaySeconds = DEFAULT_DELAY_SECONDS;
    }

    private Data data = new Data();

    private TimingConfig() {
        load();
    }

    public float getDelaySeconds() {
        return data.delaySeconds;
    }

    /** Doi thoi gian cho (giay, cho phep thap phan) va luu ngay xuong file. */
    public void setDelaySeconds(float seconds) {
        data.delaySeconds = clamp(seconds);
        save();
    }

    /** So tick (20 tick/giay) tuong ung voi thoi gian cho hien tai - dung truc tiep trong TooltipDumpHandler. */
    public int getDelayTicks() {
        return Math.max(0, Math.round(data.delaySeconds * 20f));
    }

    private static float clamp(float v) {
        return Math.max(MIN_DELAY_SECONDS, Math.min(MAX_DELAY_SECONDS, v));
    }

    private File getFile() {
        return new File(Minecraft.getMinecraft().gameDir, CONFIG_FILE);
    }

    public void save() {
        File file = getFile();
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(data, writer);
            LOGGER.info("Đã lưu thời gian chờ tự click: {}s vào {}", data.delaySeconds, file.getAbsolutePath());
        } catch (IOException e) {
            LOGGER.error("Không thể lưu file cấu hình thời gian", e);
        }
    }

    public void load() {
        File file = getFile();
        if (!file.exists()) {
            LOGGER.info("Không tìm thấy file cấu hình thời gian, dùng mặc định {}s.", DEFAULT_DELAY_SECONDS);
            save();
            return;
        }
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            Gson gson = new Gson();
            Data loaded = gson.fromJson(reader, Data.class);
            if (loaded != null) {
                data = loaded;
                data.delaySeconds = clamp(data.delaySeconds);
            }
            LOGGER.info("Đã tải thời gian chờ tự click: {}s", data.delaySeconds);
        } catch (IOException e) {
            LOGGER.error("Không thể đọc file cấu hình thời gian", e);
        }
    }
}
