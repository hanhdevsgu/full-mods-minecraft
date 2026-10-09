package com.minclient.event;

import com.minclient.command.CommandManager;
import com.minclient.gui.ImpactClickGui;
import com.minclient.module.ModuleManager;
import com.minclient.pathfinding.MotorController;
import com.minclient.pathfinding.PathRenderer;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.PlayerSPPushOutOfBlocksEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

public class ClientEventHandler {
    private final ModuleManager moduleManager;
    private final CommandManager commandManager;
    private final MotorController motorController;

    public ClientEventHandler(ModuleManager moduleManager, CommandManager commandManager, MotorController motorController) {
        this.moduleManager = moduleManager;
        this.commandManager = commandManager;
        this.motorController = motorController;
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
        // Mở Impact ClickGUI khi nhấn phím RSHIFT (Right Shift)
        if (Keyboard.getEventKeyState() && Keyboard.getEventKey() == Keyboard.KEY_RSHIFT) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.currentScreen == null) {
                mc.displayGuiScreen(new ImpactClickGui(moduleManager));
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
        }
    }

    @SubscribeEvent
    public void onPushOutOfBlocks(PlayerSPPushOutOfBlocksEvent event) {
        if (moduleManager.getNoPushModule().isEnabled()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (moduleManager.getPathRenderModule().isEnabled()) {
            PathRenderer.render(event, motorController);
        }
    }
}
