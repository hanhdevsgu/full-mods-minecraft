package com.autorefill;

import java.io.File;
import net.minecraftforge.common.config.Configuration;

public class ConfigManager {
   public static boolean enabled = true;
   public static int delayTicks = 2;
   public static boolean autoDetectBaritone = true;
   public static boolean silent = false;
   public static int maxPV = 0;
   public static int currentPV = 1;
   private static Configuration config;

   public static void init(File configFile) {
      config = new Configuration(configFile);
      load();
   }

   public static void load() {
      if (config != null) {
         config.load();
         enabled = config.getBoolean("enabled", "general", true, "Bat hoac tat tinh nang");
         delayTicks = config.getInt("delayTicks", "general", 2, 1, 20, "Do tre giua cac lan lay block (ticks). 2 ticks = 100ms an toan tuyet doi");
         autoDetectBaritone = config.getBoolean("autoDetectBaritone", "general", true, "Tu dong nhan dien lenh Baritone nhu #set set <block>");
         silent = config.getBoolean("silent", "general", false, "An thong bao tren man hinh");
         maxPV = config.getInt("maxPV", "pv", 0, 0, 100, "So luong kho /pv toi da (0: tat)");
         currentPV = config.getInt("currentPV", "pv", 1, 1, 100, "So thu tu kho /pv hien tai");
         if (config.hasChanged()) {
            config.save();
         }
      }
   }

   public static void save() {
      if (config != null) {
         config.get("general", "enabled", true).setValue(enabled);
         config.get("general", "delayTicks", 2).setValue(delayTicks);
         config.get("general", "autoDetectBaritone", true).setValue(autoDetectBaritone);
         config.get("general", "silent", false).setValue(silent);
         config.get("pv", "maxPV", 0).setValue(maxPV);
         config.get("pv", "currentPV", 1).setValue(currentPV);
         config.save();
      }
   }
}
