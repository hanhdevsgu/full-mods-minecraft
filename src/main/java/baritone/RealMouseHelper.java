package baritone;

import java.awt.Robot;
import java.awt.event.InputEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class RealMouseHelper {
    private static Robot robot = null;
    private static boolean initialized = false;
    private static Class<?> mcClass = null;
    private static Method getMcMethod = null;
    private static Field rightClickDelayField = null;
    private static Field inGameHasFocusField = null;
    private static Field currentScreenField = null;
    private static Field gameSettingsField = null;
    private static Field pauseOnLostFocusField = null;
    private static Method rightClickMouseMethod = null;
    private static Method displayIsActiveMethod = null;
    private static Method mouseIsInsideWindowMethod = null;
    private static boolean lwjglInitialized = false;

    private static void initRobot() {
        if (initialized) {
            return;
        }
        initialized = true;
        try {
            robot = new Robot();
            robot.setAutoDelay(0);
            robot.setAutoWaitForIdle(false);
            System.out.println("[Baritone] Real mouse click helper initialized successfully!");
        } catch (Throwable throwable) {
            System.err.println("[Baritone] Failed to initialize Robot: " + throwable);
        }
    }

    private static void initReflection() {
        if (mcClass != null) {
            return;
        }
        try {
            String string;
            try {
                mcClass = Class.forName("net.minecraft.client.Minecraft");
            } catch (ClassNotFoundException classNotFoundException) {
                mcClass = Class.forName("bib");
            }
            for (Method object : mcClass.getDeclaredMethods()) {
                if (!Modifier.isStatic(object.getModifiers()) || object.getParameterCount() != 0 || object.getReturnType() != mcClass) continue;
                object.setAccessible(true);
                getMcMethod = object;
                break;
            }
            for (Field field : mcClass.getDeclaredFields()) {
                string = field.getName();
                Class<?> clazz = field.getType();
                if (clazz == Integer.TYPE && (string.equals("rightClickDelayTimer") || string.equals("field_71467_ac") || string.equals("as"))) {
                    field.setAccessible(true);
                    rightClickDelayField = field;
                    continue;
                }
                if (clazz == Boolean.TYPE && (string.equals("inGameHasFocus") || string.equals("field_71415_G") || string.equals("x"))) {
                    field.setAccessible(true);
                    inGameHasFocusField = field;
                    continue;
                }
                if (string.equals("currentScreen") || string.equals("field_71462_r") || string.equals("m")) {
                    field.setAccessible(true);
                    currentScreenField = field;
                    continue;
                }
                if (!string.equals("gameSettings") && !string.equals("field_71474_y") && !string.equals("t")) continue;
                field.setAccessible(true);
                gameSettingsField = field;
            }
            for (Method method : mcClass.getDeclaredMethods()) {
                if (method.getParameterCount() != 0 || method.getReturnType() != Void.TYPE || !(string = method.getName()).equals("rightClickMouse") && !string.equals("func_147121_ag") && !string.equals("aB")) continue;
                method.setAccessible(true);
                rightClickMouseMethod = method;
                break;
            }
        } catch (Throwable throwable) {
            System.err.println("[Baritone] Reflection init warning: " + throwable);
        }
        if (!lwjglInitialized) {
            Object object;
            lwjglInitialized = true;
            try {
                object = Class.forName("org.lwjgl.opengl.Display");
                displayIsActiveMethod = ((Class<?>) object).getMethod("isActive");
                displayIsActiveMethod.setAccessible(true);
            } catch (Throwable ignored) {
            }
            try {
                object = Class.forName("org.lwjgl.input.Mouse");
                mouseIsInsideWindowMethod = ((Class<?>) object).getMethod("isInsideWindow");
                mouseIsInsideWindowMethod.setAccessible(true);
            } catch (Throwable ignored) {
            }
        }
    }

    private static Object getMinecraftInstance() {
        RealMouseHelper.initReflection();
        if (mcClass == null || getMcMethod == null) {
            return null;
        }
        try {
            return getMcMethod.invoke(null);
        } catch (Throwable throwable) {
            return null;
        }
    }

    public static boolean isMinecraftActive() {
        try {
            Object object;
            boolean bl;
            Object object2;
            RealMouseHelper.initReflection();
            if (displayIsActiveMethod != null && (object2 = (Boolean) displayIsActiveMethod.invoke(null)) != null && !((Boolean) object2)) {
                return false;
            }
            if (mouseIsInsideWindowMethod != null && (object2 = (Boolean) mouseIsInsideWindowMethod.invoke(null)) != null && !((Boolean) object2)) {
                return false;
            }
            object2 = RealMouseHelper.getMinecraftInstance();
            if (object2 == null) {
                return false;
            }
            if (inGameHasFocusField != null && !(bl = inGameHasFocusField.getBoolean(object2))) {
                return false;
            }
            return currentScreenField == null || (object = currentScreenField.get(object2)) == null;
        } catch (Throwable throwable) {
            return false;
        }
    }

    public static boolean callRealMouse() {
        RealMouseHelper.initRobot();
        RealMouseHelper.resetRightClickDelayTimer();
        if (RealMouseHelper.isMinecraftActive() && robot != null) {
            try {
                robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
                robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
                return true;
            } catch (Throwable throwable) {
                System.err.println("[Baritone] Error during real mouse click: " + throwable);
            }
        }
        RealMouseHelper.tryInvokeRightClickMouse();
        return false;
    }

    public static boolean callRealMouseLeft() {
        RealMouseHelper.initRobot();
        if (RealMouseHelper.isMinecraftActive() && robot != null) {
            try {
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                return true;
            } catch (Throwable throwable) {
                System.err.println("[Baritone] Error during real mouse left press: " + throwable);
            }
        }
        return false;
    }

    public static void releaseRealMouseLeft() {
        if (robot != null) {
            try {
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            } catch (Throwable ignored) {
            }
        }
    }

    public static void releaseAll() {
        if (robot != null) {
            try {
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void resetRightClickDelayTimer() {
        try {
            Object object;
            Object object2 = RealMouseHelper.getMinecraftInstance();
            if (object2 == null) {
                return;
            }
            if (rightClickDelayField != null) {
                rightClickDelayField.setInt(object2, 0);
            }
            if (gameSettingsField != null && (object = gameSettingsField.get(object2)) != null) {
                if (pauseOnLostFocusField == null) {
                    for (Field field : object.getClass().getDeclaredFields()) {
                        String string = field.getName();
                        if (field.getType() != Boolean.TYPE || !string.equals("pauseOnLostFocus") && !string.equals("field_74355_t") && !string.equals("w") && !string.equals("t")) continue;
                        field.setAccessible(true);
                        pauseOnLostFocusField = field;
                        break;
                    }
                }
                if (pauseOnLostFocusField != null) {
                    pauseOnLostFocusField.setBoolean(object, false);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void tryInvokeRightClickMouse() {
        try {
            Object object = RealMouseHelper.getMinecraftInstance();
            if (object == null) {
                return;
            }
            if (rightClickMouseMethod != null) {
                rightClickMouseMethod.invoke(object);
            }
        } catch (Throwable ignored) {
        }
    }
}
