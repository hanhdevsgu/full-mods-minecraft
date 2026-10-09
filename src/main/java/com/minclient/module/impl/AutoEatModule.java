package com.minclient.module.impl;

import com.minclient.module.Module;
import com.minclient.setting.NumberSetting;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;

public class AutoEatModule extends Module {
    private final NumberSetting threshold = new NumberSetting("Hunger", 16.0, 1.0, 19.0, 1.0);
    private boolean isEating = false;
    private int prevSlot = -1;

    public AutoEatModule() {
        super("AutoEat", "Tự động ăn thức ăn trong túi đồ khi thanh đói giảm", Category.PLAYER, false);
        addSetting(threshold);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;

        if (mc.player.getFoodStats().getFoodLevel() <= threshold.getValue()) {
            int foodSlot = findFoodSlot();
            if (foodSlot != -1) {
                if (!isEating) {
                    prevSlot = mc.player.inventory.currentItem;
                    mc.player.inventory.currentItem = foodSlot;
                    KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
                    isEating = true;
                }
            }
        } else if (isEating) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
            if (prevSlot != -1) {
                mc.player.inventory.currentItem = prevSlot;
            }
            isEating = false;
        }
    }

    private int findFoodSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.inventory.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() instanceof ItemFood) {
                return i;
            }
        }
        return -1;
    }
}
