package com.minclient.module.impl;

import com.minclient.module.Module;
import com.minclient.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Timer;

import java.lang.reflect.Field;

public class TimerModule extends Module {
    private final NumberSetting speed = new NumberSetting("Speed", 1.2, 0.2, 5.0, 0.1);
    private Field timerField;
    private Field tickLengthField;

    public TimerModule() {
        super("Timer", "Thay đổi tốc độ game tick (chạy nhanh hoặc chậm toàn bộ thế giới)", Category.WORLD, false);
        addSetting(speed);
        initReflection();
    }

    private void initReflection() {
        try {
            try {
                timerField = Minecraft.class.getDeclaredField("timer");
            } catch (NoSuchFieldException e) {
                timerField = Minecraft.class.getDeclaredField("field_71428_T");
            }
            timerField.setAccessible(true);

            try {
                tickLengthField = Timer.class.getDeclaredField("tickLength");
            } catch (NoSuchFieldException e) {
                tickLengthField = Timer.class.getDeclaredField("field_194149_e");
            }
            tickLengthField.setAccessible(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onTick() {
        if (timerField != null && tickLengthField != null) {
            try {
                Timer t = (Timer) timerField.get(mc);
                if (t != null) {
                    float target = 50.0F / (float) speed.getValue().doubleValue();
                    tickLengthField.setFloat(t, target);
                }
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void onDisable() {
        if (timerField != null && tickLengthField != null) {
            try {
                Timer t = (Timer) timerField.get(mc);
                if (t != null) {
                    tickLengthField.setFloat(t, 50.0F);
                }
            } catch (Exception ignored) {
            }
        }
    }
}
