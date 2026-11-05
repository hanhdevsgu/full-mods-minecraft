package com.zorotool.macrorecorder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public class ConfigHelper {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static File configFile;
   private static boolean isLoaded = false;
   public static final int DEFAULT_VAULTS = 10;
   public static final int DEFAULT_FARM_ITEM_ID = 338;
   public static final float DEFAULT_MOVE_DELAY_SECONDS = 0.4F;
   public static final int DEFAULT_ITEM_TRANSFER_DELAY_MS = 0;
   public static final float DEFAULT_SHOP_DELAY_SECONDS = 7.0F;
   public static final float DEFAULT_CHEST_RETRY_INTERVAL_SECONDS = 0.2F;
   public static final float DEFAULT_LOOK_WAIT_SECONDS = 1.0F;
   public static final float DEFAULT_NPC_RIGHT_CLICK_INITIAL_DELAY_SECONDS = 0.5F;
   public static final float DEFAULT_NPC_CLICK_RETRY_INTERVAL_SECONDS = 0.3F;
   public static final float DEFAULT_NPC_REACH_DISTANCE = 4.5F;
   public static final float DEFAULT_HOME_WAIT_SECONDS = 10.0F;
   public static final float DEFAULT_BEFORE_SHOP_DELAY_SECONDS = 1.0F;
   public static final float DEFAULT_AFTER_SHOP_CLICK_DELAY_SECONDS = 5.0F;
   public static final float DEFAULT_SHOP_TO_SETHOME_DELAY_SECONDS = 4.0F;
   public static final String DEFAULT_AFTER_SHOP_GOTO_COMMAND = "#goto -103 150 116";
   public static final float DEFAULT_AFTER_SHOP_GOTO_WAIT_SECONDS = 2.0F;
   public static final String DEFAULT_AFTER_SHOP_LOOK_COMMAND = "";
   public static final String DEFAULT_SHOP_GOTO_COMMAND = "#goto -106 150 127";
   public static final String DEFAULT_SHOP_LOOK_COMMAND = "";
   public static final String DEFAULT_NEXT_CHEST_GOTO_COMMAND = "#goto ~ ~ ~-1";
   public static final String DEFAULT_SETHOME_COMMAND = "/sethome";
   public static final String DEFAULT_SHOP_COMMAND = "/shop";
   public static final String DEFAULT_HOME_MIA_COMMAND = "/HOME MIA";
   public static final float DEFAULT_QUEUE_DISTANCE_THRESHOLD = 10.0F;
   private static ConfigHelper.ConfigData data = new ConfigHelper.ConfigData();

   public static void init(FMLPreInitializationEvent event) {
      configFile = new File(event.getModConfigurationDirectory(), "macrorecorder.json");
      isLoaded = true;
      loadConfig();
      System.out.println("[MacroRecorder] ConfigHelper (JSON) initialized! File: " + configFile.getAbsolutePath());
   }

   public static void loadConfig() {
      if (!isLoaded) {
         System.out.println("[MacroRecorder] ConfigHelper not initialized yet!");
      } else {
         try {
            if (configFile.exists()) {
               try (FileReader reader = new FileReader(configFile)) {
                  ConfigHelper.ConfigData loaded = (ConfigHelper.ConfigData)GSON.fromJson(reader, ConfigHelper.ConfigData.class);
                  if (loaded != null) {
                     data = loaded;
                  }
               }
            }

            clampAll();
            saveConfig();
            logCurrent("LOADED CONFIG (JSON)");
         } catch (Exception var13) {
            System.err.println("[MacroRecorder] Lỗi load config JSON: " + var13.getMessage());
            data = new ConfigHelper.ConfigData();
         }
      }
   }

   public static void saveConfig() {
      if (!isLoaded) {
         System.out.println("[MacroRecorder] ConfigHelper not initialized!");
      } else {
         try {
            clampAll();

            try (FileWriter writer = new FileWriter(configFile)) {
               GSON.toJson(data, writer);
            }

            logCurrent("SAVED CONFIG (JSON)");
         } catch (Exception var13) {
            System.err.println("[MacroRecorder] Lỗi save config JSON: " + var13.getMessage());
         }
      }
   }

   public static void setConfig(int v, float moveDelay, float shopDelay, float afterShopClickDelay) {
      System.out.println("[MacroRecorder] Setting new config values...");
      data.vaults = v;
      data.moveDelaySeconds = moveDelay;
      data.shopDelaySeconds = shopDelay;
      data.afterShopClickDelaySeconds = afterShopClickDelay;
      saveConfig();
   }

   public static void setGotoLookConfig(
      String shopGoto, String shopLook, float beforeShopDelay, float shopToSethomeDelay, String afterShopGoto, float afterShopGotoWait, String afterShopLook
   ) {
      data.shopGotoCommand = nonBlankOrDefault(shopGoto, "#goto -106 150 127");
      data.shopLookCommand = nonBlankOrDefault(shopLook, "");
      data.beforeShopDelaySeconds = shopClamp(beforeShopDelay, 0.0F, 60.0F);
      data.shopToSethomeDelaySeconds = shopClamp(shopToSethomeDelay, 0.0F, 60.0F);
      data.afterShopGotoCommand = nonBlankOrDefault(afterShopGoto, "#goto -103 150 116");
      data.afterShopGotoWaitSeconds = shopClamp(afterShopGotoWait, 0.0F, 60.0F);
      data.afterShopLookCommand = nonBlankOrDefault(afterShopLook, "");
      saveConfig();
   }

   private static float shopClamp(float v, float min, float max) {
      return clampFloat(v, min, max);
   }

   public static float getShopToSethomeDelaySeconds() {
      return data.shopToSethomeDelaySeconds;
   }

   public static String getNextChestGotoCommand() {
      return data.nextChestGotoCommand;
   }

   public static void setNextChestGotoCommand(String command) {
      data.nextChestGotoCommand = nonBlankOrDefault(command, "#goto ~ ~ ~-1");
      saveConfig();
   }

   public static void setSethomeCommand(String command) {
      data.sethomeCommand = nonBlankOrDefault(command, "/sethome");
      saveConfig();
   }

   public static void setHomeMiaCommand(String command) {
      data.homeMiaCommand = nonBlankOrDefault(command, "/HOME MIA");
      saveConfig();
   }

   public static int getFarmItemId() {
      return data.farmItemId;
   }

   public static void setFarmItemId(int itemId) {
      data.farmItemId = itemId;
      saveConfig();
   }

   public static int getItemTransferDelayMs() {
      return data.itemTransferDelayMs;
   }

   public static void setItemTransferDelayMs(int delayMs) {
      data.itemTransferDelayMs = clampInt(delayMs, 0, 60000);
      saveConfig();
   }

   public static void reloadConfig() {
      System.out.println("[MacroRecorder] Reloading config from file...");
      loadConfig();
   }

   private static void clampAll() {
      data.vaults = clampInt(data.vaults, 1, 9999);
      data.farmItemId = clampInt(data.farmItemId, 1, 32767);
      data.itemTransferDelayMs = clampInt(data.itemTransferDelayMs, 0, 60000);
      data.moveDelaySeconds = clampFloat(data.moveDelaySeconds, 0.0F, 10.0F);
      data.shopDelaySeconds = clampFloat(data.shopDelaySeconds, 0.0F, 60.0F);
      data.chestRetryIntervalSeconds = clampFloat(data.chestRetryIntervalSeconds, 0.05F, 5.0F);
      data.lookWaitSeconds = clampFloat(data.lookWaitSeconds, 0.0F, 10.0F);
      data.npcRightClickInitialDelaySeconds = clampFloat(data.npcRightClickInitialDelaySeconds, 0.0F, 10.0F);
      data.npcClickRetryIntervalSeconds = clampFloat(data.npcClickRetryIntervalSeconds, 0.2F, 5.0F);
      data.npcReachDistance = clampFloat(data.npcReachDistance, 1.0F, 10.0F);
      data.homeWaitSeconds = clampFloat(data.homeWaitSeconds, 0.0F, 60.0F);
      data.beforeShopDelaySeconds = clampFloat(data.beforeShopDelaySeconds, 0.0F, 60.0F);
      data.afterShopClickDelaySeconds = clampFloat(data.afterShopClickDelaySeconds, 0.0F, 60.0F);
      data.shopToSethomeDelaySeconds = clampFloat(data.shopToSethomeDelaySeconds, 0.0F, 60.0F);
      data.afterShopGotoWaitSeconds = clampFloat(data.afterShopGotoWaitSeconds, 0.0F, 60.0F);
      if (data.accountOrder == null) {
         data.accountOrder = new ArrayList<>();
      }

      List<String> cleaned = new ArrayList<>();

      for (String name : data.accountOrder) {
         if (name != null && !name.trim().isEmpty()) {
            cleaned.add(name.trim());
         }
      }

      data.accountOrder = cleaned;
      data.queueDistanceThreshold = clampFloat(data.queueDistanceThreshold, 0.0F, 256.0F);
      data.shopGotoCommand = nonBlankOrDefault(data.shopGotoCommand, "#goto -106 150 127");
      data.shopLookCommand = data.shopLookCommand == null ? "" : data.shopLookCommand;
      data.nextChestGotoCommand = nonBlankOrDefault(data.nextChestGotoCommand, "#goto ~ ~ ~-1");
      data.afterShopGotoCommand = nonBlankOrDefault(data.afterShopGotoCommand, "#goto -103 150 116");
      if (data.afterShopLookCommand != null && data.afterShopLookCommand.trim().startsWith(".look")) {
         data.afterShopLookCommand = "";
      }

      data.afterShopLookCommand = data.afterShopLookCommand == null ? "" : data.afterShopLookCommand;
      data.sethomeCommand = nonBlankOrDefault(data.sethomeCommand, "/sethome");
      data.shopCommand = nonBlankOrDefault(data.shopCommand, "/shop");
      data.homeMiaCommand = nonBlankOrDefault(data.homeMiaCommand, "/HOME MIA");
   }

   private static String nonBlankOrDefault(String value, String defaultValue) {
      return value != null && !value.trim().isEmpty() ? value : defaultValue;
   }

   private static int clampInt(int v, int min, int max) {
      return Math.max(min, Math.min(max, v));
   }

   private static float clampFloat(float v, float min, float max) {
      return Math.max(min, Math.min(max, v));
   }

   private static void logCurrent(String title) {
      System.out.println("[MacroRecorder] ========================================");
      System.out.println("[MacroRecorder] " + title + " (" + configFile.getAbsolutePath() + "):");
      System.out
         .println("[MacroRecorder] - vaults=" + data.vaults + " moveDelaySeconds=" + data.moveDelaySeconds + " shopDelaySeconds=" + data.shopDelaySeconds);
      System.out.println("[MacroRecorder] - chestRetryIntervalSeconds=" + data.chestRetryIntervalSeconds + " lookWaitSeconds=" + data.lookWaitSeconds);
      System.out
         .println(
            "[MacroRecorder] - npcRightClickInitialDelaySeconds="
               + data.npcRightClickInitialDelaySeconds
               + " npcClickRetryIntervalSeconds="
               + data.npcClickRetryIntervalSeconds
               + " npcReachDistance="
               + data.npcReachDistance
         );
      System.out.println("[MacroRecorder] - homeWaitSeconds=" + data.homeWaitSeconds + " beforeShopDelaySeconds=" + data.beforeShopDelaySeconds);
      System.out
         .println(
            "[MacroRecorder] - afterShopClickDelaySeconds="
               + data.afterShopClickDelaySeconds
               + " shopToSethomeDelaySeconds="
               + data.shopToSethomeDelaySeconds
               + " afterShopGotoWaitSeconds="
               + data.afterShopGotoWaitSeconds
         );
      System.out.println("[MacroRecorder] - shopGotoCommand=\"" + data.shopGotoCommand + "\" shopLookCommand=\"" + data.shopLookCommand + "\"");
      System.out
         .println("[MacroRecorder] - afterShopGotoCommand=\"" + data.afterShopGotoCommand + "\" afterShopLookCommand=\"" + data.afterShopLookCommand + "\"");
      System.out.println("[MacroRecorder] - accountOrder=" + data.accountOrder + " queueDistanceThreshold=" + data.queueDistanceThreshold + "m");
      System.out.println("[MacroRecorder] ========================================");
   }

   public static int getVaults() {
      return data.vaults;
   }

   public static float getMoveDelaySeconds() {
      return data.moveDelaySeconds;
   }

   public static float getShopDelaySeconds() {
      return data.shopDelaySeconds;
   }

   public static float getChestRetryIntervalSeconds() {
      return data.chestRetryIntervalSeconds;
   }

   public static float getLookWaitSeconds() {
      return data.lookWaitSeconds;
   }

   public static float getNpcRightClickInitialDelaySeconds() {
      return data.npcRightClickInitialDelaySeconds;
   }

   public static float getNpcClickRetryIntervalSeconds() {
      return data.npcClickRetryIntervalSeconds;
   }

   public static float getNpcReachDistance() {
      return data.npcReachDistance;
   }

   public static float getHomeWaitSeconds() {
      return data.homeWaitSeconds;
   }

   public static float getBeforeShopDelaySeconds() {
      return data.beforeShopDelaySeconds;
   }

   public static String getShopGotoCommand() {
      return data.shopGotoCommand;
   }

   public static String getShopLookCommand() {
      return data.shopLookCommand;
   }

   public static float getAfterShopClickDelaySeconds() {
      return data.afterShopClickDelaySeconds;
   }

   public static String getAfterShopGotoCommand() {
      return data.afterShopGotoCommand;
   }

   public static float getAfterShopGotoWaitSeconds() {
      return data.afterShopGotoWaitSeconds;
   }

   public static String getAfterShopLookCommand() {
      return data.afterShopLookCommand;
   }

   public static String getSethomeCommand() {
      return data.sethomeCommand;
   }

   public static String getShopCommand() {
      return data.shopCommand;
   }

   public static String getHomeMiaCommand() {
      return data.homeMiaCommand;
   }

   public static List<String> getAccountOrder() {
      return new ArrayList<>(data.accountOrder);
   }

   public static void setAccountOrder(List<String> order) {
      List<String> cleaned = new ArrayList<>();

      for (String name : order) {
         if (name != null && !name.trim().isEmpty()) {
            cleaned.add(name.trim());
         }
      }

      data.accountOrder = cleaned;
      saveConfig();
   }

   public static float getQueueDistanceThreshold() {
      return data.queueDistanceThreshold;
   }

   public static void setQueueDistanceThreshold(float meters) {
      data.queueDistanceThreshold = clampFloat(meters, 0.0F, 256.0F);
      saveConfig();
   }

   private static class ConfigData {
      int vaults = 10;
      int farmItemId = 338;
      int itemTransferDelayMs = 0;
      float moveDelaySeconds = 0.4F;
      float shopDelaySeconds = 7.0F;
      float chestRetryIntervalSeconds = 0.2F;
      float lookWaitSeconds = 1.0F;
      float npcRightClickInitialDelaySeconds = 0.5F;
      float npcClickRetryIntervalSeconds = 0.3F;
      float npcReachDistance = 4.5F;
      float homeWaitSeconds = 10.0F;
      float beforeShopDelaySeconds = 1.0F;
      float afterShopClickDelaySeconds = 5.0F;
      float shopToSethomeDelaySeconds = 4.0F;
      String afterShopGotoCommand = "#goto -103 150 116";
      float afterShopGotoWaitSeconds = 2.0F;
      String afterShopLookCommand = "";
      String shopGotoCommand = "#goto -106 150 127";
      String shopLookCommand = "";
      String nextChestGotoCommand = "#goto ~ ~ ~-1";
      String sethomeCommand = "/sethome";
      String shopCommand = "/shop";
      String homeMiaCommand = "/HOME MIA";
      List<String> accountOrder = new ArrayList<>();
      float queueDistanceThreshold = 10.0F;

      private ConfigData() {
      }
   }
}
