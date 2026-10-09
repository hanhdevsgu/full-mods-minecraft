package com.autorefill;

import net.minecraft.client.Minecraft;
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

        // Fallback qua chat client neu khong dung duoc API
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null) {
            mc.player.sendChatMessage("#pause");
        }
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
            // Fallback qua chat
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.player != null) {
                mc.player.sendChatMessage("#resume");
            }
        }
    }

    /**
     * Chay bat ky lenh Baritone nao (khong can dau #)
     */
    public static void execute(String command) {
        if (command == null || command.trim().isEmpty()) return;
        init();
        String clean = command.trim();
        if (clean.startsWith("#") || clean.startsWith(".") || clean.startsWith("@")) {
            clean = clean.substring(1).trim();
        }

        if (isBaritoneAvailable() && executeMethod != null) {
            try {
                Object provider = getProviderMethod.invoke(null);
                Object primary = getPrimaryBaritoneMethod.invoke(provider);
                Object cmdManager = getCommandManagerMethod.invoke(primary);
                executeMethod.invoke(cmdManager, clean);
                return;
            } catch (Throwable ignored) {}
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null) {
            mc.player.sendChatMessage("#" + clean);
        }
    }
}
