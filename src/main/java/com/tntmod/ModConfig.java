package com.tntmod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File configFile;

    // Settings: Mặc định lúc nào vào game mod cũng TẮT (enabled = false), người chơi ấn F7 để bật
    public static boolean enabled = false;
    public static int dispenserTargetAmount = 64;
    public static boolean autoCloseChest = true;
    public static boolean autoCloseDispenser = true;
    public static boolean enableChestEsp = false;
    public static boolean enableDispenserEsp = true;
    // 0 = Block Kim Cương 3D Skin, 1 = Tô Đậm Xanh Neon Full Máy (Solid), 2 = Xanh Ngọc Trong Suốt
    public static int renderMode = 0;
    public static String teamRoomId = "default";
    public static String teamServerUrl = "https://tntmod-cloud-relay.onrender.com";
    public static boolean useClanChatSync = true;
    public static String teamChatCommandPrefix = "/f c c";

    private static class ConfigData {
        boolean enabled = false;
        int dispenserTargetAmount = 64;
        boolean autoCloseChest = true;
        boolean autoCloseDispenser = true;
        boolean enableChestEsp = false;
        boolean enableDispenserEsp = true;
        int renderMode = 0;
        String teamRoomId = "default";
        String teamServerUrl = "https://tntmod-cloud-relay.onrender.com";
        boolean useClanChatSync = true;
        String teamChatCommandPrefix = "/f c c";
    }

    public static void init() {
        File configDir = new File(Minecraft.getMinecraft().mcDataDir, "config");
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        configFile = new File(configDir, "tnt_autologistics.json");
        load();
    }

    public static void load() {
        if (configFile == null || !configFile.exists()) {
            save();
            return;
        }
        try (FileReader reader = new FileReader(configFile)) {
            ConfigData data = GSON.fromJson(reader, ConfigData.class);
            if (data != null) {
                // Yêu cầu: Mặc định lúc nào vào game nó cũng tắt, vào game ấn F7 để bật!
                enabled = false;
                dispenserTargetAmount = data.dispenserTargetAmount <= 0 ? 64 : data.dispenserTargetAmount;
                autoCloseChest = data.autoCloseChest;
                autoCloseDispenser = data.autoCloseDispenser;
                enableChestEsp = data.enableChestEsp;
                enableDispenserEsp = data.enableDispenserEsp;
                renderMode = (data.renderMode >= 0 && data.renderMode <= 2) ? data.renderMode : 0;
                teamRoomId = data.teamRoomId != null ? data.teamRoomId : "default";
                teamServerUrl = (data.teamServerUrl != null && !data.teamServerUrl.contains("127.0.0.1")) ? data.teamServerUrl : "https://tntmod-cloud-relay.onrender.com";
                useClanChatSync = data.useClanChatSync;
                teamChatCommandPrefix = data.teamChatCommandPrefix != null ? data.teamChatCommandPrefix : "/f c c";
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static void save() {
        if (configFile == null) return;
        try (FileWriter writer = new FileWriter(configFile)) {
            ConfigData data = new ConfigData();
            data.enabled = enabled;
            data.dispenserTargetAmount = dispenserTargetAmount;
            data.autoCloseChest = autoCloseChest;
            data.autoCloseDispenser = autoCloseDispenser;
            data.enableChestEsp = enableChestEsp;
            data.enableDispenserEsp = enableDispenserEsp;
            data.renderMode = renderMode;
            data.teamRoomId = teamRoomId;
            data.teamServerUrl = teamServerUrl;
            data.useClanChatSync = useClanChatSync;
            data.teamChatCommandPrefix = teamChatCommandPrefix;
            GSON.toJson(data, writer);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
