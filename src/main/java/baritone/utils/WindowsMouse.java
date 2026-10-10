package baritone.utils;

import baritone.RealMouseHelper;

public class WindowsMouse {

    public static void pressLeft() {
        RealMouseHelper.callRealMouseLeft();
    }

    public static void releaseLeft() {
        RealMouseHelper.releaseRealMouseLeft();
    }

    public static void clickRight() {
        RealMouseHelper.callRealMouse();
    }

    public static void releaseAll() {
        RealMouseHelper.releaseAll();
    }
}
