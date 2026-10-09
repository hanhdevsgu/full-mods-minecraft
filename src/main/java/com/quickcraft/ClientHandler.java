package com.quickcraft;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiCrafting;
import net.minecraft.client.gui.inventory.GuiShulkerBox;
import net.minecraft.inventory.Container;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.gui.FontRenderer;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;

public class ClientHandler {
    public static KeyBinding OPEN_KEY;

    /** Item se tu craft khi mo ban che tao */
    public static String itemId = "42";
    /** Cong tat tong: OFF = mod an hoan toan (khong lam gi, khong log, khong hien gi) */
    public static boolean enabled = true;
    /** ON = tu dong dong GUI ban che tao khi craft xong; OFF = khong dong, tu bam E/ESC */
    public static boolean autoCloseCraft = true;
    /** ON = tu dong dong GUI ruong sau khi lay do; OFF = khong dong */
    public static boolean autoCloseChest = true;

    public static void init() {
        OPEN_KEY = new KeyBinding("key.quickcraft.open", Keyboard.KEY_V, "key.categories.quickcraft");
        ClientRegistry.registerKeyBinding(OPEN_KEY);
        loadConfig();
        MinecraftForge.EVENT_BUS.register(new ClientHandler());
    }

    public static void loadConfig() {
        if (QuickCraftMod.configFile == null) return;
        Configuration cfg = new Configuration(QuickCraftMod.configFile);
        itemId = cfg.getString("itemId", "general", "42", "ID item can craft (42, 35:14, minecraft:iron_block)");
        enabled = cfg.getBoolean("enabled", "general", true, "Bat/tat toan bo mod (OFF = an hoan toan)");
        autoCloseCraft = cfg.getBoolean("autoCloseCraft", "general", true, "Tu dong dong GUI ban che tao khi craft xong (OFF = khong dong)");
        autoCloseChest = cfg.getBoolean("autoCloseChest", "general", true, "Tu dong dong GUI ruong sau khi lay do (OFF = khong dong)");
        if (cfg.hasChanged()) cfg.save();
    }

    public static void saveConfig() {
        if (QuickCraftMod.configFile == null) return;
        Configuration cfg = new Configuration(QuickCraftMod.configFile);
        cfg.get("general", "itemId", "42").set(itemId);
        cfg.get("general", "enabled", true).set(enabled);
        cfg.get("general", "autoCloseCraft", true).set(autoCloseCraft);
        cfg.get("general", "autoCloseChest", true).set(autoCloseChest);
        cfg.save();
    }

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent event) {
        if (OPEN_KEY.isPressed()) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.currentScreen == null) {
                mc.displayGuiScreen(new GuiQuickCraft());
            }
        }
    }

    public static String lastStatus = "";
    public static long statusTime = 0;

    public static void setStatus(String msg) {
        lastStatus = msg;
        statusTime = System.currentTimeMillis();
    }

    private boolean wasChest = false;
    private boolean chestPending = false;
    private int chestTries = 0;
    private boolean wasCrafting = false;
    private int delay = 0;

    /** Mo ban che tao -> tu craft toi da, khong can bam gi */
    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!enabled) {
            chestPending = false;
            wasChest = false;
            wasCrafting = false;
            delay = 0;
            return;
        }
        ClientCrafter.tick();
        Minecraft mc = Minecraft.getMinecraft();
        tickChest(mc);
        boolean isCraft = mc.currentScreen instanceof GuiCrafting;
        if (isCraft && !wasCrafting) delay = 3; // doi vai tick cho server mo xong
        wasCrafting = isCraft;

        if (delay > 0 && --delay == 0 && isCraft) {
            if (mc.player == null) return;
            if (ItemResolver.parse(itemId) == null) {
                setStatus("\u00a7cID item khong hop le: " + itemId + " (bam V de sua)");
                return;
            }
            QuickCraftMod.logger.info("[QuickCraft] mo ban che tao -> craft '{}'", itemId);
            ClientCrafter.start(itemId);
        }
    }

    /** Mo ruong -> lay nguyen lieu ngay lap tuc (bam Ctrl khi mo ruong de bo qua). */
    private void tickChest(Minecraft mc) {
        boolean isChest = mc.currentScreen instanceof GuiChest || mc.currentScreen instanceof GuiShulkerBox;
        if (isChest && !wasChest) {
            chestPending = mc.player != null;
            chestTries = 0;
        }
        wasChest = isChest;
        if (!chestPending) return;
        if (!isChest || mc.player == null) { chestPending = false; return; }

        Container c = mc.player.openContainer;
        if (!ChestTaker.isPlainChest(c)) { chestPending = false; return; }
        if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)) {
            chestPending = false;
            return;
        }
        // doi server gui noi dung ruong (thuong cung tick, toi da vai tick)
        if (!ChestTaker.hasChestData(mc.player, c) && ++chestTries < 3) return;
        chestPending = false;
        if (ClientCrafter.isRunning()) return;
        ChestTaker.run(mc, c);
    }

    /** Ve ket qua len man hinh ban che tao */
    @SubscribeEvent
    public void onDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!enabled || !(event.getGui() instanceof GuiCrafting)) return;
        if (lastStatus.isEmpty() || System.currentTimeMillis() - statusTime > 6000) return;
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        fr.drawStringWithShadow("[QuickCraft] " + lastStatus, 6, 6, 0xFFFFFF);
    }
}
