package com.zorotool.macrorecorder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * TOAN BO config (so kho /pv, cac delay di chuyen/shop, tat ca cac delay
 * lenh khac, VA cac lenh #goto/.look toi shop + diem lay mia - truoc chi la
 * hang so cung trong code) gio duoc gop chung vao 1 FILE JSON DUY NHAT:
 * config/macrorecorder.json.
 *
 * GUI F8 (xem GuiMacroConfig) co them o ID vat pham farm (F3+H), mac dinh 338 = minecraft:reeds,
 * ben canh cac o vaults/delay. ID nay duoc luu trong macrorecorder.json va duoc dung truc tiep
 * khi dem/lay/chuyen vat pham farm. Cac
 * delay con lai (chest retry, look wait, npc right-click, home...) KHONG co
 * o trong GUI - muon chinh thi mo thang file JSON len sua bang tay, roi bam
 * F8 (mo GUI) 1 lan de ConfigHelper.reloadConfig() doc lai file.
 */
public class ConfigHelper {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static File configFile;
    private static boolean isLoaded = false;

    // ===== Gia tri mac dinh (giu nguyen y het hang so cu truoc khi doi sang JSON) =====
    public static final int DEFAULT_VAULTS = 10;
    // ID vat pham dung de farm (F3+H trong Minecraft 1.12.2). Mac dinh: REEDS = 338.
    public static final int DEFAULT_FARM_ITEM_ID = 338;
    public static final float DEFAULT_MOVE_DELAY_SECONDS = 0.4f;
    // Delay giua cac lan windowClick khi lay/bo item. 0 = toc do sieu nhanh hien tai.
    // Don vi milliseconds de nguoi dung co the tu cam nhan va chinh trong F8.
    public static final int DEFAULT_ITEM_TRANSFER_DELAY_MS = 0;
    public static final float DEFAULT_SHOP_DELAY_SECONDS = 7.0f;
    public static final float DEFAULT_CHEST_RETRY_INTERVAL_SECONDS = 0.2f;      // truoc: CHEST_RETRY_INTERVAL_TICKS = 4
    public static final float DEFAULT_LOOK_WAIT_SECONDS = 1.0f;                 // truoc: LOOK_WAIT_TICKS = 20
    public static final float DEFAULT_NPC_RIGHT_CLICK_INITIAL_DELAY_SECONDS = 0.5f; // truoc: NPC_RIGHT_CLICK_INITIAL_DELAY_TICKS = 40
    // Khoang nghi GIUA 2 LAN click NPC lien tiep khi retry (KHONG dung chung
    // chestRetryIntervalSeconds nua - 0.2s la qua nhanh so voi nguoi that va
    // nhieu NPC plugin co co che chong-spam-click se AM THAM BO QUA click neu
    // click lai qua nhanh trong luc lan truoc chua kip xu ly xong). 1 giay la
    // toc do click lai gan voi nguoi that, giup NPC nhan click thay vi bi lo.
    public static final float DEFAULT_NPC_CLICK_RETRY_INTERVAL_SECONDS = 0.3f;
    public static final float DEFAULT_NPC_REACH_DISTANCE = 4.5f;                // moi: ban kinh do tim NPC bang tay (fix loi trat NPC)
    public static final float DEFAULT_HOME_WAIT_SECONDS = 10.0f;                // truoc: HOME_WAIT_TICKS = 200
    // Cho bao lau SAU khi #goto LAN CUOI roi khoi kho cuoi cung, TRUOC khi gui
    // /sethome + /shop (truoc: hard-code cung TICKS_PER_SECOND = 1 giay).
    public static final float DEFAULT_BEFORE_SHOP_DELAY_SECONDS = 1.0f;

    // ===== Chuoi hanh dong SAU KHI da chuot phai NPC shop (F6-style) THANH CONG =====
    // ===== (tuc GUI shop da mo/xac nhan) - truoc khi quay lai chay logic BAN    =====
    // ===== (F4) tu dau: cho afterShopClickDelaySeconds (mac dinh 5s, CO trong  =====
    // ===== GUI F8 - o thu 4) -> #goto afterShopGotoCommand (roi khoi khu shop) =====
    // ===== -> cho afterShopGotoWaitSeconds (mac dinh 2s) -> gui .look          =====
    // ===== afterShopLookCommand -> roi moi bat dau lai F4 (ban mia) tu dau.    =====
    public static final float DEFAULT_AFTER_SHOP_CLICK_DELAY_SECONDS = 5.0f;
    public static final float DEFAULT_SHOP_TO_SETHOME_DELAY_SECONDS = 4.0f;
    public static final String DEFAULT_AFTER_SHOP_GOTO_COMMAND = "#goto -103 150 116";
    public static final float DEFAULT_AFTER_SHOP_GOTO_WAIT_SECONDS = 2.0f;
    public static final String DEFAULT_AFTER_SHOP_LOOK_COMMAND = "";

    // ===== Cac lenh #goto / .look (truoc day la hang so String cung trong code) =====
    // ===== gio cung nam trong JSON de sua toa do / diem den ma khong can build lai jar. =====
    public static final String DEFAULT_SHOP_GOTO_COMMAND = "#goto -106 150 127";
    // Bo hoan toan .look vi da co AutoAim (/aimsell) tu dong ngam bang mượt ma nguoi that
    public static final String DEFAULT_SHOP_LOOK_COMMAND = "";
    public static final String DEFAULT_NEXT_CHEST_GOTO_COMMAND = "#goto ~ ~ ~-1";

    // Cac lenh chat "tran trui" (khong qua #goto/.look) - TRUOC day la hang so
    // String cung trong code (vd trySendCommand("/shop")) nen sua file JSON
    // khong co tac dung gi. Gio dua vao JSON giong shopGotoCommand/shopLookCommand
    // de co the doi noi dung lenh ma khong can build lai jar.
    public static final String DEFAULT_SETHOME_COMMAND = "/sethome";
    public static final String DEFAULT_SHOP_COMMAND = "/shop";
    public static final String DEFAULT_HOME_MIA_COMMAND = "/HOME MIA";

    // ===== DANH SACH THU TU TAI KHOAN (nhieu nick mo cung luc F4) - GUI phim U =====
    // ===== (xem GuiAccountOrder). accountOrder la thu tu "ai chay truoc, ai chay =====
    // ===== sau": phan tu dau tien LUON duoc chay ngay (khong cho gi ca) khi toi =====
    // ===== doan chuan bi /shop; tu phan tu thu 2 tro di phai cho khoang cach (met, =====
    // ===== xem queueDistanceThreshold) truoc khi duoc chay tiep - logic chi tiet  =====
    // ===== xem MacroRecorderMod.canProceedToShop().                              =====
    public static final float DEFAULT_QUEUE_DISTANCE_THRESHOLD = 10.0f;
    // (Da bo homeLookCommand theo yeu cau - KHONG con gui lenh .look nao khi bat
    // dau vong lap lay mia (F9) sau /HOME MIA nua, chi con cho beforeShopDelaySeconds/
    // lookWaitTicks binh thuong roi mo ruong dau tien luon.)

    /** Cau truc du lieu duoc luu thang xuong file JSON (Gson tu doc/ghi cac field nay). */
    private static class ConfigData {
        int vaults = DEFAULT_VAULTS;
        int farmItemId = DEFAULT_FARM_ITEM_ID;
        int itemTransferDelayMs = DEFAULT_ITEM_TRANSFER_DELAY_MS;
        float moveDelaySeconds = DEFAULT_MOVE_DELAY_SECONDS;
        float shopDelaySeconds = DEFAULT_SHOP_DELAY_SECONDS;
        float chestRetryIntervalSeconds = DEFAULT_CHEST_RETRY_INTERVAL_SECONDS;
        float lookWaitSeconds = DEFAULT_LOOK_WAIT_SECONDS;
        float npcRightClickInitialDelaySeconds = DEFAULT_NPC_RIGHT_CLICK_INITIAL_DELAY_SECONDS;
        float npcClickRetryIntervalSeconds = DEFAULT_NPC_CLICK_RETRY_INTERVAL_SECONDS;
        float npcReachDistance = DEFAULT_NPC_REACH_DISTANCE;
        float homeWaitSeconds = DEFAULT_HOME_WAIT_SECONDS;
        float beforeShopDelaySeconds = DEFAULT_BEFORE_SHOP_DELAY_SECONDS;
        // Chuoi hanh dong SAU KHI chuot phai NPC shop (F6-style) xac nhan xong,
        // TRUOC khi quay lai chay F4 (ban mia) tu dau (xem MacroRecorderMod).
        float afterShopClickDelaySeconds = DEFAULT_AFTER_SHOP_CLICK_DELAY_SECONDS; // GUI F8
        float shopToSethomeDelaySeconds = DEFAULT_SHOP_TO_SETHOME_DELAY_SECONDS;
        String afterShopGotoCommand = DEFAULT_AFTER_SHOP_GOTO_COMMAND;
        float afterShopGotoWaitSeconds = DEFAULT_AFTER_SHOP_GOTO_WAIT_SECONDS;
        String afterShopLookCommand = DEFAULT_AFTER_SHOP_LOOK_COMMAND;

        // Lenh #goto toi shop + .look vao NPC shop (chay sau khi lay xong cac kho).
        String shopGotoCommand = DEFAULT_SHOP_GOTO_COMMAND;
        String shopLookCommand = DEFAULT_SHOP_LOOK_COMMAND;
        String nextChestGotoCommand = DEFAULT_NEXT_CHEST_GOTO_COMMAND;

        // Cac lenh chat "tran trui" gui truc tiep qua trySendCommand() - MOI them vao JSON.
        String sethomeCommand = DEFAULT_SETHOME_COMMAND;
        String shopCommand = DEFAULT_SHOP_COMMAND;
        String homeMiaCommand = DEFAULT_HOME_MIA_COMMAND;

        // Danh sach username theo THU TU chay (nick dau tien chay truoc), dung
        // chung 1 file JSON cho CA 3 nick (moi client tu doc file cua rieng no,
        // nhung ban nen dat CUNG 1 noi dung cho ca 3 de dong bo dung thu tu).
        List<String> accountOrder = new ArrayList<>();
        // Khoang cach toi thieu (met) can co giua 2 nick lien tiep truoc khi nick
        // phia sau duoc phep chay tiep doan #goto/shop - xem canProceedToShop().
        float queueDistanceThreshold = DEFAULT_QUEUE_DISTANCE_THRESHOLD;
    }

    private static ConfigData data = new ConfigData();

    public static void init(FMLPreInitializationEvent event) {
        configFile = new File(event.getModConfigurationDirectory(), "macrorecorder.json");
        isLoaded = true;
        loadConfig();
        System.out.println("[MacroRecorder] ConfigHelper (JSON) initialized! File: " + configFile.getAbsolutePath());
    }

    public static void loadConfig() {
        if (!isLoaded) {
            System.out.println("[MacroRecorder] ConfigHelper not initialized yet!");
            return;
        }
        try {
            if (configFile.exists()) {
                try (FileReader reader = new FileReader(configFile)) {
                    ConfigData loaded = GSON.fromJson(reader, ConfigData.class);
                    if (loaded != null) {
                        data = loaded;
                    }
                }
            }
            clampAll();
            // Ghi lai ngay sau khi doc: file cu thieu field moi (vd lan dau nang cap
            // tu .cfg sang .json) se tu dong duoc bo sung gia tri mac dinh va luu lai.
            saveConfig();
            logCurrent("LOADED CONFIG (JSON)");
        } catch (Exception e) {
            System.err.println("[MacroRecorder] Lỗi load config JSON: " + e.getMessage());
            data = new ConfigData();
        }
    }

    public static void saveConfig() {
        if (!isLoaded) {
            System.out.println("[MacroRecorder] ConfigHelper not initialized!");
            return;
        }
        try {
            clampAll();
            try (FileWriter writer = new FileWriter(configFile)) {
                GSON.toJson(data, writer);
            }
            logCurrent("SAVED CONFIG (JSON)");
        } catch (Exception e) {
            System.err.println("[MacroRecorder] Lỗi save config JSON: " + e.getMessage());
        }
    }

    /** Duoc goi tu GUI F8 (gio co THEM 1 tham so thu 4: afterShopClickDelay). */
    public static void setConfig(int v, float moveDelay, float shopDelay, float afterShopClickDelay) {
        System.out.println("[MacroRecorder] Setting new config values...");
        data.vaults = v;
        data.moveDelaySeconds = moveDelay;
        data.shopDelaySeconds = shopDelay;
        data.afterShopClickDelaySeconds = afterShopClickDelay;
        saveConfig();
    }

    public static void setGotoLookConfig(String shopGoto, String shopLook, float beforeShopDelay,
                                         float shopToSethomeDelay, String afterShopGoto,
                                         float afterShopGotoWait, String afterShopLook) {
        data.shopGotoCommand = nonBlankOrDefault(shopGoto, DEFAULT_SHOP_GOTO_COMMAND);
        data.shopLookCommand = nonBlankOrDefault(shopLook, DEFAULT_SHOP_LOOK_COMMAND);
        data.beforeShopDelaySeconds = shopClamp(beforeShopDelay, 0f, 60f);
        data.shopToSethomeDelaySeconds = shopClamp(shopToSethomeDelay, 0f, 60f);
        data.afterShopGotoCommand = nonBlankOrDefault(afterShopGoto, DEFAULT_AFTER_SHOP_GOTO_COMMAND);
        data.afterShopGotoWaitSeconds = shopClamp(afterShopGotoWait, 0f, 60f);
        data.afterShopLookCommand = nonBlankOrDefault(afterShopLook, DEFAULT_AFTER_SHOP_LOOK_COMMAND);
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
        data.nextChestGotoCommand = nonBlankOrDefault(command, DEFAULT_NEXT_CHEST_GOTO_COMMAND);
        saveConfig();
    }

    /** ID vat pham farm hien tai (F3+H). */
    public static int getFarmItemId() {
        return data.farmItemId;
    }

    /** Luu ID vat pham farm (F3+H). */
    public static void setFarmItemId(int itemId) {
        data.farmItemId = itemId;
        saveConfig();
    }

    /** Delay giua cac click lay/bo item trong ruong. 0 = giu nguyen toc do toi da. */
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
        data.moveDelaySeconds = clampFloat(data.moveDelaySeconds, 0f, 10f);
        data.shopDelaySeconds = clampFloat(data.shopDelaySeconds, 0f, 60f);
        data.chestRetryIntervalSeconds = clampFloat(data.chestRetryIntervalSeconds, 0.05f, 5f);
        data.lookWaitSeconds = clampFloat(data.lookWaitSeconds, 0f, 10f);
        data.npcRightClickInitialDelaySeconds = clampFloat(data.npcRightClickInitialDelaySeconds, 0f, 10f);
        data.npcClickRetryIntervalSeconds = clampFloat(data.npcClickRetryIntervalSeconds, 0.2f, 5f);
        data.npcReachDistance = clampFloat(data.npcReachDistance, 1f, 10f);
        data.homeWaitSeconds = clampFloat(data.homeWaitSeconds, 0f, 60f);
        data.beforeShopDelaySeconds = clampFloat(data.beforeShopDelaySeconds, 0f, 60f);
        data.afterShopClickDelaySeconds = clampFloat(data.afterShopClickDelaySeconds, 0f, 60f);
        data.shopToSethomeDelaySeconds = clampFloat(data.shopToSethomeDelaySeconds, 0f, 60f);
        data.afterShopGotoWaitSeconds = clampFloat(data.afterShopGotoWaitSeconds, 0f, 60f);

        if (data.accountOrder == null) {
            data.accountOrder = new ArrayList<>();
        }
        // Bo cac dong trong/thua khoang trang trong danh sach (vd loi nhap tay JSON).
        List<String> cleaned = new ArrayList<>();
        for (String name : data.accountOrder) {
            if (name != null && !name.trim().isEmpty()) {
                cleaned.add(name.trim());
            }
        }
        data.accountOrder = cleaned;
        data.queueDistanceThreshold = clampFloat(data.queueDistanceThreshold, 0f, 256f);

        // Neu file JSON thieu/de trong cac lenh goto/look (vd nang cap tu ban cu,
        // hoac nguoi dung lo xoa) thi dung lai gia tri mac dinh, tranh gui lenh rong.
        data.shopGotoCommand = nonBlankOrDefault(data.shopGotoCommand, DEFAULT_SHOP_GOTO_COMMAND);
        data.shopLookCommand = data.shopLookCommand == null ? "" : data.shopLookCommand;
        data.nextChestGotoCommand = nonBlankOrDefault(data.nextChestGotoCommand, DEFAULT_NEXT_CHEST_GOTO_COMMAND);
        data.afterShopGotoCommand = nonBlankOrDefault(data.afterShopGotoCommand, DEFAULT_AFTER_SHOP_GOTO_COMMAND);
        if (data.afterShopLookCommand != null && data.afterShopLookCommand.trim().startsWith(".look")) {
            data.afterShopLookCommand = "";
        }
        data.afterShopLookCommand = data.afterShopLookCommand == null ? "" : data.afterShopLookCommand;
        data.sethomeCommand = nonBlankOrDefault(data.sethomeCommand, DEFAULT_SETHOME_COMMAND);
        data.shopCommand = nonBlankOrDefault(data.shopCommand, DEFAULT_SHOP_COMMAND);
        data.homeMiaCommand = nonBlankOrDefault(data.homeMiaCommand, DEFAULT_HOME_MIA_COMMAND);
    }

    private static String nonBlankOrDefault(String value, String defaultValue) {
        return (value == null || value.trim().isEmpty()) ? defaultValue : value;
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
        System.out.println("[MacroRecorder] - vaults=" + data.vaults
                + " moveDelaySeconds=" + data.moveDelaySeconds
                + " shopDelaySeconds=" + data.shopDelaySeconds);
        System.out.println("[MacroRecorder] - chestRetryIntervalSeconds=" + data.chestRetryIntervalSeconds
                + " lookWaitSeconds=" + data.lookWaitSeconds);
        System.out.println("[MacroRecorder] - npcRightClickInitialDelaySeconds=" + data.npcRightClickInitialDelaySeconds
                + " npcClickRetryIntervalSeconds=" + data.npcClickRetryIntervalSeconds
                + " npcReachDistance=" + data.npcReachDistance);
        System.out.println("[MacroRecorder] - homeWaitSeconds=" + data.homeWaitSeconds
                + " beforeShopDelaySeconds=" + data.beforeShopDelaySeconds);
        System.out.println("[MacroRecorder] - afterShopClickDelaySeconds=" + data.afterShopClickDelaySeconds
                + " shopToSethomeDelaySeconds=" + data.shopToSethomeDelaySeconds
                + " afterShopGotoWaitSeconds=" + data.afterShopGotoWaitSeconds);
        System.out.println("[MacroRecorder] - shopGotoCommand=\"" + data.shopGotoCommand
                + "\" shopLookCommand=\"" + data.shopLookCommand + "\"");
        System.out.println("[MacroRecorder] - afterShopGotoCommand=\"" + data.afterShopGotoCommand
                + "\" afterShopLookCommand=\"" + data.afterShopLookCommand + "\"");
        System.out.println("[MacroRecorder] - accountOrder=" + data.accountOrder
                + " queueDistanceThreshold=" + data.queueDistanceThreshold + "m");
        System.out.println("[MacroRecorder] ========================================");
    }

    // ===== GETTERS - dung cho GUI F8 (3 cai dau) va MacroRecorderMod (tat ca) =====
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

    // ===== DANH SACH THU TU TAI KHOAN - dung cho GUI (phim U, xem GuiAccountOrder) =====
    // ===== va cho MacroRecorderMod.canProceedToShop().                             =====

    /** Tra ve BAN SAO (khong tra ve list goc de tranh GUI sua truc tiep ma khong qua setAccountOrder). */
    public static List<String> getAccountOrder() {
        return new ArrayList<>(data.accountOrder);
    }

    /** Luu lai TOAN BO danh sach thu tu tai khoan (goi khi bam "Lưu" trong GUI phim U). */
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
        data.queueDistanceThreshold = clampFloat(meters, 0f, 256f);
        saveConfig();
    }
}
