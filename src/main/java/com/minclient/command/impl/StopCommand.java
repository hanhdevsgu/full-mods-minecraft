package com.minclient.command.impl;

import baritone.api.BaritoneAPI;
import com.minclient.command.Command;

public class StopCommand extends Command {

    public StopCommand() {
        super("stop", "Hủy bỏ di chuyển và dừng ngay lập tức (.stop hoặc #stop)", ".stop");
    }

    @Override
    public void execute(String[] args) {
        try {
            BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
            sendMessage("§a[Baritone] Đã dừng toàn bộ hành động di chuyển.");
        } catch (Throwable t) {
            try {
                Object cmdManager = BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager();
                java.lang.reflect.Method execMethod = cmdManager.getClass().getMethod("execute", String.class);
                execMethod.invoke(cmdManager, "stop");
                sendMessage("§a[Baritone] Đã dừng toàn bộ hành động di chuyển.");
            } catch (Throwable t2) {
                sendMessage("§c[Baritone] Lỗi khi dừng: " + t.getMessage());
            }
        }
    }
}
