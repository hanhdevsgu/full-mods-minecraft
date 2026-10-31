package com.zorotool.macrorecorder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

@Mod(modid = MacroRecorderMod.MODID, version = MacroRecorderMod.VERSION, acceptedMinecraftVersions = "[1.12.2]")
public class MacroRecorderMod {
    public static final String MODID = "macrorecorder";
    public static final String VERSION = "1.0";

    private static MacroRecorderMod INSTANCE;
    public static MacroRecorderMod getInstance() {
        return INSTANCE;
    }

    private int itemId = 338;
    private int pvNumber = 10;
    private Minecraft mc;
    private KeyBinding f4Key;
    private KeyBinding f8Key;
    private boolean running = false;
    private File configFile;
    private State state = State.IDLE;
    private int waitTicks = 0;
    private int currentPv = 1;
    private boolean sellPvEmptyNeedsNext = false;
    private int commandCooldownTicks = 0;
    private int transferStallTicks = 0;
    private int sellStallTicks = 0;
    private int lastInventoryCount = -1;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        INSTANCE = this;
        this.mc = Minecraft.func_71410_x();
        MinecraftForge.EVENT_BUS.register((Object) this);

        // Khoi tao he thong Ban Quyen
        LicenseGuard.init();

        this.f4Key = new KeyBinding("F4 Start / Dung", 62, "Macro Recorder");
        this.f8Key = new KeyBinding("F8 Cai dat", 66, "Macro Recorder");
        ClientRegistry.registerKeyBinding((KeyBinding) this.f4Key);
        ClientRegistry.registerKeyBinding((KeyBinding) this.f8Key);

        this.loadSettings();
        System.out.println("[MacroRecorder] Sell Kho San Sang - F4 Start/Dung + F8 Cai dat");
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void onGuiOpen(GuiOpenEvent event) {
        if (!LicenseGuard.isLicensed) {
            if (event != null && event.getGui() != null && !(event.getGui() instanceof GuiLicenseGate)) {
                event.setGui(new GuiLicenseGate(event.getGui()));
            }
        }
    }

    private File resolveConfigFile() {
        if (this.configFile != null) {
            return this.configFile;
        }
        File configDir = Loader.instance().getConfigDir();
        this.configFile = new File(configDir, "macrorecorder.properties");
        return this.configFile;
    }

    private void loadSettings() {
        File file = this.resolveConfigFile();
        if (!file.exists()) {
            this.saveSettingsToDisk();
            return;
        }
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(file)) {
            props.load(in);
        } catch (Exception e) {
            System.out.println("[MacroRecorder] Loi doc config: " + e);
            return;
        }
        this.itemId = this.parseIntSafe(props.getProperty("itemId"), this.itemId);
        this.pvNumber = this.parseIntSafe(props.getProperty("pvNumber"), this.pvNumber);
        if (this.pvNumber < 1) {
            this.pvNumber = 1;
        }
        System.out.println("[MacroRecorder] Nap config thanh cong: itemId=" + this.itemId + " | pvNumber=" + this.pvNumber);
    }

    private void saveSettingsToDisk() {
        File file = this.resolveConfigFile();
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            Properties props = new Properties();
            props.setProperty("itemId", String.valueOf(this.itemId));
            props.setProperty("pvNumber", String.valueOf(this.pvNumber));
            try (FileOutputStream out = new FileOutputStream(file)) {
                props.store(out, "MacroRecorder config - tu dong luu, khong can chinh tay");
            }
        } catch (Exception e) {
            System.out.println("[MacroRecorder] Loi luu config: " + e);
        }
    }

    private int parseIntSafe(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (this.f8Key != null && this.f8Key.func_151468_f()) {
            if (!LicenseGuard.isLicensed) {
                if (this.mc != null) {
                    this.mc.func_147108_a(new GuiLicenseGate(null));
                }
                return;
            }
            if (this.mc != null && !this.running) {
                this.mc.func_147108_a((GuiScreen) new SettingsGui());
            }
        }

        if (this.f4Key != null && this.f4Key.func_151468_f()) {
            if (!LicenseGuard.isLicensed) {
                if (this.mc != null) {
                    this.mc.func_147108_a(new GuiLicenseGate(null));
                }
                return;
            }
            if (this.running) {
                this.stopMacro();
                this.sendF4Status(false);
            } else {
                this.startMacro();
                this.sendF4Status(true);
            }
        }
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || this.mc == null) {
            return;
        }

        // Kiem tra Ban Quyen thoi gian thuc & Single Instance
        if (!LicenseGuard.isLicensed || LicenseGuard.isAnotherInstanceRunning) {
            if (this.running) {
                this.stopMacro();
            }
            if (this.mc != null && !(this.mc.field_71462_r instanceof GuiLicenseGate)) {
                this.mc.func_147108_a(new GuiLicenseGate(null, LicenseGuard.lastStatusMsg));
            }
            return;
        }

        if (!this.running || this.mc.field_71439_g == null || this.mc.field_71441_e == null) {
            return;
        }

        if (this.commandCooldownTicks > 0) {
            --this.commandCooldownTicks;
        }

        try {
            this.process();
        } catch (Throwable t) {
            System.out.println("[MacroRecorder] Loi state machine: " + t);
            t.printStackTrace();
        }
    }

    public void startMacro() {
        if (this.mc == null || this.mc.field_71439_g == null) {
            return;
        }
        if (!LicenseGuard.checkSingleInstance() || !LicenseGuard.isLicensed) {
            this.stopMacro();
            if (this.mc != null) {
                this.mc.func_147108_a(new GuiLicenseGate(null, LicenseGuard.lastStatusMsg));
            }
            return;
        }

        this.running = true;
        this.waitTicks = 0;
        this.commandCooldownTicks = 0;
        this.transferStallTicks = 0;
        this.sellStallTicks = 0;
        this.lastInventoryCount = -1;
        this.currentPv = 1;

        if (this.countConfiguredItemsInInventory() > 0) {
            // Neu trong balo co san item config -> Ban sach inventory truoc, xong xuoi moi mo /pv 1 -> N
            this.sellPvEmptyNeedsNext = false;
            this.state = State.SELLING;
            this.debug("C\u00f3 s\u1eb5n item trong balo -> B\u00e1n s\u1ea1ch tr\u01b0\u1edbc khi m\u1edf /pv 1");
        } else {
            // Balo da sach do -> Mo /pv 1 ngay lap tuc
            this.sellPvEmptyNeedsNext = false;
            this.state = State.SELL_OPEN_PV;
            this.debug("Balo s\u1ea1ch -> M\u1edf /pv 1");
        }
    }

    public void stopMacro() {
        this.running = false;
        this.state = State.IDLE;
        this.waitTicks = 0;
        this.commandCooldownTicks = 0;
        this.transferStallTicks = 0;
        this.sellStallTicks = 0;
        this.lastInventoryCount = -1;
        this.sellPvEmptyNeedsNext = false;
        try {
            if (this.mc != null && this.mc.field_71439_g != null && this.mc.field_71462_r != null) {
                this.mc.field_71439_g.func_71053_j();
            }
        } catch (Exception ignored) {}
        System.out.println("[MacroRecorder] F4 STOP");
    }

    private void process() {
        switch (this.state) {
            case SELL_OPEN_PV:
                this.processSellOpenPv();
                break;
            case SELL_WAIT_PV:
                this.processSellWaitPv();
                break;
            case SELL_TAKE_FROM_PV:
                this.processSellTakeFromPv();
                break;
            case SELL_CLOSE_PV:
                this.processSellClosePv();
                break;
            case SELLING:
                this.processSelling();
                break;
            case SELL_RECHECK_PV:
                this.processSellRecheckPv();
                break;
            default:
                break;
        }
    }

    private void processSellOpenPv() {
        if (this.mc.field_71462_r instanceof GuiContainer && ((GuiContainer) this.mc.field_71462_r).field_147002_h instanceof ContainerChest) {
            this.state = State.SELL_WAIT_PV;
            return;
        }
        if (this.commandCooldownTicks > 0) {
            return;
        }
        this.mc.field_71439_g.func_71165_d("/pv " + this.currentPv);
        this.commandCooldownTicks = 2;
        this.waitTicks = 0;
        this.state = State.SELL_WAIT_PV;
    }

    private void processSellWaitPv() {
        if (this.mc.field_71462_r instanceof GuiContainer && ((GuiContainer) this.mc.field_71462_r).field_147002_h instanceof ContainerChest) {
            this.state = State.SELL_TAKE_FROM_PV;
            this.transferStallTicks = 0;
            return;
        }
        ++this.waitTicks;
        if (this.waitTicks >= 35) {
            this.waitTicks = 0;
            this.state = State.SELL_OPEN_PV;
        }
    }

    // Fix triet de: Lay do va ban linh hoat, khong bat buoc phai bo du hay day balo
    private void processSellTakeFromPv() {
        if (!(this.mc.field_71462_r instanceof GuiContainer) || !(((GuiContainer) this.mc.field_71462_r).field_147002_h instanceof ContainerChest)) {
            this.state = State.SELL_OPEN_PV;
            return;
        }

        ContainerChest pv = (ContainerChest) ((GuiContainer) this.mc.field_71462_r).field_147002_h;
        boolean chestHasItems = this.containerHasConfiguredItem(pv, true);

        // Truong hop 1: Ruong da het do config
        if (!chestHasItems) {
            this.closeScreen();
            this.sellPvEmptyNeedsNext = true;
            // Neu trong tui co bat ky so luong item nao (> 0) thi dem di ban ngay!
            if (this.countConfiguredItemsInInventory() > 0) {
                this.state = State.SELLING;
            } else {
                this.finishCurrentPv();
            }
            return;
        }

        // Truong hop 2: Balo da khong con o trong de chua them stack
        if (!this.hasEmptyInventorySlot()) {
            this.closeScreen();
            this.sellPvEmptyNeedsNext = false; // Ruong van con do, ban xong se mo lai
            this.state = State.SELLING;
            return;
        }

        // Lay do tu ruong sang balo
        int moved = this.takeConfiguredItemFromContainer();
        if (moved > 0) {
            this.transferStallTicks = 0;
            boolean chestStillHasItems = this.containerHasConfiguredItem(pv, true);
            boolean inventoryIsFull = !this.hasEmptyInventorySlot();

            if (!chestStillHasItems) {
                // Kho da sach do! (Lan 2 cua kho 54 o hoac kho it do) -> Dong kho, ban xong se sang ngay kho tiep theo!
                this.closeScreen();
                this.sellPvEmptyNeedsNext = true;
                this.state = State.SELLING;
                return;
            }

            if (inventoryIsFull) {
                // Balo da day kin, nhung trong kho van con do (Lan 1 cua kho 54 o) -> Dong kho, ban xong se mo lai kho nay lay not!
                this.closeScreen();
                this.sellPvEmptyNeedsNext = false;
                this.state = State.SELLING;
                return;
            }
            return;
        }

        ++this.transferStallTicks;
        if (this.transferStallTicks >= 8) {
            this.transferStallTicks = 0;
            this.closeScreen();
            if (this.countConfiguredItemsInInventory() > 0) {
                this.sellPvEmptyNeedsNext = !this.containerHasConfiguredItem(pv, true);
                this.state = State.SELLING;
            } else {
                this.finishCurrentPv();
            }
        }
    }

    private void processSellClosePv() {
        this.closeScreen();
        this.state = State.SELLING;
    }

    // Fix triet de: Ban het sach se toan bo 36 o balo (> 0) moi mo lai /pv
    private void processSelling() {
        int currentCount = this.countConfiguredItemsInInventory();

        // 1. Da ban het sach se toan bo item trong balo (con 0 item)
        if (currentCount <= 0) {
            this.sellStallTicks = 0;
            this.lastInventoryCount = -1;
            if (this.sellPvEmptyNeedsNext) {
                this.sellPvEmptyNeedsNext = false;
                this.finishCurrentPv();
            } else {
                this.state = State.SELL_OPEN_PV;
            }
            return;
        }

        // 2. Kiem tra tien trinh ban: Neu so luong dang giam thi reset timer
        if (this.lastInventoryCount == -1 || currentCount < this.lastInventoryCount) {
            this.lastInventoryCount = currentCount;
            this.sellStallTicks = 0;
        } else {
            // So luong chua giam qua cac tick (cho packet server phan hoi hoac server lag)
            ++this.sellStallTicks;
        }

        // 3. Chi khi click lien tuc 60 ticks (3 giay) ma so luong hoan toan khong the giam duoc nua
        // (Vi du: con le it hon so luong yeu cau cua bang sell, vd bang sell yeu cau 16 ma chi con 1-15)
        if (this.sellStallTicks >= 60) {
            this.sellStallTicks = 0;
            this.lastInventoryCount = -1;
            if (this.sellPvEmptyNeedsNext) {
                this.sellPvEmptyNeedsNext = false;
                this.finishCurrentPv();
            } else {
                this.state = State.SELL_OPEN_PV;
            }
            return;
        }

        // 4. Ban item bang cach click chuot phai lien tuc
        for (int i = 0; i < 36 && this.countConfiguredItemsInInventory() > 0; ++i) {
            this.rightClickSell();
        }
    }

    private void processSellRecheckPv() {
        this.state = this.countConfiguredItemsInInventory() > 0 ? State.SELLING : State.SELL_OPEN_PV;
    }

    private void finishCurrentPv() {
        this.transferStallTicks = 0;
        this.waitTicks = 0;
        this.sellStallTicks = 0;
        this.lastInventoryCount = -1;

        if (this.currentPv >= this.pvNumber) {
            this.debug(TextFormatting.GREEN + "\u0110\u00c3 B\u00c1N H\u1eb0T /pv 1 -> " + this.pvNumber + " -> HO\u00c0N T\u1ea4T & D\u1ef0NG MACRO!");
            this.stopMacro();
            this.sendF4Status(false);
            return;
        }

        this.debug("\u0110\u00e3 b\u00e1n xong /pv " + this.currentPv + " -> chuy\u1ec3n sang /pv " + (this.currentPv + 1));
        ++this.currentPv;
        this.sellPvEmptyNeedsNext = false;
        this.state = State.SELL_OPEN_PV;
    }

    private boolean isConfiguredItem(ItemStack stack) {
        if (stack == null || stack.func_190926_b()) {
            return false;
        }
        Item item = Item.func_150899_d((int) this.itemId);
        return item != null && stack.func_77973_b() == item;
    }

    private int countConfiguredItemsInInventory() {
        if (this.mc == null || this.mc.field_71439_g == null) {
            return 0;
        }
        int total = 0;
        for (ItemStack stack : this.mc.field_71439_g.field_71071_by.field_70462_a) {
            if (!this.isConfiguredItem(stack)) continue;
            total += stack.func_190916_E();
        }
        return total;
    }

    private boolean hasEmptyInventorySlot() {
        if (this.mc == null || this.mc.field_71439_g == null) {
            return false;
        }
        for (int i = 0; i < 36; ++i) {
            ItemStack stack = (ItemStack) this.mc.field_71439_g.field_71071_by.field_70462_a.get(i);
            if (stack == null || stack.func_190926_b()) {
                return true;
            }
        }
        return false;
    }

    private boolean containerHasConfiguredItem(ContainerChest container, boolean lowerOnly) {
        int end = lowerOnly ? container.func_85151_d().func_70302_i_() : container.field_75151_b.size();
        for (int i = 0; i < end; ++i) {
            Slot slot = (Slot) container.field_75151_b.get(i);
            if (slot == null || !slot.func_75216_d() || !this.isConfiguredItem(slot.func_75211_c())) continue;
            return true;
        }
        return false;
    }

    private int takeConfiguredItemFromContainer() {
        if (!(this.mc.field_71462_r instanceof GuiContainer)) {
            return 0;
        }
        try {
            GuiContainer gui = (GuiContainer) this.mc.field_71462_r;
            if (!(gui.field_147002_h instanceof ContainerChest)) {
                return 0;
            }
            ContainerChest container = (ContainerChest) gui.field_147002_h;
            int totalTaken = 0;
            int chestSlotCount = container.func_85151_d().func_70302_i_();
            for (int i = chestSlotCount - 1; i >= 0; --i) {
                Slot slot = (Slot) container.field_75151_b.get(i);
                if (slot == null || !slot.func_75216_d() || !this.isConfiguredItem(slot.func_75211_c())) continue;
                this.mc.field_71442_b.func_187098_a(container.field_75152_c, i, 0, ClickType.QUICK_MOVE, (EntityPlayer) this.mc.field_71439_g);
                ++totalTaken;
            }
            return totalTaken;
        } catch (Exception e) {
            System.out.println("[MacroRecorder] Loi khi lay do trong ruong: " + e);
            return 0;
        }
    }

    private void rightClickSell() {
        try {
            this.mc.field_71442_b.func_187101_a((EntityPlayer) this.mc.field_71439_g, (World) this.mc.field_71441_e, EnumHand.MAIN_HAND);
        } catch (Exception e) {
            System.out.println("[MacroRecorder] Loi ban item: " + e);
        }
    }

    private void closeScreen() {
        try {
            if (this.mc.field_71462_r != null && this.mc.field_71439_g != null) {
                this.mc.field_71439_g.func_71053_j();
            }
        } catch (Exception ignored) {}
    }

    private void sendF4Status(boolean on) {
        if (this.mc == null || this.mc.field_71439_g == null) {
            return;
        }
        try {
            this.mc.field_71439_g.func_145747_a((ITextComponent) new TextComponentString(TextFormatting.GRAY + "[F4] " + (on ? TextFormatting.GREEN + "ON" : TextFormatting.RED + "OFF")));
        } catch (Exception ignored) {}
    }

    private void debug(String message) {
        if (this.mc == null || this.mc.field_71439_g == null) {
            return;
        }
        try {
            this.mc.field_71439_g.func_145747_a((ITextComponent) new TextComponentString(TextFormatting.GRAY + "[F4] " + TextFormatting.YELLOW + message));
        } catch (Exception ignored) {}
    }

    // =========================================================================
    // GUI CAI DAT F8 (FIXED: Settings dc moi so kho 1..9999, go Enter de luu)
    // =========================================================================
    @SideOnly(Side.CLIENT)
    public class SettingsGui extends GuiScreen {
        private GuiTextField itemField;
        private GuiTextField pvField;
        private String error = "";

        public SettingsGui() {}

        private int baseX() {
            return this.field_146294_l / 2 - 100;
        }

        private int baseY() {
            return this.field_146295_m / 2 - 50;
        }

        @Override
        public void func_73866_w_() {
            Keyboard.enableRepeatEvents(true);
            int x = this.baseX();
            int y = this.baseY();
            int w = 200;

            this.field_146292_n.clear();
            this.itemField = new GuiTextField(0, this.field_146289_q, x, y, w, 20);
            this.pvField = new GuiTextField(1, this.field_146289_q, x, y + 28, w, 20);

            this.itemField.func_146203_f(10);
            this.pvField.func_146203_f(10);

            this.itemField.func_146180_a(String.valueOf(MacroRecorderMod.this.itemId));
            this.pvField.func_146180_a(String.valueOf(MacroRecorderMod.this.pvNumber));

            this.field_146292_n.add(new GuiButton(13, x, y + 64, 98, 20, "L\u01b0u"));
            this.field_146292_n.add(new GuiButton(14, x + 102, y + 64, 98, 20, "\u0110\u00f3ng"));
        }

        @Override
        public void func_146281_b() {
            Keyboard.enableRepeatEvents(false);
        }

        @Override
        public void func_73876_c() {
            this.itemField.func_146178_a();
            this.pvField.func_146178_a();
        }

        @Override
        protected void func_146284_a(GuiButton button) {
            if (button.field_146127_k == 13) {
                this.saveSettings();
            } else if (button.field_146127_k == 14) {
                this.field_146297_k.func_147108_a(null);
            }
        }

        private void saveSettings() {
            try {
                String itemText = this.itemField.func_146179_b().trim();
                String pvText = this.pvField.func_146179_b().trim();

                int newItem = itemText.isEmpty() ? MacroRecorderMod.this.itemId : Integer.parseInt(itemText);
                int newPv = pvText.isEmpty() ? MacroRecorderMod.this.pvNumber : Integer.parseInt(pvText);

                if (newItem < 0 || newPv < 1) {
                    throw new NumberFormatException();
                }

                MacroRecorderMod.this.itemId = newItem;
                MacroRecorderMod.this.pvNumber = newPv;
                this.error = "";
                MacroRecorderMod.this.saveSettingsToDisk();

                if (MacroRecorderMod.this.mc != null && MacroRecorderMod.this.mc.field_71439_g != null) {
                    MacroRecorderMod.this.mc.field_71439_g.func_145747_a((ITextComponent) new TextComponentString(
                            TextFormatting.GREEN + "[F8] \u0110\u00e3 l\u01b0u c\u00e0i \u0111\u1eb7t: ID Item = " + newItem + " | S\u1ed1 kho /pv = " + newPv));
                }

                this.field_146297_k.func_147108_a(null);
            } catch (NumberFormatException e) {
                this.error = "ID Item v\u00e0 S\u1ed1 kho /pv ph\u1ea3i l\u00e0 s\u1ed1 h\u1ee3p l\u1ec7 (kho >= 1).";
            } catch (Exception e) {
                this.error = "Kh\u00f4ng l\u01b0u \u0111\u01b0\u1ee3c: " + e.getClass().getSimpleName();
            }
        }

        @Override
        protected void func_73869_a(char typedChar, int keyCode) {
            if (keyCode == 1) { // ESC
                this.field_146297_k.func_147108_a(null);
                return;
            }
            if (keyCode == 28 || keyCode == 156) { // Enter / Numpad Enter
                this.saveSettings();
                return;
            }
            this.itemField.func_146201_a(typedChar, keyCode);
            this.pvField.func_146201_a(typedChar, keyCode);
            try {
                super.func_73869_a(typedChar, keyCode);
            } catch (Throwable ignored) {}
        }

        @Override
        protected void func_73864_a(int mouseX, int mouseY, int mouseButton) {
            try {
                super.func_73864_a(mouseX, mouseY, mouseButton);
            } catch (Throwable ignored) {}
            this.itemField.func_146192_a(mouseX, mouseY, mouseButton);
            this.pvField.func_146192_a(mouseX, mouseY, mouseButton);
        }

        @Override
        public void func_73863_a(int mouseX, int mouseY, float partialTicks) {
            this.func_146276_q_();
            int x = this.baseX();
            int y = this.baseY();

            this.func_73732_a(this.field_146289_q, TextFormatting.GOLD + "" + TextFormatting.BOLD + "C\u00c0I \u0110\u1eb6T MACRO SELL KHO", this.field_146294_l / 2, y - 24, 0xFFD700);
            this.func_73731_b(this.field_146289_q, "ID Item (F3+H)", x - 90, y + 5, 0xFFFFFF);
            this.func_73731_b(this.field_146289_q, "S\u1ed1 kho /pv", x - 90, y + 33, 0xFFFFFF);

            this.itemField.func_146194_f();
            this.pvField.func_146194_f();
            super.func_73863_a(mouseX, mouseY, partialTicks);

            this.func_73732_a(this.field_146289_q, TextFormatting.GRAY + "F4: R\u00fat v\u00e0 b\u00e1n h\u1ebft /pv 1 -> N r\u1ed3i t\u1ef1 \u0111\u1ed9ng d\u1eebng", this.field_146294_l / 2, y + 92, 0xAAAAAA);
            if (!this.error.isEmpty()) {
                this.func_73732_a(this.field_146289_q, this.error, this.field_146294_l / 2, y + 106, 0xFF5555);
            }
        }
    }

    // =========================================================================
    // MODAL BAN QUYEN BAT BUOC TRUOC KHI VAO GAME (1 KEY 1 MAY 1 NICK)
    // =========================================================================
    @SideOnly(Side.CLIENT)
    public static class GuiLicenseGate extends GuiScreen {
        private final GuiScreen parentScreen;
        private GuiTextField keyInput;
        private GuiButton copyHwidBtn;
        private GuiButton activateBtn;
        private GuiButton exitBtn;
        private String statusMsg = "";
        private int statusColor = 0xFF5555;
        private int successTimer = -1;

        public GuiLicenseGate(GuiScreen parent) {
            this(parent, null);
        }

        public GuiLicenseGate(GuiScreen parent, String reason) {
            this.parentScreen = parent;
            if (reason != null && !reason.isEmpty()) {
                this.statusMsg = reason;
                this.statusColor = 0xFF5555;
            }
        }

        @Override
        public void func_73866_w_() {
            Keyboard.enableRepeatEvents(true);
            this.field_146292_n.clear();
            int cx = this.field_146294_l / 2;
            int cy = this.field_146295_m / 2;

            if (LicenseGuard.isAnotherInstanceRunning) {
                // CHE DO: CUA SO THU 2 - THONG BAO KHONG THE MO THEM CUA SO
                this.keyInput = null;
                int btnW = 200;
                int btnX = cx - btnW / 2;
                this.field_146292_n.add(new GuiButton(4, btnX, cy + 12, btnW, 20, "Th\u1eed l\u1ea1i"));
                this.field_146292_n.add(new GuiButton(3, btnX, cy + 38, btnW, 20, "Tho\u00e1t Game"));
                return;
            }

            int boxW = 280;
            int boxX = cx - boxW / 2;

            this.keyInput = new GuiTextField(101, this.field_146289_q, boxX, cy - 10, boxW, 20);
            this.keyInput.func_146203_f(128);
            this.keyInput.func_146195_b(true);
            if (LicenseGuard.activeKey != null) {
                this.keyInput.func_146180_a(LicenseGuard.activeKey);
            }

            this.copyHwidBtn = new GuiButton(1, boxX, cy - 44, boxW, 20, "Sao ch\u00e9p HWID m\u00e1y");
            this.activateBtn = new GuiButton(2, boxX, cy + 18, boxW, 20, "K\u00cdCH HO\u1ea0T V\u00c0 V\u00c0O GAME");
            this.exitBtn = new GuiButton(3, boxX, cy + 44, boxW, 20, "Tho\u00e1t Game");

            this.field_146292_n.add(this.copyHwidBtn);
            this.field_146292_n.add(this.activateBtn);
            this.field_146292_n.add(this.exitBtn);
        }

        @Override
        public void func_146281_b() {
            Keyboard.enableRepeatEvents(false);
        }

        @Override
        public void func_73876_c() {
            if (this.keyInput != null) {
                this.keyInput.func_146178_a();
            }
            if (this.successTimer > 0) {
                this.successTimer--;
                if (this.successTimer == 0) {
                    Minecraft mc = Minecraft.func_71410_x();
                    if (this.parentScreen != null && !(this.parentScreen instanceof GuiLicenseGate)) {
                        mc.func_147108_a(this.parentScreen);
                    } else {
                        mc.func_147108_a(new GuiMainMenu());
                    }
                }
            }
        }

        @Override
        protected void func_146284_a(GuiButton button) {
            if (button.field_146127_k == 1) {
                GuiScreen.func_146275_d(LicenseGuard.getHWID());
                this.statusMsg = "\u0110\u00e3 sao ch\u00e9p HWID: " + LicenseGuard.getHWID();
                this.statusColor = 0x55FF55;
            } else if (button.field_146127_k == 2) {
                this.triggerActivate();
            } else if (button.field_146127_k == 3) {
                Minecraft.func_71410_x().func_71400_g();
            } else if (button.field_146127_k == 4) {
                if (LicenseGuard.checkSingleInstance()) {
                    LicenseGuard.isAnotherInstanceRunning = false;
                    if (LicenseGuard.validateCachedLicense()) {
                        LicenseGuard.startHeartbeat();
                        Minecraft.func_71410_x().func_147108_a(new GuiMainMenu());
                    } else {
                        this.func_73866_w_();
                    }
                } else {
                    this.statusMsg = "Kh\u00f4ng th\u1ec3 m\u1edf th\u00eam c\u1eeda s\u1ed5 n\u1eefa!";
                    this.statusColor = 0xFF5555;
                }
            }
        }

        private void triggerActivate() {
            if (!LicenseGuard.checkSingleInstance()) {
                this.statusMsg = "Key kh\u00f4ng h\u1ee3p l\u1ec7 ho\u1eb7c \u0111\u00e3 b\u1ecb kh\u00f3a!";
                this.statusColor = 0xFF5555;
                return;
            }

            final String inputKey = this.keyInput.func_146179_b().trim();
            if (inputKey.isEmpty()) {
                this.statusMsg = "Vui l\u00f2ng nh\u1eadp License Key!";
                this.statusColor = 0xFF5555;
                return;
            }
            if (inputKey.equalsIgnoreCase(LicenseGuard.getHWID())) {
                this.statusMsg = "B\u1ea1n \u0111ang d\u00e1n nh\u1ea7m m\u00e3 HWID! Vui l\u00f2ng nh\u1eadp Key.";
                this.statusColor = 0xFFAA00;
                return;
            }

            this.statusMsg = "\u0110ang ki\u1ec3m tra Key v\u1edbi m\u00e1y ch\u1ee7...";
            this.statusColor = 0xFFFF55;
            this.activateBtn.field_146124_l = false;

            LicenseGuard.verifyOnline(inputKey, new Runnable() {
                @Override
                public void run() {
                    GuiLicenseGate.this.activateBtn.field_146124_l = true;
                    if (LicenseGuard.isLicensed) {
                        GuiLicenseGate.this.statusMsg = "K\u00cdCH HO\u1ea0T TH\u00c0NH C\u00d4NG!";
                        GuiLicenseGate.this.statusColor = 0x55FF55;
                        GuiLicenseGate.this.successTimer = 18;
                    } else {
                        GuiLicenseGate.this.statusMsg = LicenseGuard.lastStatusMsg;
                        GuiLicenseGate.this.statusColor = 0xFF5555;
                    }
                }
            });
        }

        @Override
        protected void func_73869_a(char typedChar, int keyCode) {
            if (keyCode == 1) {
                return; // Chan phim ESC khong cho tat modal neu chua kich hoat
            }
            if (keyCode == 28 || keyCode == 156) {
                this.triggerActivate();
                return;
            }
            if (this.keyInput != null) {
                this.keyInput.func_146201_a(typedChar, keyCode);
            }
            try {
                super.func_73869_a(typedChar, keyCode);
            } catch (Throwable ignored) {}
        }

        @Override
        protected void func_73864_a(int mouseX, int mouseY, int mouseButton) {
            try {
                super.func_73864_a(mouseX, mouseY, mouseButton);
            } catch (Throwable ignored) {}
            if (this.keyInput != null) {
                this.keyInput.func_146192_a(mouseX, mouseY, mouseButton);
            }
        }

        @Override
        public void func_73863_a(int mouseX, int mouseY, float partialTicks) {
            this.func_146276_q_();

            int cx = this.field_146294_l / 2;
            int cy = this.field_146295_m / 2;

            if (LicenseGuard.isAnotherInstanceRunning) {
                int boxW = 310;
                int boxH = 145;
                int x1 = cx - boxW / 2;
                int y1 = cy - 72;
                int x2 = cx + boxW / 2;
                int y2 = cy + 73;

                // Frame, Shadow & Background
                GuiScreen.func_73734_a(x1 - 2, y1 - 2, x2 + 2, y2 + 2, 0x88000000);
                GuiScreen.func_73734_a(x1 - 1, y1 - 1, x2 + 1, y2 + 1, 0xFF334155);
                GuiScreen.func_73734_a(x1, y1, x2, y2, 0xF40F172A);
                GuiScreen.func_73734_a(x1, y1, x2, y1 + 3, 0xFFEF4444);
                GuiScreen.func_73734_a(x1 + 16, y1 + 25, x2 - 16, y1 + 26, 0x3394A3B8);

                this.func_73732_a(this.field_146289_q, TextFormatting.RED + "" + TextFormatting.BOLD + "TH\u00d4NG B\u00c1O", cx, y1 + 11, 0xFF5555);
                this.func_73732_a(this.field_146289_q, TextFormatting.GOLD + "" + TextFormatting.BOLD + "Kh\u00f4ng th\u1ec3 m\u1edf th\u00eam c\u1eeda s\u1ed5 n\u1eefa!", cx, y1 + 35, 0xFFAA00);
                this.func_73732_a(this.field_146289_q, TextFormatting.GRAY + "Vui l\u00f2ng t\u1eaft b\u1edbt c\u1eeda s\u1ed5 Minecraft \u0111ang ch\u1ea1y.", cx, y1 + 49, 0xAAAAAA);

                super.func_73863_a(mouseX, mouseY, partialTicks);
                return;
            }

            int boxW = 320;
            int boxH = 180;
            int x1 = cx - boxW / 2;
            int y1 = cy - 90;
            int x2 = cx + boxW / 2;
            int y2 = cy + 90;

            // Frame, Shadow & Background
            GuiScreen.func_73734_a(x1 - 2, y1 - 2, x2 + 2, y2 + 2, 0x88000000);
            GuiScreen.func_73734_a(x1 - 1, y1 - 1, x2 + 1, y2 + 1, 0xFF334155);
            GuiScreen.func_73734_a(x1, y1, x2, y2, 0xF40F172A);
            GuiScreen.func_73734_a(x1, y1, x2, y1 + 3, 0xFF38BDF8);
            GuiScreen.func_73734_a(x1 + 16, y1 + 25, x2 - 16, y1 + 26, 0x3394A3B8);

            this.func_73732_a(this.field_146289_q, TextFormatting.AQUA + "" + TextFormatting.BOLD + "X\u00c1C TH\u1ef0C B\u1ea2N QUY\u1ec0N", cx, y1 + 11, 0x38BDF8);

            String myHwid = LicenseGuard.getHWID();
            this.func_73732_a(this.field_146289_q, TextFormatting.GRAY + "M\u00e3 m\u00e1y: " + TextFormatting.YELLOW + myHwid, cx, y1 + 31, 0xAAAAAA);

            if (this.keyInput != null) {
                this.keyInput.func_146194_f();
            }
            super.func_73863_a(mouseX, mouseY, partialTicks);

            if (this.statusMsg != null && !this.statusMsg.isEmpty()) {
                this.func_73732_a(this.field_146289_q, this.statusMsg, cx, cy + 70, this.statusColor);
            }
        }
    }

    private static enum State {
        IDLE,
        SELL_OPEN_PV,
        SELL_WAIT_PV,
        SELL_TAKE_FROM_PV,
        SELL_CLOSE_PV,
        SELLING,
        SELL_RECHECK_PV
    }
}
