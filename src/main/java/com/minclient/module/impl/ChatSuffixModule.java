package com.minclient.module.impl;

import com.minclient.module.Module;
import com.minclient.setting.ModeSetting;

public class ChatSuffixModule extends Module {
    private final ModeSetting suffixMode = new ModeSetting("Suffix", "Impact", "Impact", "MinClient");

    public ChatSuffixModule() {
        super("ChatSuffix", "Tự động thêm chữ ký client vào cuối tin nhắn chat", Category.MISC, false);
        addSetting(suffixMode);
    }

    public String transformMessage(String message) {
        if (!isEnabled()) return message;
        if ("Impact".equalsIgnoreCase(suffixMode.getValue())) {
            return message + " \u2503 \u1D09\u1D0D\u1D18\u1D00\u1D04\u1D1B";
        } else {
            return message + " \u2503 MinClient";
        }
    }
}
