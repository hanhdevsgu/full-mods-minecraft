package com.minclient.event;

import com.minclient.command.CommandManager;
import com.minclient.gui.ImpactClickGui;
import com.minclient.gui.ImpactHudRenderer;
import com.minclient.gui.WorldRenderHelper;
import com.minclient.module.Module;
import com.minclient.module.ModuleManager;
import com.minclient.pathfinding.MotorController;
import com.minclient.pathfinding.PathRenderer;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.PlayerSPPushOutOfBlocksEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

public class ClientEventHandler {
    private final ModuleManager moduleManager;
    private final CommandManager commandManager;
    private final MotorController motorController;
    private final ImpactHudRenderer hudRenderer;

    public ClientEventHandler(ModuleManager moduleManager, CommandManager commandManager, MotorController motorController) {
        this.moduleManager = moduleManager;
        this.commandManager = commandManager;
        this.motorController = motorController;
        this.hudRenderer = new ImpactHudRenderer(moduleManager);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            moduleManager.onTick();
            motorController.onTick();
        }
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (!Keyboard.getEventKeyState()) return;

        int key = Keyboard.getEventKey();
        Minecraft mc = Minecraft.getMinecraft();

        // Mở Impact ClickGUI khi nhấn phím RSHIFT (Right Shift)
        if (key == Keyboard.KEY_RSHIFT) {
            if (mc.currentScreen == null) {
                mc.displayGuiScreen(new ImpactClickGui(moduleManager));
            }
            return;
        }

        // Kích hoạt Module theo KeyBind đã cài đặt
        if (key != Keyboard.KEY_NONE) {
            for (Module mod : moduleManager.getModules()) {
                if (mod.getKeyBind() == key) {
                    mod.toggle();
                }
            }
        }
    }

    @SubscribeEvent
    public void onClientChat(ClientChatEvent event) {
        String msg = event.getMessage();
        // TUYỆT ĐỐI HỦY GỬI LÊN SERVER nếu tin nhắn bắt đầu bằng '.' hoặc '#'
        if (msg != null && (msg.startsWith(".") || msg.startsWith("#"))) {
            event.setCanceled(true);
            commandManager.handleChat(msg);
            return;
        }

        // Áp dụng ChatSuffix nếu đang bật
        if (msg != null && !msg.startsWith("/") && moduleManager.getChatSuffixModule().isEnabled()) {
            event.setMessage(moduleManager.getChatSuffixModule().transformMessage(msg));
        }
    }

    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        if (moduleManager.getCriticalsModule().isEnabled()) {
            moduleManager.getCriticalsModule().doCrit();
        }
    }

    @SubscribeEvent
    public void onKnockback(LivingKnockBackEvent event) {
        if (event.getEntity() == Minecraft.getMinecraft().player && moduleManager.getVelocityModule().isEnabled()) {
            double hRatio = moduleManager.getVelocityModule().getHorizontalPercent();
            double vRatio = moduleManager.getVelocityModule().getVerticalPercent();
            if (hRatio == 0.0 && vRatio == 0.0) {
                event.setCanceled(true);
            } else {
                event.setStrength((float) (event.getStrength() * hRatio));
                event.setRatioX(event.getRatioX() * hRatio);
                event.setRatioZ(event.getRatioZ() * hRatio);
            }
        }
    }

    @SubscribeEvent
    public void onPushOutOfBlocks(PlayerSPPushOutOfBlocksEvent event) {
        if (moduleManager.getNoPushModule().isNoPushBlocks()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Text event) {
        hudRenderer.render(event);
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (moduleManager.getPathRenderModule().isEnabled()) {
            PathRenderer.render(event, motorController);
        }
        WorldRenderHelper.renderWorld(event, moduleManager);
    }
}
