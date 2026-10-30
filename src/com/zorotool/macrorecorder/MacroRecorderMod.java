/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.block.Block
 *  net.minecraft.block.BlockChest
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.GuiButton
 *  net.minecraft.client.gui.GuiScreen
 *  net.minecraft.client.gui.GuiTextField
 *  net.minecraft.client.gui.inventory.GuiContainer
 *  net.minecraft.client.settings.KeyBinding
 *  net.minecraft.entity.player.EntityPlayer
 *  net.minecraft.inventory.ClickType
 *  net.minecraft.inventory.ContainerChest
 *  net.minecraft.inventory.Slot
 *  net.minecraft.item.Item
 *  net.minecraft.item.ItemStack
 *  net.minecraft.util.EnumHand
 *  net.minecraft.util.math.BlockPos
 *  net.minecraft.util.math.RayTraceResult$Type
 *  net.minecraft.util.text.ITextComponent
 *  net.minecraft.util.text.TextComponentString
 *  net.minecraft.util.text.TextFormatting
 *  net.minecraft.world.World
 *  net.minecraftforge.client.event.RenderGameOverlayEvent$Post
 *  net.minecraftforge.common.MinecraftForge
 *  net.minecraftforge.fml.client.registry.ClientRegistry
 *  net.minecraftforge.fml.common.Mod
 *  net.minecraftforge.fml.common.Mod$EventHandler
 *  net.minecraftforge.fml.common.event.FMLInitializationEvent
 *  net.minecraftforge.fml.common.eventhandler.SubscribeEvent
 *  net.minecraftforge.fml.common.gameevent.InputEvent$KeyInputEvent
 *  net.minecraftforge.fml.common.gameevent.TickEvent$ClientTickEvent
 *  net.minecraftforge.fml.common.gameevent.TickEvent$Phase
 *  net.minecraftforge.fml.common.gameevent.TickEvent$RenderTickEvent
 *  net.minecraftforge.fml.relauncher.Side
 *  net.minecraftforge.fml.relauncher.SideOnly
 *  org.lwjgl.input.Mouse
 */
package com.zorotool.macrorecorder;

import java.io.IOException;
import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Mouse;
import org.lwjgl.input.Keyboard;
import net.minecraftforge.client.event.GuiOpenEvent;

@Mod(modid="macrorecorder", version="1.0", acceptedMinecraftVersions="[1.12.2]")
public class MacroRecorderMod {
    public static final String MODID = "macrorecorder";
    private static MacroRecorderMod INSTANCE;
    public static MacroRecorderMod getInstance() {
        return INSTANCE;
    }
    public static final String VERSION = "1.0";
    private int itemId = 338;
    private int pvNumber = 1;
    private double gotoDelaySeconds = 0.0;
    private double homeBackDelaySeconds = 2.0;
    private String setHomeCommand = "/sethome";
    private String backCommand = "/back";
    private String gotoCommand = "#goto ~ ~ ~-1";
    private int yCheck = 0;
    private boolean yCheckConfigured = false;
    private boolean lockPitchYawWhenYCorrect = true;
    private boolean cameraLockActive = false;
    private float lockedYaw = 0.0f;
    private float lockedPitch = 0.0f;
    private boolean debugChat = true;
    private boolean fillPvBeforeGoto = true;
    private static final int INVENTORY_FULL_SLOTS = 36;
    private static final int SELL_DONE_ITEMS = 16;
    private static final int WATCHDOG_TICKS = 140;
    private static final int TICKS_PER_SECOND = 20;
    private Minecraft mc;
    private KeyBinding f4Key;
    private KeyBinding f8Key;
    private boolean running = false;
    private Mode mode = Mode.NONE;
    private State state = State.IDLE;
    private int waitTicks = 0;
    private int watchdogTicks = 0;
    private int gotoWaitTicks = 0;
    private int gotoDelayTicks = 0;
    private int homeBackDelayTicks = 0;
    private boolean teleportCommandSent = false;
    private BlockPos teleportStartPos = null;
    private boolean homeFallbackConsumed = false;
    private boolean sellPvEmptyNeedsHome = false;
    private int commandCooldownTicks = 0;
    private int transferStallTicks = 0;
    private boolean inCombat = false;
    private int combatTicksRemaining = 0;
    private static final int COMBAT_TIMEOUT_TICKS = 320;
    private boolean stopMacroOnCombat = false;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        INSTANCE = this;
        this.mc = Minecraft.func_71410_x();
        MinecraftForge.EVENT_BUS.register((Object)this);
        LicenseGuard.init();
        this.f4Key = new KeyBinding("F4 Start / Dung", 62, "Macro Recorder");
        this.f8Key = new KeyBinding("F8 Cai dat", 66, "Macro Recorder");
        ClientRegistry.registerKeyBinding((KeyBinding)this.f4Key);
        ClientRegistry.registerKeyBinding((KeyBinding)this.f8Key);
        System.out.println("[MacroRecorder] F4 Start/Dung + F8 Cai dat");
    }

    @SubscribeEvent
    @SideOnly(value=Side.CLIENT)
    public void onGuiOpen(GuiOpenEvent event) {
        if (!LicenseGuard.isLicensed) {
            if (event != null && event.getGui() != null && !(event.getGui() instanceof GuiLicenseGate)) {
                event.setGui(new GuiLicenseGate(event.getGui()));
            }
        }
    }


    @SubscribeEvent
    @SideOnly(value=Side.CLIENT)
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (this.f8Key != null && this.f8Key.func_151468_f() && this.mc != null && !this.running) {
            this.mc.func_147108_a((GuiScreen)new SettingsGui());
        }
        if (this.f4Key != null && this.f4Key.func_151468_f()) {
            if (!LicenseGuard.isLicensed) {
                if (this.mc != null && this.mc.field_71439_g != null) {
                    this.mc.field_71439_g.func_145747_a((ITextComponent)new TextComponentString(
                        TextFormatting.RED + "[Bản Quyền] CHƯA KÍCH HOẠT! Bấm F8 hoặc gõ /key <mã-key> để kích hoạt."
                    ));
                }
                return;
            }
            if (this.running) {
                this.stopMacro();
                this.sendF4Status(false);
            } else {
                this.startFromCurrentY();
                this.sendF4Status(true);
            }
        }
    }

    @SubscribeEvent
    @SideOnly(value=Side.CLIENT)
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || this.mc == null || this.mc.field_71439_g == null || this.mc.field_71441_e == null) {
            return;
        }

        // Kiem tra nguoi choi chet -> dung macro ngay de tranh loi va tranh ban
        if (this.mc.field_71439_g.field_70128_L || this.mc.field_71439_g.func_110143_aJ() <= 0.0f) {
            if (this.inCombat) {
                this.exitCombat("Nguoi choi da chet");
            }
            if (this.running) {
                this.closeScreen();
                this.cameraLockActive = false;
                this.stopMacro();
                this.sendCombatAlert("Nguoi choi da chet -> DA DUNG MACRO!");
                return;
            }
        }

        // Layer 2: Phat hien sat thuong / bi nguoi khac danh truc tiep qua hurtTime
        if (this.mc.field_71439_g.field_70737_aN == 9 || this.mc.field_71439_g.field_70737_aN == 10) {
            if (this.running && this.mode == Mode.SELL) {
                if (!this.inCombat) {
                    this.enterCombat("Bi sat thuong / bi nguoi khac danh!");
                } else {
                    this.combatTicksRemaining = COMBAT_TIMEOUT_TICKS;
                }
            }
        }

        // Dem nguoc thoi gian cho chien dau neu khong nhan duoc tin nhan ket thuc
        if (this.inCombat) {
            if (this.combatTicksRemaining > 0) {
                --this.combatTicksRemaining;
                if (this.combatTicksRemaining == 0) {
                    this.exitCombat("Het thoi gian cho (15s)");
                }
            }
            this.closeScreen();
            this.cameraLockActive = false;
            // DANG TRONG CHIEN DAU: NGUNG HOAN TOAN MOI THAO TAC SELL / LENH!
            return;
        }

        if (!LicenseGuard.isLicensed) {
            if (this.running) {
                this.stopMacro();
            }
            if (this.mc != null) {
                if (this.mc.field_71462_r == null) {
                    this.mc.func_147108_a(new GuiLicenseGate(null));
                    return;
                } else if (!(this.mc.field_71462_r instanceof GuiLicenseGate)) {
                    this.mc.func_147108_a(new GuiLicenseGate(this.mc.field_71462_r));
                    return;
                }
            }
            return;
        }

        this.applyPitchYawLockIfNeeded();
        if (!this.running) {
            return;
        }
        if (this.commandCooldownTicks > 0) {
            --this.commandCooldownTicks;
        }
        this.guardYMode();
        this.applyPitchYawLockIfNeeded();
        try {
            this.process();
        }
        catch (Throwable t) {
            System.out.println("[MacroRecorder] Loi state machine: " + t);
            t.printStackTrace();
        }
    }

    private void startFromCurrentY() {
        int currentY;
        if (this.mc == null || this.mc.field_71439_g == null) {
            return;
        }
        this.running = true;
        this.waitTicks = 0;
        this.watchdogTicks = 0;
        this.gotoWaitTicks = 0;
        this.gotoDelayTicks = 0;
        this.homeBackDelayTicks = 0;
        this.teleportCommandSent = false;
        this.teleportStartPos = null;
        this.homeFallbackConsumed = false;
        this.sellPvEmptyNeedsHome = false;
        this.commandCooldownTicks = 0;
        this.transferStallTicks = 0;
        this.inCombat = false;
        this.combatTicksRemaining = 0;
        if (!this.yCheckConfigured) {
            this.yCheck = this.mc.field_71439_g.func_180425_c().func_177956_o();
            this.yCheckConfigured = true;
        }
        if ((currentY = this.mc.field_71439_g.func_180425_c().func_177956_o()) == this.yCheck) {
            this.mode = Mode.TAKE;
            this.state = State.TAKE_CHECK_INVENTORY;
            this.cameraLockActive = this.lockPitchYawWhenYCorrect;
            if (this.cameraLockActive) {
                this.forceLockedRotation();
                this.consumeMouseDelta();
            }
            this.debug("F4 START | Y=" + currentY + " == Y check " + this.yCheck + " | NHAN ITEM | LOCK YAW=" + this.formatAngleForDebug(this.lockedYaw) + " PITCH=" + this.formatAngleForDebug(this.lockedPitch));
        } else {
            this.mode = Mode.SELL;
            this.state = State.SELL_OPEN_PV;
            this.cameraLockActive = false;
            this.debug("F4 START | Y=" + currentY + " != Y check " + this.yCheck + " | BAN ITEM");
        }
    }

    public void stopMacro() {
        this.cameraLockActive = false;
        this.running = false;
        this.mode = Mode.NONE;
        this.state = State.IDLE;
        this.waitTicks = 0;
        this.watchdogTicks = 0;
        this.gotoWaitTicks = 0;
        this.gotoDelayTicks = 0;
        this.homeBackDelayTicks = 0;
        this.teleportCommandSent = false;
        this.teleportStartPos = null;
        this.homeFallbackConsumed = false;
        this.commandCooldownTicks = 0;
        this.transferStallTicks = 0;
        this.inCombat = false;
        this.combatTicksRemaining = 0;
        try {
            if (this.mc != null && this.mc.field_71439_g != null && this.mc.field_71462_r != null) {
                this.mc.field_71439_g.func_71053_j();
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        System.out.println("[MacroRecorder] F4 STOP");
    }

    private void applyPitchYawLockIfNeeded() {
        if (this.mc == null || this.mc.field_71439_g == null) {
            return;
        }
        if (this.inCombat || !this.lockPitchYawWhenYCorrect || this.mc.field_71439_g.func_180425_c().func_177956_o() != this.yCheck) {
            this.cameraLockActive = false;
            return;
        }
        this.cameraLockActive = true;
        this.consumeMouseDelta();
        this.forceLockedRotation();
    }

    private void consumeMouseDelta() {
        try {
            Mouse.getDX();
            Mouse.getDY();
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        try {
            if (this.mc != null && this.mc.field_71417_B != null) {
                this.mc.field_71417_B.field_74377_a = 0;
                this.mc.field_71417_B.field_74375_b = 0;
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    private void forceLockedRotation() {
        if (this.mc == null || this.mc.field_71439_g == null) {
            return;
        }
        float yaw = this.normalizeYaw(this.lockedYaw);
        float pitch = Math.max(-90.0f, Math.min(90.0f, this.lockedPitch));
        this.mc.field_71439_g.field_70177_z = yaw;
        this.mc.field_71439_g.field_70125_A = pitch;
        this.mc.field_71439_g.field_70126_B = yaw;
        this.mc.field_71439_g.field_70127_C = pitch;
        this.mc.field_71439_g.field_70759_as = yaw;
        this.mc.field_71439_g.field_70758_at = yaw;
        this.mc.field_71439_g.field_70761_aq = yaw;
    }

    @SideOnly(value=Side.CLIENT)
    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        if (this.mc == null || this.mc.field_71439_g == null) {
            return;
        }
        if (this.inCombat || !this.lockPitchYawWhenYCorrect || this.mc.field_71439_g.func_180425_c().func_177956_o() != this.yCheck) {
            this.cameraLockActive = false;
            return;
        }
        this.cameraLockActive = true;
        this.consumeMouseDelta();
        this.forceLockedRotation();
    }

    private void guardYMode() {
        if (!this.running || this.mc == null || this.mc.field_71439_g == null || this.inCombat) {
            return;
        }
        int currentY = this.mc.field_71439_g.func_180425_c().func_177956_o();
        if (currentY != this.yCheck && this.mode == Mode.TAKE) {
            this.cameraLockActive = false;
            this.closeScreen();
            this.mode = Mode.SELL;
            this.state = State.SELL_OPEN_PV;
            this.sellPvEmptyNeedsHome = false;
            this.waitTicks = 0;
            this.transferStallTicks = 0;
            this.debug("Y doi sang " + currentY + " != Y check " + this.yCheck + " -> FLOW BAN");
            return;
        }
        if (currentY == this.yCheck && this.mode == Mode.SELL) {
            this.closeScreen();
            this.mode = Mode.TAKE;
            this.cameraLockActive = this.lockPitchYawWhenYCorrect;
            this.state = State.TAKE_CHECK_INVENTORY;
            this.sellPvEmptyNeedsHome = false;
            this.waitTicks = 0;
            this.transferStallTicks = 0;
            this.debug("Y doi ve " + currentY + " == Y check " + this.yCheck + " -> FLOW NHAN");
        }
    }

    private void process() {
        if (this.inCombat) {
            this.closeScreen();
            return;
        }
        int currentY = this.mc.field_71439_g.func_180425_c().func_177956_o();
        if (currentY == this.yCheck && this.mode == Mode.SELL) {
            this.mode = Mode.TAKE;
            this.waitTicks = 0;
            this.watchdogTicks = 0;
            this.transferStallTicks = 0;
            this.state = State.TAKE_CHECK_INVENTORY;
        } else if (currentY != this.yCheck && this.mode == Mode.TAKE && this.state != State.TAKE_TELEPORT && this.state != State.TAKE_WAIT_TELEPORT) {
            this.mode = Mode.SELL;
            this.waitTicks = 0;
            this.watchdogTicks = 0;
            this.transferStallTicks = 0;
            this.state = State.SELL_OPEN_PV;
        }
        switch (this.state) {
            case TAKE_CHECK_INVENTORY: {
                this.processTakeCheckInventory();
                break;
            }
            case TAKE_OPEN_CHEST: {
                this.processTakeOpenChest();
                break;
            }
            case TAKE_WAIT_CHEST: {
                this.processTakeWaitChest();
                break;
            }
            case TAKE_READ_CHEST: {
                this.processTakeReadChest();
                break;
            }
            case TAKE_OPEN_PV: {
                this.processTakeOpenPv();
                break;
            }
            case TAKE_WAIT_PV: {
                this.processTakeWaitPv();
                break;
            }
            case TAKE_PUT_IN_PV: {
                this.processTakePutInPv();
                break;
            }
            case TAKE_GOTO_NEXT_CHEST: {
                this.processTakeGotoNextChest();
                break;
            }
            case TAKE_WAIT_GOTO: {
                this.processTakeWaitGoto();
                break;
            }
            case TAKE_TELEPORT: {
                this.processTakeTeleport();
                break;
            }
            case TAKE_WAIT_TELEPORT: {
                this.processTakeWaitTeleport();
                break;
            }
            case SELL_OPEN_PV: {
                this.processSellOpenPv();
                break;
            }
            case SELL_WAIT_PV: {
                this.processSellWaitPv();
                break;
            }
            case SELL_TAKE_FROM_PV: {
                this.processSellTakeFromPv();
                break;
            }
            case SELL_CLOSE_PV: {
                this.processSellClosePv();
                break;
            }
            case SELLING: {
                this.processSelling();
                break;
            }
            case SELL_RECHECK_PV: {
                this.processSellRecheckPv();
                break;
            }
            case SELL_TELEPORT: {
                this.processSellTeleport();
                break;
            }
            case SELL_WAIT_TELEPORT: {
                this.processSellWaitTeleport();
                break;
            }
        }
    }

    private void processTakeCheckInventory() {
        this.state = this.isInventoryFull() ? State.TAKE_OPEN_PV : State.TAKE_OPEN_CHEST;
    }

    private void processTakeOpenChest() {
        if (this.mc.field_71462_r instanceof GuiContainer && ((GuiContainer)this.mc.field_71462_r).field_147002_h instanceof ContainerChest) {
            this.state = State.TAKE_READ_CHEST;
            return;
        }
        if (this.isLookingAtChest()) {
            this.openChest();
            this.waitTicks = 0;
            this.state = State.TAKE_WAIT_CHEST;
        } else {
            this.state = State.TAKE_GOTO_NEXT_CHEST;
        }
    }

    private void processTakeWaitChest() {
        if (this.mc.field_71462_r instanceof GuiContainer && ((GuiContainer)this.mc.field_71462_r).field_147002_h instanceof ContainerChest) {
            this.state = State.TAKE_READ_CHEST;
            return;
        }
        ++this.waitTicks;
        if (this.waitTicks >= 20) {
            this.waitTicks = 0;
            if (this.isLookingAtChest()) {
                this.openChest();
            }
        }
    }

    private void processTakeReadChest() {
        if (!(this.mc.field_71462_r instanceof GuiContainer) || !(((GuiContainer)this.mc.field_71462_r).field_147002_h instanceof ContainerChest)) {
            this.state = State.TAKE_OPEN_CHEST;
            return;
        }
        ContainerChest chest = (ContainerChest)((GuiContainer)this.mc.field_71462_r).field_147002_h;
        if (this.isInventoryFull()) {
            this.transferStallTicks = 0;
            this.closeScreen();
            this.state = State.TAKE_OPEN_PV;
            return;
        }
        if (!this.containerHasConfiguredItem(chest, true)) {
            this.transferStallTicks = 0;
            this.closeScreen();
            this.state = State.TAKE_GOTO_NEXT_CHEST;
            return;
        }
        int moved = this.takeConfiguredItemFromContainer();
        if (moved > 0) {
            this.transferStallTicks = 0;
            return;
        }
        ++this.transferStallTicks;
        if (this.transferStallTicks >= 10) {
            this.transferStallTicks = 0;
            if (this.containerHasConfiguredItem(chest, true)) {
                this.takeConfiguredItemFromContainer();
            } else {
                this.closeScreen();
                this.state = State.TAKE_GOTO_NEXT_CHEST;
            }
        }
    }

    private void processTakeOpenPv() {
        if (this.mc.field_71462_r instanceof GuiContainer && ((GuiContainer)this.mc.field_71462_r).field_147002_h instanceof ContainerChest) {
            this.state = State.TAKE_WAIT_PV;
            return;
        }
        if (this.commandCooldownTicks > 0) {
            return;
        }
        this.mc.field_71439_g.func_71165_d("/pv " + this.pvNumber);
        this.commandCooldownTicks = 2;
        this.waitTicks = 0;
        this.state = State.TAKE_WAIT_PV;
    }

    private void processTakeWaitPv() {
        if (this.mc.field_71462_r instanceof GuiContainer && ((GuiContainer)this.mc.field_71462_r).field_147002_h instanceof ContainerChest) {
            this.state = State.TAKE_PUT_IN_PV;
            return;
        }
        ++this.waitTicks;
        if (this.waitTicks >= 40) {
            this.waitTicks = 0;
            this.state = State.TAKE_OPEN_PV;
        }
    }

    private void processTakePutInPv() {
        if (!(this.mc.field_71462_r instanceof GuiContainer) || !(((GuiContainer)this.mc.field_71462_r).field_147002_h instanceof ContainerChest)) {
            this.state = State.TAKE_OPEN_PV;
            return;
        }
        ContainerChest pv = (ContainerChest)((GuiContainer)this.mc.field_71462_r).field_147002_h;
        if (this.countConfiguredItemsInInventory() > 0 && !this.isPvFullWithItem(pv)) {
            int moved = this.moveConfiguredItemToContainer();
            if (moved > 0) {
                this.transferStallTicks = 0;
                return;
            }
            ++this.transferStallTicks;
            if (this.transferStallTicks < 10) {
                return;
            }
        }
        this.transferStallTicks = 0;
        boolean inventoryFull = this.isInventoryFull();
        boolean inventoryEmpty = this.countConfiguredItemsInInventory() <= 0;
        boolean pvFull = this.isPvFullWithItem(pv);
        if (pvFull && inventoryFull) {
            this.closeScreen();
            this.homeBackDelayTicks = 0;
            this.state = State.TAKE_TELEPORT;
            return;
        }
        if (inventoryEmpty) {
            this.closeScreen();
            this.state = State.TAKE_OPEN_CHEST;
            return;
        }
        if (pvFull) {
            this.closeScreen();
            this.state = State.TAKE_OPEN_CHEST;
        }
    }

    private void processTakeGotoNextChest() {
        if (this.mc.field_71462_r != null) {
            this.closeScreen();
            return;
        }
        ++this.gotoDelayTicks;
        int delayTicks = Math.max(0, (int)Math.ceil(this.gotoDelaySeconds * 20.0));
        if (this.gotoDelayTicks < delayTicks) {
            return;
        }
        if (this.commandCooldownTicks > 0) {
            return;
        }
        this.mc.field_71439_g.func_71165_d(this.gotoCommand);
        this.gotoWaitTicks = 0;
        this.gotoDelayTicks = 0;
        this.commandCooldownTicks = 2;
        this.state = State.TAKE_WAIT_GOTO;
        this.debug("#goto r\u01b0\u01a1ng ti\u1ebfp theo: " + this.gotoCommand + " | delay=" + this.formatDelay(this.gotoDelaySeconds) + "s");
    }

    private void processTakeWaitGoto() {
        ++this.gotoWaitTicks;
        if (this.isLookingAtChest()) {
            this.state = State.TAKE_OPEN_CHEST;
            this.gotoWaitTicks = 0;
            return;
        }
        if (this.gotoWaitTicks >= 100) {
            this.gotoWaitTicks = 0;
            this.gotoDelayTicks = 0;
            this.state = State.TAKE_GOTO_NEXT_CHEST;
        }
    }

    private String formatDelay(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long)value);
        }
        return String.valueOf(value);
    }

    private void processTakeTeleport() {
        int delayTicks = Math.max(0, (int)Math.ceil(this.homeBackDelaySeconds * 20.0));
        if (this.homeBackDelayTicks < delayTicks) {
            ++this.homeBackDelayTicks;
            return;
        }
        if (this.commandCooldownTicks > 0) {
            return;
        }
        this.teleportStartPos = this.mc.field_71439_g.func_180425_c();
        this.homeFallbackConsumed = false;
        this.mc.field_71439_g.func_71165_d(this.setHomeCommand);
        this.mc.field_71439_g.func_71165_d(this.backCommand);
        this.teleportCommandSent = true;
        this.waitTicks = 0;
        this.watchdogTicks = 0;
        this.homeBackDelayTicks = 0;
        this.commandCooldownTicks = 2;
        this.state = State.TAKE_WAIT_TELEPORT;
        this.debug("PV + inventory FULL -> cho " + this.formatDelay(this.homeBackDelaySeconds) + "s -> " + this.setHomeCommand + " + " + this.backCommand);
    }

    private void processTakeWaitTeleport() {
        int currentY = this.mc.field_71439_g.func_180425_c().func_177956_o();
        if (currentY != this.yCheck) {
            this.waitTicks = 0;
            this.watchdogTicks = 0;
            this.teleportStartPos = null;
            this.mode = Mode.SELL;
            this.state = State.SELL_OPEN_PV;
            this.debug("Y hien tai != Y check -> chuyen ngay sang logic BAN");
            return;
        }
        ++this.waitTicks;
        if (this.waitTicks >= 140) {
            this.waitTicks = 0;
            this.watchdogTicks = 0;
            this.teleportStartPos = this.mc.field_71439_g.func_180425_c();
            this.mc.field_71439_g.func_71165_d(this.setHomeCommand);
            this.mc.field_71439_g.func_71165_d(this.backCommand);
            this.debug("Watchdog 7s -> retry " + this.setHomeCommand + " + " + this.backCommand);
        }
    }

    private void processSellOpenPv() {
        if (this.mc.field_71462_r instanceof GuiContainer && ((GuiContainer)this.mc.field_71462_r).field_147002_h instanceof ContainerChest) {
            this.state = State.SELL_WAIT_PV;
            return;
        }
        if (this.commandCooldownTicks > 0) {
            return;
        }
        this.mc.field_71439_g.func_71165_d("/pv " + this.pvNumber);
        this.commandCooldownTicks = 2;
        this.waitTicks = 0;
        this.state = State.SELL_WAIT_PV;
    }

    private void processSellWaitPv() {
        if (this.mc.field_71462_r instanceof GuiContainer && ((GuiContainer)this.mc.field_71462_r).field_147002_h instanceof ContainerChest) {
            this.state = State.SELL_TAKE_FROM_PV;
            return;
        }
        ++this.waitTicks;
        if (this.waitTicks >= 40) {
            this.waitTicks = 0;
            this.state = State.SELL_OPEN_PV;
        }
    }

    private void processSellTakeFromPv() {
        if (!(this.mc.field_71462_r instanceof GuiContainer) || !(((GuiContainer)this.mc.field_71462_r).field_147002_h instanceof ContainerChest)) {
            this.state = State.SELL_OPEN_PV;
            return;
        }
        ContainerChest pv = (ContainerChest)((GuiContainer)this.mc.field_71462_r).field_147002_h;
        if (!this.containerHasConfiguredItem(pv, true)) {
            this.closeScreen();
            if (this.countConfiguredItemsInInventory() >= 16) {
                this.sellPvEmptyNeedsHome = true;
                this.state = State.SELLING;
            } else {
                this.state = State.SELL_TELEPORT;
            }
            return;
        }
        if (this.isInventoryFull()) {
            this.closeScreen();
            this.state = State.SELLING;
            return;
        }
        int moved = this.takeConfiguredItemFromContainer();
        if (moved > 0) {
            this.transferStallTicks = 0;
            return;
        }
        ++this.transferStallTicks;
        if (this.transferStallTicks >= 10) {
            this.transferStallTicks = 0;
            if (this.containerHasConfiguredItem(pv, true)) {
                this.takeConfiguredItemFromContainer();
            } else {
                this.closeScreen();
                this.state = State.SELL_TELEPORT;
            }
        }
    }

    private void processSellClosePv() {
        this.closeScreen();
        this.state = State.SELLING;
    }

    private void processSelling() {
        if (this.inCombat) {
            return;
        }
        if (this.countConfiguredItemsInInventory() < 16) {
            if (this.sellPvEmptyNeedsHome) {
                this.sellPvEmptyNeedsHome = false;
                this.state = State.SELL_TELEPORT;
            } else {
                this.state = State.SELL_OPEN_PV;
            }
            return;
        }
        int before = this.countConfiguredItemsInInventory();
        for (int i = 0; i < 36 && this.countConfiguredItemsInInventory() >= 16; ++i) {
            if (this.inCombat) {
                break;
            }
            this.rightClickSell();
        }
        int after = this.countConfiguredItemsInInventory();
        if (after >= before) {
            return;
        }
    }

    private void processSellRecheckPv() {
        this.state = this.countConfiguredItemsInInventory() > 0 ? State.SELLING : State.SELL_OPEN_PV;
    }

    private void processSellTeleport() {
        this.teleportStartPos = this.mc.field_71439_g.func_180425_c();
        this.homeFallbackConsumed = false;
        this.mc.field_71439_g.func_71165_d("/home");
        this.teleportCommandSent = true;
        this.waitTicks = 0;
        this.watchdogTicks = 0;
        this.state = State.SELL_WAIT_TELEPORT;
        this.debug("PV 1 het -> /home");
    }

    private void processSellWaitTeleport() {
        int currentY;
        if (!this.yCheckConfigured) {
            this.yCheck = this.mc.field_71439_g.func_180425_c().func_177956_o();
            this.yCheckConfigured = true;
        }
        if ((currentY = this.mc.field_71439_g.func_180425_c().func_177956_o()) == this.yCheck) {
            this.waitTicks = 0;
            this.watchdogTicks = 0;
            this.teleportStartPos = null;
            this.mode = Mode.TAKE;
            this.state = State.TAKE_CHECK_INVENTORY;
            this.debug("Y hien tai = Y check -> chuyen ngay sang logic NHAN ITEM");
            return;
        }
        ++this.waitTicks;
        if (this.waitTicks >= 140) {
            this.waitTicks = 0;
            this.watchdogTicks = 0;
            this.teleportStartPos = this.mc.field_71439_g.func_180425_c();
            this.mc.field_71439_g.func_71165_d("/home");
            this.debug("Watchdog 7s -> retry /home");
        }
    }

    private boolean isConfiguredItem(ItemStack stack) {
        if (stack == null || stack.func_190926_b()) {
            return false;
        }
        Item item = Item.func_150899_d((int)this.itemId);
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

    private boolean isInventoryFull() {
        if (this.mc == null || this.mc.field_71439_g == null) {
            return false;
        }
        if (this.mc.field_71439_g.field_71071_by.field_70462_a.size() < 36) {
            return false;
        }
        for (int i = 0; i < 36; ++i) {
            ItemStack stack = (ItemStack)this.mc.field_71439_g.field_71071_by.field_70462_a.get(i);
            if (stack != null && !stack.func_190926_b()) continue;
            return false;
        }
        return true;
    }

    private boolean containerHasConfiguredItem(ContainerChest container, boolean lowerOnly) {
        int end = lowerOnly ? container.func_85151_d().func_70302_i_() : container.field_75151_b.size();
        for (int i = 0; i < end; ++i) {
            Slot slot = (Slot)container.field_75151_b.get(i);
            if (slot == null || !slot.func_75216_d() || !this.isConfiguredItem(slot.func_75211_c())) continue;
            return true;
        }
        return false;
    }

    private boolean isPvFullWithItem(ContainerChest container) {
        int size = container.func_85151_d().func_70302_i_();
        for (int i = 0; i < size; ++i) {
            Slot slot = (Slot)container.field_75151_b.get(i);
            if (slot != null && slot.func_75216_d() && this.isConfiguredItem(slot.func_75211_c())) continue;
            return false;
        }
        return true;
    }

    private int takeConfiguredItemFromContainer() {
        if (!(this.mc.field_71462_r instanceof GuiContainer)) {
            return 0;
        }
        try {
            GuiContainer gui = (GuiContainer)this.mc.field_71462_r;
            if (!(gui.field_147002_h instanceof ContainerChest)) {
                return 0;
            }
            ContainerChest container = (ContainerChest)gui.field_147002_h;
            int totalTaken = 0;
            int chestSlotCount = container.func_85151_d().func_70302_i_();
            for (int i = chestSlotCount - 1; i >= 0; --i) {
                Slot slot = (Slot)container.field_75151_b.get(i);
                if (slot == null || !slot.func_75216_d() || !this.isConfiguredItem(slot.func_75211_c())) continue;
                this.mc.field_71442_b.func_187098_a(container.field_75152_c, i, 0, ClickType.QUICK_MOVE, (EntityPlayer)this.mc.field_71439_g);
                ++totalTaken;
            }
            return totalTaken;
        }
        catch (Exception e) {
            System.out.println("[MacroRecorder] L\u1ed7i khi l\u1ea5y \u0111\u1ed3 trong r\u01b0\u01a1ng: " + e);
            e.printStackTrace();
            return 0;
        }
    }

    private int moveConfiguredItemToContainer() {
        if (!(this.mc.field_71462_r instanceof GuiContainer)) {
            return 0;
        }
        try {
            int startInventorySlot;
            GuiContainer gui = (GuiContainer)this.mc.field_71462_r;
            if (!(gui.field_147002_h instanceof ContainerChest)) {
                return 0;
            }
            ContainerChest container = (ContainerChest)gui.field_147002_h;
            int totalMoved = 0;
            for (int i = startInventorySlot = container.func_85151_d().func_70302_i_(); i < container.field_75151_b.size(); ++i) {
                Slot slot = (Slot)container.field_75151_b.get(i);
                if (slot == null || !slot.func_75216_d() || !this.isConfiguredItem(slot.func_75211_c())) continue;
                this.mc.field_71442_b.func_187098_a(container.field_75152_c, i, 0, ClickType.QUICK_MOVE, (EntityPlayer)this.mc.field_71439_g);
                ++totalMoved;
            }
            return totalMoved;
        }
        catch (Exception e) {
            System.out.println("[MacroRecorder] L\u1ed7i khi b\u1ecf \u0111\u1ed3 v\u00e0o kho: " + e);
            e.printStackTrace();
            return 0;
        }
    }

    private boolean isLookingAtChest() {
        try {
            if (this.mc.field_71476_x == null || this.mc.field_71476_x.field_72313_a != RayTraceResult.Type.BLOCK) {
                return false;
            }
            BlockPos pos = this.mc.field_71476_x.func_178782_a();
            Block block = this.mc.field_71441_e.func_180495_p(pos).func_177230_c();
            return block instanceof BlockChest;
        }
        catch (Exception e) {
            return false;
        }
    }

    private void openChest() {
        try {
            if (this.mc.field_71476_x == null || this.mc.field_71476_x.field_72313_a != RayTraceResult.Type.BLOCK) {
                return;
            }
            this.mc.field_71442_b.func_187099_a(this.mc.field_71439_g, this.mc.field_71441_e, this.mc.field_71476_x.func_178782_a(), this.mc.field_71476_x.field_178784_b, this.mc.field_71476_x.field_72307_f, EnumHand.MAIN_HAND);
        }
        catch (Exception e) {
            System.out.println("[MacroRecorder] Loi mo ruong: " + e);
        }
    }

    private void rightClickSell() {
        try {
            this.mc.field_71442_b.func_187101_a((EntityPlayer)this.mc.field_71439_g, (World)this.mc.field_71441_e, EnumHand.MAIN_HAND);
        }
        catch (Exception e) {
            System.out.println("[MacroRecorder] Loi ban item: " + e);
        }
    }

    private void closeScreen() {
        try {
            if (this.mc.field_71462_r != null && this.mc.field_71439_g != null) {
                this.mc.field_71439_g.func_71053_j();
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    private void sendF4Status(boolean on) {
        if (this.mc == null || this.mc.field_71439_g == null) {
            return;
        }
        try {
            this.mc.field_71439_g.func_145747_a((ITextComponent)new TextComponentString(TextFormatting.GRAY + "[F4] " + (on ? TextFormatting.GREEN + "ON" : TextFormatting.RED + "OFF")));
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    private void debug(String message) {
        if (!this.debugChat || this.mc == null || this.mc.field_71439_g == null) {
            return;
        }
        try {
            this.mc.field_71439_g.func_145747_a((ITextComponent)new TextComponentString(TextFormatting.GRAY + "[F4] " + TextFormatting.YELLOW + message));
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    private String formatAngleForDebug(float value) {
        if ((double)value == Math.rint(value)) {
            return String.valueOf((int)value);
        }
        return String.valueOf(value);
    }

    private float normalizeYaw(float yaw) {
        if ((yaw %= 360.0f) >= 180.0f) {
            yaw -= 360.0f;
        }
        if (yaw < -180.0f) {
            yaw += 360.0f;
        }
        return yaw;
    }

    @SubscribeEvent
    @SideOnly(value=Side.CLIENT)
    public void onClientChat(ClientChatReceivedEvent event) {
        if (event == null || event.getMessage() == null) {
            return;
        }
        String unformatted = event.getMessage().func_150260_c();
        if (unformatted == null || unformatted.isEmpty()) {
            return;
        }
        if (isCombatStartMessage(unformatted)) {
            if (!this.inCombat) {
                this.enterCombat("Tin nhan server: vao chien dau");
            } else {
                this.combatTicksRemaining = COMBAT_TIMEOUT_TICKS;
            }
        } else if (isCombatEndMessage(unformatted)) {
            this.exitCombat("Tin nhan server: het trang thai chien dau");
        }
    }

    @SubscribeEvent
    @SideOnly(value=Side.CLIENT)
    public void onClientSendMessage(ClientChatEvent event) {
        if (event == null || event.getMessage() == null) {
            return;
        }
        String msg = event.getMessage().trim();
        String lower = msg.toLowerCase();

        if (lower.startsWith("/key") || lower.startsWith("/sellkey")) {
            event.setCanceled(true);
            String[] parts = msg.split("\\s+");
            if (parts.length < 2 || parts[1].trim().isEmpty()) {
                if (this.mc.field_71439_g != null) {
                    if (LicenseGuard.isLicensed) {
                        this.mc.field_71439_g.func_145747_a((ITextComponent)new TextComponentString(
                                TextFormatting.GREEN + "[B\u1ea3n Quy\u1ec1n] \u0110\u00c3 K\u00cdCH HO\u1ea0T! H\u1ea1n: " + TextFormatting.AQUA + LicenseGuard.licenseExpiry +
                                TextFormatting.YELLOW + " (" + LicenseGuard.clientName + ")"));
                    } else {
                        this.mc.field_71439_g.func_145747_a((ITextComponent)new TextComponentString(
                                TextFormatting.RED + "[B\u1ea3n Quy\u1ec1n] Ch\u01b0a k\u00edch ho\u1ea1t! G\u00f5: /key <m\u00e3-key> ho\u1eb7c b\u1ea5m F8. HWID c\u1ee7a b\u1ea1n: " + TextFormatting.YELLOW + LicenseGuard.getHWID()));
                    }
                }
                return;
            }

            final String inputKey = parts[1].trim();
            if (this.mc.field_71439_g != null) {
                this.mc.field_71439_g.func_145747_a((ITextComponent)new TextComponentString(
                        TextFormatting.YELLOW + "[B\u1ea3n Quy\u1ec1n] \u0110ang ki\u1ec3m tra Key v\u1edbi Google Sheet..."));
            }

            LicenseGuard.verifyOnline(inputKey, new Runnable() {
                @Override
                public void run() {
                    if (MacroRecorderMod.this.mc.field_71439_g != null) {
                        if (LicenseGuard.isLicensed) {
                            MacroRecorderMod.this.mc.field_71439_g.func_145747_a((ITextComponent)new TextComponentString(
                                    TextFormatting.GREEN + "[B\u1ea3n Quy\u1ec1n] K\u00cdCH HO\u1ea0T TH\u00c0NH C\u00d4NG! Ch\u00e0o m\u1eebng " + TextFormatting.YELLOW + LicenseGuard.clientName +
                                    TextFormatting.GREEN + " | H\u1ea1n: " + TextFormatting.AQUA + LicenseGuard.licenseExpiry));
                        } else {
                            MacroRecorderMod.this.mc.field_71439_g.func_145747_a((ITextComponent)new TextComponentString(
                                    TextFormatting.RED + "[B\u1ea3n Quy\u1ec1n] K\u00edch ho\u1ea1t th\u1ea5t b\u1ea1i: " + LicenseGuard.lastStatusMsg));
                        }
                    }
                }
            });
        } else if (lower.equals("/hwid") || lower.equals("/sellhwid")) {
            event.setCanceled(true);
            String h = LicenseGuard.getHWID();
            GuiScreen.func_146275_d(h);
            if (this.mc.field_71439_g != null) {
                this.mc.field_71439_g.func_145747_a((ITextComponent)new TextComponentString(
                        TextFormatting.GREEN + "[B\u1ea3n Quy\u1ec1n] HWID: " + TextFormatting.YELLOW + h + TextFormatting.GRAY + " (\u0110\u00e3 sao ch\u00e9p v\u00e0o clipboard!)"));
            }
        }
    }

    private void enterCombat(String reason) {
        this.inCombat = true;
        this.combatTicksRemaining = COMBAT_TIMEOUT_TICKS;
        this.closeScreen();
        this.cameraLockActive = false;

        if (this.stopMacroOnCombat) {
            this.stopMacro();
            this.sendCombatAlert("PHAT HIEN CHIEN DAU (" + reason + ") -> DA DUNG MACRO HOAN TOAN (F4 OFF)!");
        } else {
            this.sendCombatAlert("PHAT HIEN CHIEN DAU (" + reason + ") -> NGUNG SELL NGAY LAP TUC CHO DEN KHI HET CHIEN DAU!");
        }
    }

    private void exitCombat(String reason) {
        if (!this.inCombat && this.combatTicksRemaining <= 0) {
            return;
        }
        this.inCombat = false;
        this.combatTicksRemaining = 0;
        this.sendCombatAlert("HET TRANG THAI CHIEN DAU (" + reason + ") -> AN TOAN!");
        if (this.running && !this.stopMacroOnCombat) {
            if (this.mc != null && this.mc.field_71439_g != null) {
                int currentY = this.mc.field_71439_g.func_180425_c().func_177956_o();
                if (currentY == this.yCheck) {
                    this.mode = Mode.TAKE;
                    this.state = State.TAKE_CHECK_INVENTORY;
                    this.cameraLockActive = this.lockPitchYawWhenYCorrect;
                } else {
                    this.mode = Mode.SELL;
                    this.state = State.SELL_OPEN_PV;
                    this.cameraLockActive = false;
                }
                this.waitTicks = 0;
                this.watchdogTicks = 0;
                this.transferStallTicks = 0;
                this.sendCombatAlert("Tiep tuc sell / macro...");
            }
        }
    }

    private void sendCombatAlert(String message) {
        if (this.mc == null || this.mc.field_71439_g == null) {
            return;
        }
        try {
            this.mc.field_71439_g.func_145747_a((ITextComponent)new TextComponentString(TextFormatting.RED + "[F4 COMBAT] " + TextFormatting.YELLOW + message));
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    private static String stripAccents(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '\u00e0': case '\u00e1': case '\u1ea1': case '\u1ea3': case '\u00e3':
                case '\u00e2': case '\u1ea7': case '\u1ea5': case '\u1ead': case '\u1ea9': case '\u1eab':
                case '\u0103': case '\u1eb1': case '\u1eb3': case '\u1eb5': case '\u1eb7':
                case '\u00c0': case '\u00c1': case '\u1ea0': case '\u1ea2': case '\u00c3':
                case '\u00c2': case '\u1ea6': case '\u1ea4': case '\u1eac': case '\u1ea8': case '\u1eaa':
                case '\u0102': case '\u1eb0': case '\u1eb2': case '\u1eb6': case '\u1eb4':
                    sb.append('a'); break;
                case '\u00e8': case '\u00e9': case '\u1eb9': case '\u1ebb': case '\u1ebd':
                case '\u00ea': case '\u1ec1': case '\u1ebf': case '\u1ec7': case '\u1ec3': case '\u1ec5':
                case '\u00c8': case '\u00c9': case '\u1eb8': case '\u1eba': case '\u1ebc':
                case '\u00ca': case '\u1ec0': case '\u1ebe': case '\u1ec6': case '\u1ec2': case '\u1ec4':
                    sb.append('e'); break;
                case '\u00ec': case '\u00ed': case '\u1ecb': case '\u1ec9': case '\u0129':
                case '\u00cc': case '\u00cd': case '\u1eca': case '\u1ec8': case '\u0128':
                    sb.append('i'); break;
                case '\u00f2': case '\u00f3': case '\u1ecd': case '\u1ecf': case '\u00f5':
                case '\u00f4': case '\u1ed3': case '\u1ed1': case '\u1ed9': case '\u1ed5': case '\u1ed7':
                case '\u01a1': case '\u1edd': case '\u1edb': case '\u1ee3': case '\u1edf': case '\u1ee1':
                case '\u00d2': case '\u00d3': case '\u1ecc': case '\u1ece': case '\u00d5':
                case '\u00d4': case '\u1ed2': case '\u1ed0': case '\u1ed8': case '\u1ed4': case '\u1ed6':
                case '\u01a0': case '\u1edc': case '\u1eda': case '\u1ee2': case '\u1ede': case '\u1ee0':
                    sb.append('o'); break;
                case '\u00f9': case '\u00fa': case '\u1ee5': case '\u1ee7': case '\u0169':
                case '\u01b0': case '\u1eeb': case '\u1ee9': case '\u1ef1': case '\u1eed': case '\u1eef':
                case '\u00d9': case '\u00da': case '\u1ee4': case '\u1ee6': case '\u0168':
                case '\u01af': case '\u1eea': case '\u1ee8': case '\u1ef0': case '\u1eec': case '\u1eee':
                    sb.append('u'); break;
                case '\u1ef3': case '\u00fd': case '\u1ef5': case '\u1ef7': case '\u1ef9':
                case '\u1ef2': case '\u00dd': case '\u1ef4': case '\u1ef6': case '\u1ef8':
                    sb.append('y'); break;
                case '\u0111': case '\u0110':
                    sb.append('d'); break;
                default:
                    sb.append(Character.toLowerCase(c)); break;
            }
        }
        return sb.toString();
    }

    private static boolean isCombatStartMessage(String text) {
        String s = stripAccents(text);
        if (s.contains("khong con trong trang thai") || s.contains("khng cn trong tr?ng thi") || s.contains("khong con")) {
            return false;
        }
        return s.contains("dang chien dau")
            || s.contains("chien dau voi")
            || s.contains("khi dang chien dau")
            || s.contains("combattag")
            || s.contains("combat tag")
            || s.contains("in combat")
            || s.contains("?ang chi?n ??u")
            || s.contains("chi?n ??u v?i")
            || (s.contains("chien dau") && !s.contains("khong con"));
    }

    private static boolean isCombatEndMessage(String text) {
        String s = stripAccents(text);
        return s.contains("khong con trong trang thai chien dau")
            || s.contains("khong con trong trang thai")
            || s.contains("khng cn trong tr?ng thi")
            || s.contains("kh?ng c?n trong tr?ng th?i")
            || s.contains("ban da chet")
            || s.contains("b?n ? ch?t")
            || s.contains("b?n ?a ch?t")
            || s.contains("no longer in combat")
            || s.contains("combat expired");
    }

    @SubscribeEvent
    @SideOnly(value=Side.CLIENT)
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.TEXT) {
            return;
        }
        if (this.mc == null || this.mc.field_71466_p == null) {
            return;
        }
        if (this.inCombat) {
            int secondsLeft = Math.max(0, (this.combatTicksRemaining + 19) / 20);
            String alert = TextFormatting.RED + "[!] DANG TRONG CHIEN DAU (" + secondsLeft + "s) - DA NGUNG SELL!";
            int screenWidth = event.getResolution().func_78326_a();
            int strWidth = this.mc.field_71466_p.func_78256_a(alert);
            int x = (screenWidth - strWidth) / 2;
            int y = 10;
            this.mc.field_71466_p.func_175063_a(alert, (float)x, (float)y, 0xFF5555);
        }

        if (LicenseGuard.isLicensed) {
            this.mc.field_71466_p.func_175063_a(TextFormatting.GREEN + "[VIP] " + LicenseGuard.licenseExpiry, 4.0f, 4.0f, 0x55FF55);
        } else {
            this.mc.field_71466_p.func_175063_a(TextFormatting.RED + "[CHƯA KÍCH HOẠT] Bấm F8 nhập Key", 4.0f, 4.0f, 0xFF5555);
        }
    }

    @SideOnly(value=Side.CLIENT)
    private class SettingsGui
    extends GuiScreen {
        private GuiTextField itemField;
        private GuiTextField pvField;
        private GuiTextField gotoDelayField;
        private GuiTextField homeBackDelayField;
        private GuiTextField setHomeField;
        private GuiTextField backField;
        private GuiTextField gotoField;
        private GuiTextField yField;
        private GuiTextField yawField;
        private GuiTextField pitchField;
        private GuiTextField keyField;
        private GuiButton copyHwidButton;
        private GuiButton activateButton;
        private GuiButton lockPitchYawButton;
        private GuiButton debugButton;
        private GuiButton fillPvButton;
        private GuiButton combatButton;
        private String error = "";

        private SettingsGui() {
        }

        public void func_73866_w_() {
            int x = this.field_146294_l / 2 - 100;
            int y = Math.max(18, this.field_146295_m / 2 - 173);
            int w = 200;
            this.itemField = new GuiTextField(0, this.field_146289_q, x, y, w, 20);
            this.pvField = new GuiTextField(1, this.field_146289_q, x, y + 28, w, 20);
            this.gotoDelayField = new GuiTextField(2, this.field_146289_q, x, y + 56, w, 20);
            this.homeBackDelayField = new GuiTextField(3, this.field_146289_q, x, y + 84, w, 20);
            this.setHomeField = new GuiTextField(4, this.field_146289_q, x, y + 112, w, 20);
            this.backField = new GuiTextField(5, this.field_146289_q, x, y + 140, w, 20);
            this.gotoField = new GuiTextField(6, this.field_146289_q, x, y + 168, w, 20);
            this.yField = new GuiTextField(7, this.field_146289_q, x, y + 196, w, 20);
            this.yawField = new GuiTextField(8, this.field_146289_q, x, y + 224, w, 20);
            this.pitchField = new GuiTextField(9, this.field_146289_q, x, y + 252, w, 20);
            this.itemField.func_146180_a(String.valueOf(MacroRecorderMod.this.itemId));
            this.pvField.func_146180_a(String.valueOf(MacroRecorderMod.this.pvNumber));
            this.gotoDelayField.func_146180_a(this.formatDelay(MacroRecorderMod.this.gotoDelaySeconds));
            this.homeBackDelayField.func_146180_a(this.formatDelay(MacroRecorderMod.this.homeBackDelaySeconds));
            this.setHomeField.func_146180_a(MacroRecorderMod.this.setHomeCommand);
            this.backField.func_146180_a(MacroRecorderMod.this.backCommand);
            this.gotoField.func_146180_a(MacroRecorderMod.this.gotoCommand);
            this.yawField.func_146180_a(this.formatAngle(MacroRecorderMod.this.lockedYaw));
            this.pitchField.func_146180_a(this.formatAngle(MacroRecorderMod.this.lockedPitch));
            if (!MacroRecorderMod.this.yCheckConfigured && this.field_146297_k != null && this.field_146297_k.field_71439_g != null) {
                MacroRecorderMod.this.yCheck = this.field_146297_k.field_71439_g.func_180425_c().func_177956_o();
                MacroRecorderMod.this.yCheckConfigured = true;
            }
            this.yField.func_146180_a(String.valueOf(MacroRecorderMod.this.yCheck));
            this.debugButton = new GuiButton(10, x, y + 280, 98, 20, this.debugText());
            this.fillPvButton = new GuiButton(11, x + 102, y + 280, 98, 20, this.fillPvText());
            this.lockPitchYawButton = new GuiButton(12, x, y + 308, w, 20, this.lockPitchYawText());
            this.combatButton = new GuiButton(15, x, y + 336, w, 20, this.combatText());
            this.field_146292_n.add(this.debugButton);
            this.field_146292_n.add(this.fillPvButton);
            this.field_146292_n.add(this.lockPitchYawButton);
            this.field_146292_n.add(this.combatButton);
            this.field_146292_n.add(new GuiButton(13, x, y + 364, 98, 20, "Luu"));
            this.field_146292_n.add(new GuiButton(14, x + 102, y + 364, 98, 20, "Dong"));

            int rx = x + 215;
            this.copyHwidButton = new GuiButton(16, rx, y + 26, 160, 20, "Sao chep HWID");
            this.keyField = new GuiTextField(20, this.field_146289_q, rx, y + 68, 160, 20);
            this.keyField.func_146180_a(LicenseGuard.activeKey);
            this.activateButton = new GuiButton(17, rx, y + 92, 160, 20, "Kich hoat Key");
            this.field_146292_n.add(this.copyHwidButton);
            this.field_146292_n.add(this.activateButton);
        }

        private String formatDelay(double value) {
            if (value == Math.rint(value)) {
                return String.valueOf((long)value);
            }
            return String.valueOf(value);
        }

        private String formatAngle(float value) {
            if ((double)value == Math.rint(value)) {
                return String.valueOf((int)value);
            }
            return String.valueOf(value);
        }

        private String debugText() {
            return "Debug chat: " + (MacroRecorderMod.this.debugChat ? "ON" : "OFF");
        }

        private String lockPitchYawText() {
            return "Khoa Pitch/Yaw khi Y dung: " + (MacroRecorderMod.this.lockPitchYawWhenYCorrect ? "ON" : "OFF");
        }

        private String fillPvText() {
            return "Full 36 -> /pv " + MacroRecorderMod.this.pvNumber + ": " + (MacroRecorderMod.this.fillPvBeforeGoto ? "ON" : "OFF");
        }

        private String combatText() {
            return "Khi bi danh: " + (MacroRecorderMod.this.stopMacroOnCombat ? "Dung han F4" : "Tam dung cho het");
        }

        public void func_73876_c() {
            this.itemField.func_146178_a();
            this.pvField.func_146178_a();
            this.gotoDelayField.func_146178_a();
            this.homeBackDelayField.func_146178_a();
            this.setHomeField.func_146178_a();
            this.backField.func_146178_a();
            this.yField.func_146178_a();
            this.yawField.func_146178_a();
            this.pitchField.func_146178_a();
            if (this.keyField != null) this.keyField.func_146178_a();
        }

        protected void func_146284_a(GuiButton button) {
            if (button.field_146127_k == 10) {
                MacroRecorderMod.this.debugChat = !MacroRecorderMod.this.debugChat;
                button.field_146126_j = this.debugText();
            } else if (button.field_146127_k == 11) {
                MacroRecorderMod.this.fillPvBeforeGoto = !MacroRecorderMod.this.fillPvBeforeGoto;
                button.field_146126_j = this.fillPvText();
            } else if (button.field_146127_k == 12) {
                MacroRecorderMod.this.lockPitchYawWhenYCorrect = !MacroRecorderMod.this.lockPitchYawWhenYCorrect;
                if (!MacroRecorderMod.this.lockPitchYawWhenYCorrect) {
                    MacroRecorderMod.this.cameraLockActive = false;
                } else if (this.field_146297_k != null && this.field_146297_k.field_71439_g != null && this.field_146297_k.field_71439_g.func_180425_c().func_177956_o() == MacroRecorderMod.this.yCheck) {
                    MacroRecorderMod.this.cameraLockActive = true;
                    MacroRecorderMod.this.consumeMouseDelta();
                    MacroRecorderMod.this.forceLockedRotation();
                }
                button.field_146126_j = this.lockPitchYawText();
            } else if (button.field_146127_k == 15) {
                MacroRecorderMod.this.stopMacroOnCombat = !MacroRecorderMod.this.stopMacroOnCombat;
                button.field_146126_j = this.combatText();
            } else if (button.field_146127_k == 16) {
                GuiScreen.func_146275_d(LicenseGuard.getHWID());
                this.error = "Da sao chep HWID: " + LicenseGuard.getHWID();
            } else if (button.field_146127_k == 17) {
                final String inputKey = this.keyField.func_146179_b().trim();
                if (inputKey.isEmpty()) {
                    this.error = "Vui long nhap Key!";
                } else {
                    this.error = "Dang kiem tra Key voi Google Sheet...";
                    LicenseGuard.verifyOnline(inputKey, new Runnable() {
                        @Override
                        public void run() {
                            SettingsGui.this.error = LicenseGuard.lastStatusMsg;
                        }
                    });
                }
            } else if (button.field_146127_k == 13) {
                this.saveSettings();
            } else if (button.field_146127_k == 14) {
                this.field_146297_k.func_147108_a(null);
            }
        }

        private void saveSettings() {
            try {
                float newPitch;
                String itemText = this.itemField.func_146179_b().trim();
                String pvText = this.pvField.func_146179_b().trim();
                String delayText = this.gotoDelayField.func_146179_b().trim();
                String homeBackDelayText = this.homeBackDelayField.func_146179_b().trim();
                String yText = this.yField.func_146179_b().trim();
                int newItem = itemText.isEmpty() ? MacroRecorderMod.this.itemId : Integer.parseInt(itemText);
                int newPv = pvText.isEmpty() ? MacroRecorderMod.this.pvNumber : Integer.parseInt(pvText);
                double newDelay = delayText.isEmpty() ? MacroRecorderMod.this.gotoDelaySeconds : Double.parseDouble(delayText);
                double newHomeBackDelay = homeBackDelayText.isEmpty() ? MacroRecorderMod.this.homeBackDelaySeconds : Double.parseDouble(homeBackDelayText);
                int newY = yText.isEmpty() ? MacroRecorderMod.this.yCheck : Integer.parseInt(yText);
                float newYaw = this.yawField.func_146179_b().trim().isEmpty() ? MacroRecorderMod.this.lockedYaw : Float.parseFloat(this.yawField.func_146179_b().trim());
                float f = newPitch = this.pitchField.func_146179_b().trim().isEmpty() ? MacroRecorderMod.this.lockedPitch : Float.parseFloat(this.pitchField.func_146179_b().trim());
                if (newItem < 0 || newPv < 1 || newDelay < 0.0 || newHomeBackDelay < 0.0 || newPitch < -90.0f || newPitch > 90.0f || Float.isNaN(newYaw) || Float.isInfinite(newYaw) || Float.isNaN(newPitch) || Float.isInfinite(newPitch)) {
                    throw new NumberFormatException();
                }
                MacroRecorderMod.this.itemId = newItem;
                MacroRecorderMod.this.pvNumber = newPv;
                MacroRecorderMod.this.gotoDelaySeconds = newDelay;
                MacroRecorderMod.this.homeBackDelaySeconds = newHomeBackDelay;
                MacroRecorderMod.this.setHomeCommand = this.normalizeCommand(this.setHomeField.func_146179_b());
                MacroRecorderMod.this.backCommand = this.normalizeCommand(this.backField.func_146179_b());
                MacroRecorderMod.this.gotoCommand = this.normalizeGotoCommand(this.gotoField.func_146179_b());
                MacroRecorderMod.this.yCheck = newY;
                MacroRecorderMod.this.yCheckConfigured = true;
                MacroRecorderMod.this.lockedYaw = MacroRecorderMod.this.normalizeYaw(newYaw);
                MacroRecorderMod.this.lockedPitch = newPitch;
                this.error = "";
                this.field_146297_k.func_147108_a(null);
            }
            catch (NumberFormatException e) {
                this.error = "ID / PV / Delay / Y phai la so hop le (Delay #goto va delay /sethome + /back cho phep 0.1, 0.2...).";
            }
            catch (Exception e) {
                this.error = "Khong luu duoc: " + e.getClass().getSimpleName();
            }
        }

        private String normalizeCommand(String value) {
            String string = value = value == null ? "" : value.trim();
            if (value.isEmpty()) {
                return "/";
            }
            return value.startsWith("/") ? value : "/" + value;
        }

        private String normalizeGotoCommand(String value) {
            value = value == null ? "" : value.trim();
            return value.isEmpty() ? "#goto ~ ~ ~-1" : value;
        }

        protected void func_73869_a(char typedChar, int keyCode) {
            if (keyCode == 1) {
                this.field_146297_k.func_147108_a(null);
                return;
            }
            this.itemField.func_146201_a(typedChar, keyCode);
            this.pvField.func_146201_a(typedChar, keyCode);
            this.gotoDelayField.func_146201_a(typedChar, keyCode);
            this.homeBackDelayField.func_146201_a(typedChar, keyCode);
            this.setHomeField.func_146201_a(typedChar, keyCode);
            this.backField.func_146201_a(typedChar, keyCode);
            this.gotoField.func_146201_a(typedChar, keyCode);
            this.yField.func_146201_a(typedChar, keyCode);
            this.yawField.func_146201_a(typedChar, keyCode);
            this.pitchField.func_146201_a(typedChar, keyCode);
            if (this.keyField != null) this.keyField.func_146201_a(typedChar, keyCode);
            super.func_73869_a(typedChar, keyCode);
        }

        protected void func_73864_a(int mouseX, int mouseY, int mouseButton) {
            int x = this.field_146294_l / 2 - 100;
            int y = Math.max(18, this.field_146295_m / 2 - 173);
            if (mouseButton == 0) {
                if (mouseX >= x && mouseX <= x + 98 && mouseY >= y + 364 && mouseY <= y + 384) {
                    this.saveSettings();
                    return;
                }
                if (mouseX >= x + 102 && mouseX <= x + 200 && mouseY >= y + 364 && mouseY <= y + 384) {
                    this.field_146297_k.func_147108_a(null);
                    return;
                }
            }
            super.func_73864_a(mouseX, mouseY, mouseButton);
            this.itemField.func_146192_a(mouseX, mouseY, mouseButton);
            this.pvField.func_146192_a(mouseX, mouseY, mouseButton);
            this.gotoDelayField.func_146192_a(mouseX, mouseY, mouseButton);
            this.homeBackDelayField.func_146192_a(mouseX, mouseY, mouseButton);
            this.setHomeField.func_146192_a(mouseX, mouseY, mouseButton);
            this.backField.func_146192_a(mouseX, mouseY, mouseButton);
            this.gotoField.func_146192_a(mouseX, mouseY, mouseButton);
            this.yField.func_146192_a(mouseX, mouseY, mouseButton);
            this.yawField.func_146192_a(mouseX, mouseY, mouseButton);
            this.pitchField.func_146192_a(mouseX, mouseY, mouseButton);
            if (this.keyField != null) this.keyField.func_146192_a(mouseX, mouseY, mouseButton);
        }

        public void func_73863_a(int mouseX, int mouseY, float partialTicks) {
            this.func_146276_q_();
            int x = this.field_146294_l / 2 - 100;
            int y = this.field_146295_m / 2 - 173;
            this.func_73732_a(this.field_146289_q, "F8 - CAI DAT F4", this.field_146294_l / 2, y - 24, 0xFFFFFF);
            this.func_73731_b(this.field_146289_q, "ID item F3+H", x - 90, y + 5, 0xFFFFFF);
            this.func_73731_b(this.field_146289_q, "So kho /pv", x - 90, y + 33, 0xFFFFFF);
            this.func_73731_b(this.field_146289_q, "Delay #goto (giay)", x - 90, y + 61, 0xFFFFFF);
            this.func_73731_b(this.field_146289_q, "Delay /sethome + /back (giay)", x - 90, y + 89, 0xFFFFFF);
            this.func_73731_b(this.field_146289_q, "Command /sethome", x - 90, y + 117, 0xFFFFFF);
            this.func_73731_b(this.field_146289_q, "Command /back", x - 90, y + 145, 0xFFFFFF);
            this.func_73731_b(this.field_146289_q, "Command #goto", x - 90, y + 173, 0xFFFFFF);
            this.func_73731_b(this.field_146289_q, "Y check", x - 90, y + 201, 0xFFFFFF);
            this.func_73731_b(this.field_146289_q, "Yaw khi Y dung", x - 90, y + 229, 0xFFFFFF);
            this.func_73731_b(this.field_146289_q, "Pitch khi Y dung", x - 90, y + 257, 0xFFFFFF);
            this.itemField.func_146194_f();
            this.pvField.func_146194_f();
            this.gotoDelayField.func_146194_f();
            this.homeBackDelayField.func_146194_f();
            this.setHomeField.func_146194_f();
            this.backField.func_146194_f();
            this.gotoField.func_146194_f();
            this.yField.func_146194_f();
            this.yawField.func_146194_f();
            this.pitchField.func_146194_f();

            int rx = x + 215;
            this.func_73731_b(this.field_146289_q, TextFormatting.GOLD + "=== BAN QUYEN TOOL ===", rx, y + 5, 0xFFAA00);
            this.func_73731_b(this.field_146289_q, TextFormatting.GRAY + "HWID: " + TextFormatting.WHITE + LicenseGuard.getHWID(), rx, y + 15, 0xCCCCCC);
            this.func_73731_b(this.field_146289_q, TextFormatting.GRAY + "Nhap Key ban quyen:", rx, y + 54, 0xCCCCCC);
            if (this.keyField != null) {
                this.keyField.func_146194_f();
            }

            if (LicenseGuard.isLicensed) {
                this.func_73731_b(this.field_146289_q, TextFormatting.GREEN + "✔ DA KICH HOAT", rx, y + 118, 0x55FF55);
                this.func_73731_b(this.field_146289_q, TextFormatting.AQUA + "Han: " + LicenseGuard.licenseExpiry, rx, y + 130, 0x55FFFF);
                this.func_73731_b(this.field_146289_q, TextFormatting.YELLOW + "KH: " + LicenseGuard.clientName, rx, y + 142, 0xFFFF55);
            } else {
                this.func_73731_b(this.field_146289_q, TextFormatting.RED + "✖ " + LicenseGuard.lastStatusMsg, rx, y + 118, 0xFF5555);
            }
            super.func_73863_a(mouseX, mouseY, partialTicks);
            if (!this.error.isEmpty()) {
                this.func_73732_a(this.field_146289_q, this.error, this.field_146294_l / 2, y + 392, 0xFF5555);
            }
        }
    }

    private static enum State {
        IDLE,
        TAKE_CHECK_INVENTORY,
        TAKE_OPEN_CHEST,
        TAKE_WAIT_CHEST,
        TAKE_READ_CHEST,
        TAKE_OPEN_PV,
        TAKE_WAIT_PV,
        TAKE_PUT_IN_PV,
        TAKE_GOTO_NEXT_CHEST,
        TAKE_WAIT_GOTO,
        TAKE_TELEPORT,
        TAKE_WAIT_TELEPORT,
        SELL_OPEN_PV,
        SELL_WAIT_PV,
        SELL_TAKE_FROM_PV,
        SELL_CLOSE_PV,
        SELLING,
        SELL_RECHECK_PV,
        SELL_TELEPORT,
        SELL_WAIT_TELEPORT;

    }

    private static enum Mode {
        NONE,
        TAKE,
        SELL;

    }

    @SideOnly(value=Side.CLIENT)
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

            int boxW = 280;
            int boxX = cx - boxW / 2;

            this.keyInput = new GuiTextField(101, this.field_146289_q, boxX, cy - 10, boxW, 20);
            this.keyInput.func_146203_f(128);
            this.keyInput.func_146195_b(true);
            if (LicenseGuard.activeKey != null) {
                this.keyInput.func_146180_a(LicenseGuard.activeKey);
            }

            this.copyHwidBtn = new GuiButton(1, boxX, cy - 45, boxW, 20, "Sao chep HWID");
            this.activateBtn = new GuiButton(2, boxX, cy + 20, boxW, 20, "KICH HOAT VA VAO GAME");
            this.exitBtn = new GuiButton(3, boxX, cy + 46, boxW, 20, "Thoat Game");

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
                        mc.func_147108_a(new net.minecraft.client.gui.GuiMainMenu());
                    }
                }
            }
        }

        @Override
        protected void func_146284_a(GuiButton button) {
            if (button.field_146127_k == 1) {
                GuiScreen.func_146275_d(LicenseGuard.getHWID());
                this.statusMsg = "Da sao chep HWID: " + LicenseGuard.getHWID();
                this.statusColor = 0x55FF55;
            } else if (button.field_146127_k == 2) {
                this.triggerActivate();
            } else if (button.field_146127_k == 3) {
                Minecraft.func_71410_x().func_71400_g();
            }
        }

        private void triggerActivate() {
            final String inputKey = this.keyInput.func_146179_b().trim();
            if (inputKey.isEmpty()) {
                this.statusMsg = "Vui long nhap License Key!";
                this.statusColor = 0xFF5555;
                return;
            }
            if (inputKey.equalsIgnoreCase(LicenseGuard.getHWID())) {
                this.statusMsg = "Ban dang dan nham ma HWID! Vui long nhap Key.";
                this.statusColor = 0xFFAA00;
                return;
            }

            this.statusMsg = "Dang kiem tra Key voi may chu...";
            this.statusColor = 0xFFFF55;
            this.activateBtn.field_146124_l = false;

            LicenseGuard.verifyOnline(inputKey, new Runnable() {
                @Override
                public void run() {
                    GuiLicenseGate.this.activateBtn.field_146124_l = true;
                    if (LicenseGuard.isLicensed) {
                        GuiLicenseGate.this.statusMsg = "KICH HOAT THANH CONG! Chao mung " + LicenseGuard.clientName;
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
            if (this.keyInput != null && this.keyInput.func_146201_a(typedChar, keyCode)) {
                return;
            }
            if (keyCode == 28 || keyCode == 156) {
                this.triggerActivate();
                return;
            }
            if (keyCode == 1) { // Block ESC
                return;
            }
            try {
                super.func_73869_a(typedChar, keyCode);
            } catch (Exception ignored) {}
        }

        @Override
        protected void func_73864_a(int mouseX, int mouseY, int mouseButton) {
            try {
                super.func_73864_a(mouseX, mouseY, mouseButton);
            } catch (Exception ignored) {}
            if (this.keyInput != null) {
                this.keyInput.func_146192_a(mouseX, mouseY, mouseButton);
            }
        }

        @Override
        public void func_73863_a(int mouseX, int mouseY, float partialTicks) {
            this.func_146276_q_();

            int cx = this.field_146294_l / 2;
            int cy = this.field_146295_m / 2;

            this.func_73732_a(this.field_146289_q, TextFormatting.GOLD + "=== KICH HOAT BAN QUYEN SELL PVP ===", cx, cy - 95, 0xFFAA00);
            this.func_73732_a(this.field_146289_q, TextFormatting.GRAY + "Vui long nhap License Key de vao game va su dung Mod", cx, cy - 80, 0xAAAAAA);

            String hwidText = TextFormatting.AQUA + "Ma may (HWID): " + TextFormatting.WHITE + LicenseGuard.getHWID();
            this.func_73732_a(this.field_146289_q, hwidText, cx, cy - 62, 0x55FFFF);

            this.func_73731_b(this.field_146289_q, TextFormatting.GRAY + "Nhap License Key:", cx - 140, cy - 22, 0xCCCCCC);

            if (this.keyInput != null) {
                this.keyInput.func_146194_f();
            }

            if (this.statusMsg != null && !this.statusMsg.isEmpty()) {
                this.func_73732_a(this.field_146289_q, this.statusMsg, cx, cy + 72, this.statusColor);
            }

            super.func_73863_a(mouseX, mouseY, partialTicks);
        }
    }
}
