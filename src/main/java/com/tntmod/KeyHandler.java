package com.tntmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;

public class KeyHandler {
    public static final KeyBinding KEY_F7 = new KeyBinding("key.tntmod.f7", Keyboard.KEY_F7, "category.tntmod");
    public static final KeyBinding KEY_F8 = new KeyBinding("key.tntmod.f8", Keyboard.KEY_F8, "category.tntmod");

    public static void register() {
        ClientRegistry.registerKeyBinding(KEY_F7);
        ClientRegistry.registerKeyBinding(KEY_F8);
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null) return;

        if (KEY_F7.isPressed()) {
            mc.displayGuiScreen(new GuiF7Config());
        } else if (KEY_F8.isPressed()) {
            mc.displayGuiScreen(new GuiF8QuickToggle());
        }
    }
}
