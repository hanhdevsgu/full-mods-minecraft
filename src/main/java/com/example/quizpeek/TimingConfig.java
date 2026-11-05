package com.example.quizpeek;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TimingConfig {
   private static final Logger LOGGER = LogManager.getLogger("quizpeek");
   private static final String CONFIG_FILE = "quizpeek_timing.json";
   public static final float DEFAULT_DELAY_SECONDS = 3.0F;
   public static final float MIN_DELAY_SECONDS = 0.0F;
   public static final float MAX_DELAY_SECONDS = 60.0F;
   private static TimingConfig instance;
   private TimingConfig.Data data = new TimingConfig.Data();

   public static TimingConfig getInstance() {
      if (instance == null) {
         instance = new TimingConfig();
      }

      return instance;
   }

   private TimingConfig() {
      this.load();
   }

   public float getDelaySeconds() {
      return this.data.delaySeconds;
   }

   public void setDelaySeconds(float seconds) {
      this.data.delaySeconds = clamp(seconds);
      this.save();
   }

   public int getDelayTicks() {
      return Math.max(0, Math.round(this.data.delaySeconds * 20.0F));
   }

   private static float clamp(float v) {
      return Math.max(0.0F, Math.min(60.0F, v));
   }

   private File getFile() {
      return new File(Minecraft.getMinecraft().gameDir, "quizpeek_timing.json");
   }

   public void save() {
      File file = this.getFile();

      try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
         Gson gson = new GsonBuilder().setPrettyPrinting().create();
         gson.toJson(this.data, writer);
         LOGGER.info("Đã lưu thời gian chờ tự click: {}s vào {}", this.data.delaySeconds, file.getAbsolutePath());
      } catch (IOException var15) {
         LOGGER.error("Không thể lưu file cấu hình thời gian", var15);
      }
   }

   public void load() {
      File file = this.getFile();
      if (!file.exists()) {
         LOGGER.info("Không tìm thấy file cấu hình thời gian, dùng mặc định {}s.", 3.0F);
         this.save();
      } else {
         try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            Gson gson = new Gson();
            TimingConfig.Data loaded = (TimingConfig.Data)gson.fromJson(reader, TimingConfig.Data.class);
            if (loaded != null) {
               this.data = loaded;
               this.data.delaySeconds = clamp(this.data.delaySeconds);
            }

            LOGGER.info("Đã tải thời gian chờ tự click: {}s", this.data.delaySeconds);
         } catch (IOException var16) {
            LOGGER.error("Không thể đọc file cấu hình thời gian", var16);
         }
      }
   }

   private static class Data {
      float delaySeconds = 3.0F;

      private Data() {
      }
   }
}
