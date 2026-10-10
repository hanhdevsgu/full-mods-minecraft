package baritone.utils;

import java.awt.Robot;
import java.awt.event.InputEvent;

public final class WindowsMouse {

    private static Robot robot;
    private static volatile boolean leftButtonHeld = false;

    static {
        try {
            robot = new Robot();
            robot.setAutoDelay(0);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private WindowsMouse() {}

    /**
     * Nhấn giữ chuột trái thật của Windows (dùng cho đào block / break block)
     */
    public static synchronized void pressLeft() {
        if (robot != null && !leftButtonHeld) {
            try {
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                leftButtonHeld = true;
            } catch (Throwable ignored) {}
        }
    }

    /**
     * Nhả chuột trái thật của Windows
     */
    public static synchronized void releaseLeft() {
        if (robot != null && leftButtonHeld) {
            try {
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                leftButtonHeld = false;
            } catch (Throwable ignored) {}
        }
    }

    /**
     * Click chuột phải thật của Windows (dùng cho đặt block / place block)
     */
    public static synchronized void clickRight() {
        if (robot != null) {
            try {
                robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
                robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
            } catch (Throwable ignored) {}
        }
    }

    /**
     * Nhả toàn bộ phím chuột Windows
     */
    public static synchronized void releaseAll() {
        releaseLeft();
    }
}
