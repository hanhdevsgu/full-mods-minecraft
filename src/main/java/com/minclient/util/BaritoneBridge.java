package com.minclient.util;

import com.minclient.MinClientMod;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class BaritoneBridge {
    private static Boolean available = null;

    public static boolean isAvailable() {
        if (available == null) {
            try {
                Class.forName("baritone.api.BaritoneAPI");
                available = true;
                MinClientMod.LOGGER.info("[MinClient] Đã phát hiện Baritone API trong môi trường game.");
            } catch (Throwable t) {
                available = false;
            }
        }
        return available;
    }

    /**
     * Gọi lệnh goto qua Baritone API
     * @return true nếu gọi thành công, false nếu cần fallback về MinClient nội bộ
     */
    public static boolean executeGoto(int x, int y, int z) {
        if (!isAvailable()) {
            return false;
        }

        try {
            // Cách 1: Gọi qua Baritone ICommandManager execute("goto <x> <y> <z>")
            Class<?> apiClass = Class.forName("baritone.api.BaritoneAPI");
            Method getProviderMethod = apiClass.getMethod("getProvider");
            Object provider = getProviderMethod.invoke(null);

            Method getPrimaryBaritoneMethod = provider.getClass().getMethod("getPrimaryBaritone");
            Object baritone = getPrimaryBaritoneMethod.invoke(provider);

            if (baritone != null) {
                Method getCommandManagerMethod = baritone.getClass().getMethod("getCommandManager");
                Object commandManager = getCommandManagerMethod.invoke(baritone);

                Method executeMethod = commandManager.getClass().getMethod("execute", String.class);
                String cmd = String.format("goto %d %d %d", x, y, z);
                executeMethod.invoke(commandManager, cmd);
                return true;
            }
        } catch (Throwable t) {
            MinClientMod.LOGGER.warn("[MinClient] Thử phương thức CustomGoalProcess của Baritone...");
            try {
                // Cách 2: Gọi qua getCustomGoalProcess().setGoalAndPath(new GoalBlock(x, y, z))
                Class<?> apiClass = Class.forName("baritone.api.BaritoneAPI");
                Method getProviderMethod = apiClass.getMethod("getProvider");
                Object provider = getProviderMethod.invoke(null);

                Method getPrimaryBaritoneMethod = provider.getClass().getMethod("getPrimaryBaritone");
                Object baritone = getPrimaryBaritoneMethod.invoke(provider);

                Method getCustomGoalProcessMethod = baritone.getClass().getMethod("getCustomGoalProcess");
                Object customGoalProcess = getCustomGoalProcessMethod.invoke(baritone);

                Class<?> goalBlockClass = Class.forName("baritone.api.pathing.goals.GoalBlock");
                Constructor<?> constructor = goalBlockClass.getConstructor(int.class, int.class, int.class);
                Object goal = constructor.newInstance(x, y, z);

                Class<?> goalClass = Class.forName("baritone.api.pathing.goals.Goal");
                Method setGoalAndPathMethod = customGoalProcess.getClass().getMethod("setGoalAndPath", goalClass);
                setGoalAndPathMethod.invoke(customGoalProcess, goal);
                return true;
            } catch (Throwable t2) {
                MinClientMod.LOGGER.error("[MinClient] Không thể gọi Baritone API goto", t2);
            }
        }
        return false;
    }

    /**
     * Dừng Baritone pathing nếu đang chạy
     */
    public static boolean cancelBaritone() {
        if (!isAvailable()) return false;
        try {
            Class<?> apiClass = Class.forName("baritone.api.BaritoneAPI");
            Method getProviderMethod = apiClass.getMethod("getProvider");
            Object provider = getProviderMethod.invoke(null);

            Method getPrimaryBaritoneMethod = provider.getClass().getMethod("getPrimaryBaritone");
            Object baritone = getPrimaryBaritoneMethod.invoke(provider);

            if (baritone != null) {
                Method getPathingBehaviorMethod = baritone.getClass().getMethod("getPathingBehavior");
                Object pathingBehavior = getPathingBehaviorMethod.invoke(baritone);

                Method cancelMethod = pathingBehavior.getClass().getMethod("cancel");
                cancelMethod.invoke(pathingBehavior);
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }
}
