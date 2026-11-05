package com.example.quizpeek;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;

public final class KeyBindings {
   public static final KeyBinding OPEN_TIMING_CONFIG = new KeyBinding("key.quizpeek.timingconfig", 45, "key.categories.quizpeek");

   private KeyBindings() {
   }

   public static void register() {
      ClientRegistry.registerKeyBinding(OPEN_TIMING_CONFIG);
   }
}
