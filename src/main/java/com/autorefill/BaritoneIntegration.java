package com.autorefill;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.common.MinecraftForge;
import java.lang.reflect.Method;

public class BaritoneIntegration {

    private static Boolean baritonePresent = null;
    private static Method getProviderMethod = null;
    private static Method getPrimaryBaritoneMethod = null;
    private static Method getCommandManagerMethod = null;
    private static Method executeMethod = null;
    private static Method getBuilderProcessMethod = null;
    private static Method isPausedMethod = null;
    private static Method isActiveMethod = null;
    private static Method builderResumeMethod = null;
    private static Method builderPauseMethod = null;

    private static String lastCommand = null;

    public static void setLastCommand(String cmd) {
        if (cmd == null || cmd.trim().isEmpty()) {
            lastCommand = null;
            return;
        }
        String clean = cmd.trim();
        if (clean.startsWith("#") || clean.startsWith(".") || clean.startsWith("@")) {
            clean = clean.substring(1).trim();
        }
        lastCommand = clean;
    }

    public static void clearLastCommand() {
        lastCommand = null;
    }

    public static String getLastCommand() {
        return lastCommand;
    }

    private static void init() {
        if (baritonePresent != null) return;
        try {
            Class<?> apiClass = Class.forName("baritone.api.BaritoneAPI");
            getProviderMethod = apiClass.getMethod("getProvider");
            Object provider = getProviderMethod.invoke(null);
            if (provider == null) {
                baritonePresent = false;
                return;
            }

            getPrimaryBaritoneMethod = provider.getClass().getMethod("getPrimaryBaritone");
            Object primary = getPrimaryBaritoneMethod.invoke(provider);
            if (primary == null) {
                baritonePresent = false;
                return;
            }

            getCommandManagerMethod = primary.getClass().getMethod("getCommandManager");
            Object cmdManager = getCommandManagerMethod.invoke(primary);
            executeMethod = cmdManager.getClass().getMethod("execute", String.class);

            try {
                getBuilderProcessMethod = primary.getClass().getMethod("getBuilderProcess");
                Object builder = getBuilderProcessMethod.invoke(primary);
                if (builder != null) {
                    try {
                        isPausedMethod = builder.getClass().getMethod("isPaused");
                    } catch (Throwable ignored) {}
                    try {
                        isActiveMethod = builder.getClass().getMethod("isActive");
                    } catch (Throwable ignored) {}
                    try {
                        builderResumeMethod = builder.getClass().getMethod("resume");
                    } catch (Throwable ignored) {}
                    try {
                        builderPauseMethod = builder.getClass().getMethod("pause");
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}

            baritonePresent = true;
            System.out.println("[AutoRefill] Baritone API tích hợp thành công qua Reflection!");
        } catch (Throwable t) {
            baritonePresent = false;
            System.out.println("[AutoRefill] Không tìm thấy Baritone API trong classpath, sẽ sử dụng fallback qua lệnh chat (#resume).");
        }
    }

    /**
     * Tắt tính năng sprint (allowSprint = false) trong Baritone để khi Baritone di chuyển
     * chỉ đi bộ bình thường, không bao giờ tiêu hao thanh thức ăn (thanh feed).
     */
    public static void disableBaritoneSprint() {
        try {
            Class<?> apiClass = Class.forName("baritone.api.BaritoneAPI");
            Method getSettingsMethod = apiClass.getMethod("getSettings");
            Object settings = getSettingsMethod.invoke(null);
            if (settings != null) {
                try {
                    java.lang.reflect.Field allowSprintField = settings.getClass().getField("allowSprint");
                    Object allowSprintSetting = allowSprintField.get(settings);
                    if (allowSprintSetting != null) {
                        java.lang.reflect.Field valField = allowSprintSetting.getClass().getField("value");
                        valField.set(allowSprintSetting, false);
                    }
                } catch (Throwable ignored) {}
                try {
                    java.lang.reflect.Field sprintAscendsField = settings.getClass().getField("sprintAscends");
                    Object sprintAscendsSetting = sprintAscendsField.get(settings);
                    if (sprintAscendsSetting != null) {
                        java.lang.reflect.Field valField = sprintAscendsSetting.getClass().getField("value");
                        valField.set(sprintAscendsSetting, false);
                    }
                } catch (Throwable ignored) {}
                System.out.println("[AutoRefill] Đã tắt allowSprint trong Baritone -> Giữ nguyên thanh feed!");
            }
        } catch (Throwable t) {
            execute("set allowSprint false");
            execute("set sprintAscends false");
        }
    }

    public static boolean isBaritoneAvailable() {
        init();
        return Boolean.TRUE.equals(baritonePresent);
    }

    public static boolean isBuilderPaused() {
        init();
        if (isBaritoneAvailable() && getBuilderProcessMethod != null && isPausedMethod != null) {
            try {
                Object provider = getProviderMethod.invoke(null);
                Object primary = getPrimaryBaritoneMethod.invoke(provider);
                Object builder = getBuilderProcessMethod.invoke(primary);
                if (builder != null) {
                    return (Boolean) isPausedMethod.invoke(builder);
                }
            } catch (Throwable ignored) {}
        }
        return false;
    }

    public static boolean isBuilderActive() {
        init();
        if (isBaritoneAvailable() && getBuilderProcessMethod != null && isActiveMethod != null) {
            try {
                Object provider = getProviderMethod.invoke(null);
                Object primary = getPrimaryBaritoneMethod.invoke(provider);
                Object builder = getBuilderProcessMethod.invoke(primary);
                if (builder != null) {
                    return (Boolean) isActiveMethod.invoke(builder);
                }
            } catch (Throwable ignored) {}
        }
        return false;
    }

    /**
     * Tam dung Baritone truoc khi mo GUI /pv de tranh loi pathing hoac di chuyen bat thuong.
     */
    public static void pause() {
        init();
        if (isBaritoneAvailable() && executeMethod != null) {
            try {
                Object provider = getProviderMethod.invoke(null);
                Object primary = getPrimaryBaritoneMethod.invoke(provider);
                Object cmdManager = getCommandManagerMethod.invoke(primary);
                executeMethod.invoke(cmdManager, "pause");

                if (getBuilderProcessMethod != null && builderPauseMethod != null) {
                    Object builder = getBuilderProcessMethod.invoke(primary);
                    if (builder != null) {
                        builderPauseMethod.invoke(builder);
                    }
                }
                return;
            } catch (Throwable ignored) {}
        }

        // Fallback qua ClientChatEvent
        sendClientOnlyMessage("#pause");
    }

    /**
     * Tiep tuc Baritone sau khi da lay du block xuong hotbar.
     * Neu Baritone bi paused -> goi resume.
     * Neu Baritone bi mat state / bi huy han -> chay lai lastCommand (vi du: sel set stone).
     */
    public static void resume() {
        init();
        boolean resumedViaApi = false;

        if (isBaritoneAvailable() && executeMethod != null) {
            try {
                Object provider = getProviderMethod.invoke(null);
                Object primary = getPrimaryBaritoneMethod.invoke(provider);
                Object cmdManager = getCommandManagerMethod.invoke(primary);

                // 1. Goi lenh resume tren CommandManager cua Baritone
                executeMethod.invoke(cmdManager, "resume");

                // 2. Goi truc tiep tren builder process neu co
                if (getBuilderProcessMethod != null && builderResumeMethod != null) {
                    Object builder = getBuilderProcessMethod.invoke(primary);
                    if (builder != null) {
                        builderResumeMethod.invoke(builder);
                    }
                }

                resumedViaApi = true;

                // 3. Kiem tra xem Builder co dang active khong
                // Neu vi ly do nao do ma Baritone bi huy hoan toan thay vi pause, va ta co lastCommand
                if (getBuilderProcessMethod != null && isActiveMethod != null) {
                    Object builder = getBuilderProcessMethod.invoke(primary);
                    if (builder != null) {
                        boolean active = (Boolean) isActiveMethod.invoke(builder);
                        if (!active && lastCommand != null && !lastCommand.isEmpty()) {
                            // Chay lai lenh dat block goc (vd: "sel set cobblestone")
                            executeMethod.invoke(cmdManager, lastCommand);
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        if (!resumedViaApi) {
            // Fallback qua ClientChatEvent
            sendClientOnlyMessage("#resume");
        }
    }

    /**
     * Dat GoalBlock truc tiep vao CustomGoalProcess cua Baritone (ho tro #goto x y z)
     */
    public static boolean setBaritoneGoalDirect(int x, int y, int z) {
        try {
            Class<?> apiClass = Class.forName("baritone.api.BaritoneAPI");
            Object provider = apiClass.getMethod("getProvider").invoke(null);
            if (provider == null) return false;
            Object primary = provider.getClass().getMethod("getPrimaryBaritone").invoke(provider);
            if (primary == null) return false;

            Class<?> goalBlockClass = Class.forName("baritone.api.pathing.goals.GoalBlock");
            Object goal = goalBlockClass.getConstructor(int.class, int.class, int.class).newInstance(x, y, z);

            Object customGoal = primary.getClass().getMethod("getCustomGoalProcess").invoke(primary);
            if (customGoal != null) {
                Class<?> goalClass = Class.forName("baritone.api.pathing.goals.Goal");
                Method setGoalAndPath = customGoal.getClass().getMethod("setGoalAndPath", goalClass);
                setGoalAndPath.invoke(customGoal, goal);
                System.out.println("[BaritoneIntegration] Da dat GoalBlock truc tiep toi (" + x + ", " + y + ", " + z + ") qua Baritone API!");
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * Dat GoalXZ truc tiep vao CustomGoalProcess cua Baritone (ho tro #goto x z)
     */
    public static boolean setBaritoneGoalXZDirect(int x, int z) {
        try {
            Class<?> apiClass = Class.forName("baritone.api.BaritoneAPI");
            Object provider = apiClass.getMethod("getProvider").invoke(null);
            if (provider == null) return false;
            Object primary = provider.getClass().getMethod("getPrimaryBaritone").invoke(provider);
            if (primary == null) return false;

            Class<?> goalXZClass = Class.forName("baritone.api.pathing.goals.GoalXZ");
            Object goal = goalXZClass.getConstructor(int.class, int.class).newInstance(x, z);

            Object customGoal = primary.getClass().getMethod("getCustomGoalProcess").invoke(primary);
            if (customGoal != null) {
                Class<?> goalClass = Class.forName("baritone.api.pathing.goals.Goal");
                Method setGoalAndPath = customGoal.getClass().getMethod("setGoalAndPath", goalClass);
                setGoalAndPath.invoke(customGoal, goal);
                System.out.println("[BaritoneIntegration] Da dat GoalXZ truc tiep toi (" + x + ", " + z + ") qua Baritone API!");
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * Huy pathing cua Baritone truc tiep qua PathingBehavior (ho tro #stop)
     */
    public static boolean cancelBaritoneDirect() {
        try {
            Class<?> apiClass = Class.forName("baritone.api.BaritoneAPI");
            Object provider = apiClass.getMethod("getProvider").invoke(null);
            if (provider == null) return false;
            Object primary = provider.getClass().getMethod("getPrimaryBaritone").invoke(provider);
            if (primary == null) return false;

            Object pathingBehavior = primary.getClass().getMethod("getPathingBehavior").invoke(primary);
            if (pathingBehavior != null) {
                pathingBehavior.getClass().getMethod("cancel").invoke(pathingBehavior);
                System.out.println("[BaritoneIntegration] Da huy pathfinding truc tiep qua Baritone PathingBehavior!");
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * Goi MinClient / Impact command manager qua Reflection neu co trong classpath
     */
    public static boolean executeMinClientOrImpact(String rawCommand) {
        if (rawCommand == null || rawCommand.trim().isEmpty()) return false;
        String clean = rawCommand.trim();
        boolean handled = false;

        // 1. MinClient Reflection (https://github.com/hanhdevsgu/full-mods-minecraft)
        try {
            Class<?> minClientClass = Class.forName("com.minclient.MinClientMod");
            java.lang.reflect.Field instField = minClientClass.getField("INSTANCE");
            Object minClient = instField.get(null);
            if (minClient != null) {
                Method getCmdMgr = minClient.getClass().getMethod("getCommandManager");
                Object cmdMgr = getCmdMgr.invoke(minClient);
                if (cmdMgr != null) {
                    try {
                        Method handleChat = cmdMgr.getClass().getMethod("handleChat", String.class);
                        handleChat.invoke(cmdMgr, clean);
                        handled = true;
                    } catch (NoSuchMethodException e) {
                        Method execute = cmdMgr.getClass().getMethod("execute", String.class);
                        execute.invoke(cmdMgr, clean);
                        handled = true;
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 2. Impact Client Reflection
        if (!handled) {
            try {
                Class<?> impactClass = Class.forName("impact.Impact");
                Method getInstance = impactClass.getMethod("getInstance");
                Object impact = getInstance.invoke(null);
                if (impact != null) {
                    Method getCmdMgr = impact.getClass().getMethod("getCommandManager");
                    Object cmdMgr = getCmdMgr.invoke(impact);
                    if (cmdMgr != null) {
                        try {
                            Method execute = cmdMgr.getClass().getMethod("execute", String.class);
                            execute.invoke(cmdMgr, clean);
                            handled = true;
                        } catch (Throwable t2) {
                            Method handleChat = cmdMgr.getClass().getMethod("handleChat", String.class);
                            handleChat.invoke(cmdMgr, clean);
                            handled = true;
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        return handled;
    }

    /**
     * Chay bat ky lenh Baritone nao (khong can dau #)
     */
    public static void execute(String command) {
        if (command == null || command.trim().isEmpty()) return;
        init();
        String raw = command.trim();
        String clean = raw;
        if (clean.startsWith("#") || clean.startsWith(".") || clean.startsWith("@")) {
            clean = clean.substring(1).trim();
        }

        // 1. Neu la lenh goto -> thu parse toa do va goi GoalBlock / GoalXZ truc tiep
        if (clean.toLowerCase().startsWith("goto ")) {
            String[] parts = clean.split("\\s+");
            if (parts.length >= 4) {
                try {
                    int x = Integer.parseInt(parts[1]);
                    int y = Integer.parseInt(parts[2]);
                    int z = Integer.parseInt(parts[3]);
                    setBaritoneGoalDirect(x, y, z);
                } catch (Throwable ignored) {}
            } else if (parts.length == 3) {
                try {
                    int x = Integer.parseInt(parts[1]);
                    int z = Integer.parseInt(parts[2]);
                    setBaritoneGoalXZDirect(x, z);
                } catch (Throwable ignored) {}
            }
        } else if (clean.equalsIgnoreCase("stop") || clean.equalsIgnoreCase("cancel")) {
            cancelBaritoneDirect();
        }

        // 2. Goi CommandManager cua Baritone
        if (isBaritoneAvailable() && executeMethod != null) {
            try {
                Object provider = getProviderMethod.invoke(null);
                Object primary = getPrimaryBaritoneMethod.invoke(provider);
                Object cmdManager = getCommandManagerMethod.invoke(primary);
                executeMethod.invoke(cmdManager, clean);
                return;
            } catch (Throwable ignored) {}
        }

        // 3. Goi MinClient / Impact qua Reflection neu co
        if (executeMinClientOrImpact(raw)) {
            return;
        }

        // 4. Fallback qua ClientChatEvent (TUYET DOI KHONG chat len server)
        sendClientOnlyMessage(raw.startsWith("#") || raw.startsWith(".") ? raw : "#" + clean);
    }

    private static void sendClientOnlyMessage(String msg) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null) {
            ClientChatEvent event = new ClientChatEvent(msg);
            MinecraftForge.EVENT_BUS.post(event);
            // TUYET DOI KHONG goi mc.player.sendChatMessage(msg)!
        }
    }
}
