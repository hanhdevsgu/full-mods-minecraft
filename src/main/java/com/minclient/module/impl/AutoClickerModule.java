package com.minclient.module.impl;

import com.minclient.module.Module;
import com.minclient.setting.BooleanSetting;
import com.minclient.setting.NumberSetting;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Mouse;

public class AutoClickerModule extends Module {
    private final NumberSetting cps = new NumberSetting("CPS", 10.0, 1.0, 20.0, 1.0);
    private final BooleanSetting leftClick = new BooleanSetting("LeftClick", true);
    private final BooleanSetting rightClick = new BooleanSetting("RightClick", false);
    private long lastClickTime = 0;

    public AutoClickerModule() {
        super("AutoClicker", "Tự động nhấp chuột liên tục khi giữ chuột", Category.COMBAT, false);
        addSetting(cps);
        addSetting(leftClick);
        addSetting(rightClick);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.currentScreen != null) return;

        long now = System.currentTimeMillis();
        long delayMs = (long) (1000.0 / cps.getValue());

        if (now - lastClickTime < delayMs) return;

        if (leftClick.getValue() && Mouse.isButtonDown(0)) {
            KeyBinding.onTick(mc.gameSettings.keyBindAttack.getKeyCode());
            lastClickTime = now;
        } else if (rightClick.getValue() && Mouse.isButtonDown(1)) {
            KeyBinding.onTick(mc.gameSettings.keyBindUseItem.getKeyCode());
            lastClickTime = now;
        }
    }
}
