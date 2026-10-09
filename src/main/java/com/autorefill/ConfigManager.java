package com.autorefill;

import net.minecraftforge.common.config.Configuration;
import java.io.File;

public class ConfigManager {
    public static boolean enabled = true;
    public static int delayTicks = 2; // 2 ticks = 100ms, an toan tuyet doi
    public static boolean autoDetectBaritone = true;
    public static boolean silent = false;

    // Cau hinh rut kho /pv
    public static int maxPV = 0;      // 0 = tat tinh nang /pv, > 0 = so luong kho toi da (vd: 3 hoac 10)
    public static int currentPV = 1;  // So thu tu kho dang mo

    private static Configuration config;

    public static void init(File configFile) {
        config = new Configuration(configFile);
        load();
    }

    public static void load() {
        if (config == null) return;
        config.load();

        enabled = config.getBoolean("enabled", Configuration.CATEGORY_GENERAL, true, "Bat hoac tat tinh nang");
        delayTicks = config.getInt("delayTicks", Configuration.CATEGORY_GENERAL, 2, 1, 20, "Do tre giua cac lan lay block (ticks). 2 ticks = 100ms an toan tuyet doi");
        autoDetectBaritone = config.getBoolean("autoDetectBaritone", Configuration.CATEGORY_GENERAL, true, "Tu dong nhan dien lenh Baritone nhu #set set <block>");
        silent = config.getBoolean("silent", Configuration.CATEGORY_GENERAL, false, "An thong bao tren man hinh");

        maxPV = config.getInt("maxPV", "pv", 0, 0, 100, "So luong kho /pv toi da (0: tat)");
        currentPV = config.getInt("currentPV", "pv", 1, 1, 100, "So thu tu kho /pv hien tai");

        if (config.hasChanged()) {
            config.save();
        }
    }

    public static void save() {
        if (config == null) return;
        config.get(Configuration.CATEGORY_GENERAL, "enabled", true).setValue(enabled);
        config.get(Configuration.CATEGORY_GENERAL, "delayTicks", 2).setValue(delayTicks);
        config.get(Configuration.CATEGORY_GENERAL, "autoDetectBaritone", true).setValue(autoDetectBaritone);
        config.get(Configuration.CATEGORY_GENERAL, "silent", false).setValue(silent);

        config.get("pv", "maxPV", 0).setValue(maxPV);
        config.get("pv", "currentPV", 1).setValue(currentPV);

        config.save();
    }
}
