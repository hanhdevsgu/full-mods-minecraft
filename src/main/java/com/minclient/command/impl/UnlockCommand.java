package com.minclient.command.impl;

import com.minclient.command.Command;
import com.minclient.util.CameraLockManager;

public class UnlockCommand extends Command {

    public UnlockCommand() {
        super("unlock", "Mở khóa chuột và góc quay nhìn (.unlock)", ".unlock");
    }

    @Override
    public void execute(String[] args) {
        if (!CameraLockManager.isLocked()) {
            sendMessage("§e[MinClient] Chuột và góc nhìn hiện không bị khóa.");
            return;
        }
        CameraLockManager.unlock("Lệnh .unlock");
    }
}
