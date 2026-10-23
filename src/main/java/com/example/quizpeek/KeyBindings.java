package com.example.quizpeek;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import org.lwjgl.input.Keyboard;

public final class KeyBindings {
    public static final KeyBinding OPEN_TIMING_CONFIG = new KeyBinding(
            "key.quizpeek.timingconfig",
            Keyboard.KEY_X,
            "key.categories.quizpeek"
    );

    private KeyBindings() {}

    public static void register() {
        ClientRegistry.registerKeyBinding(OPEN_TIMING_CONFIG);
    }
}
