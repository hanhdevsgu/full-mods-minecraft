package com.minclient.module.impl;

import com.minclient.module.Module;

public class NoSlowModule extends Module {

    public NoSlowModule() {
        super("NoSlow", "Không bị giảm tốc độ khi ăn thức ăn hoặc kéo cung", Category.MOVEMENT, false);
    }
}
