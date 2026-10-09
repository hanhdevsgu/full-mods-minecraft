package com.autorefill;

import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;

public class KeyInputHandler {

    public static final KeyBinding toggleKey = new KeyBinding("key.autorefill.toggle", Keyboard.KEY_K, "key.categories.autorefill");

    public static void register() {
        ClientRegistry.registerKeyBinding(toggleKey);
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (toggleKey.isPressed()) {
            ConfigManager.enabled = !ConfigManager.enabled;
            ConfigManager.save();
            RefillHandler.sendMessage(ConfigManager.enabled
                    ? TextFormatting.GREEN + "[AutoRefill] Đã BẬT tự động lấy đồ (Phím K)!"
                    : TextFormatting.RED + "[AutoRefill] Đã TẮT tự động lấy đồ (Phím K)!");
        }
    }
}
