package com.minclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiNewChat;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Quản lý lưu trữ và nạp lịch sử chat/lệnh.
 * Đảm bảo khi ấn phím T rồi mũi tên LÊN/XUỐNG luôn đọc được toàn bộ lệnh cũ,
 * kể cả sau khi khởi động lại game.
 */
public class ChatHistoryManager {
    private static final String HISTORY_FILE_NAME = "minclient_chat_history.txt";
    private static final int MAX_HISTORY_LINES = 500;
    private static boolean initialized = false;

    /**
     * Nạp lịch sử từ file vào GuiNewChat khi vào world hoặc khởi động.
     */
    public static synchronized void init() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.ingameGUI == null || mc.ingameGUI.getChatGUI() == null) {
            return;
        }

        File historyFile = getHistoryFile();
        if (!historyFile.exists()) {
            initialized = true;
            return;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(historyFile), StandardCharsets.UTF_8))) {
            GuiNewChat chatGui = mc.ingameGUI.getChatGUI();
            List<String> currentSent = chatGui.getSentMessages();
            String line;
            List<String> loadedLines = new ArrayList<>();

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    loadedLines.add(line);
                }
            }

            // Nạp các dòng cũ vào sentMessages của Minecraft
            for (String savedCmd : loadedLines) {
                if (!currentSent.contains(savedCmd)) {
                    currentSent.add(savedCmd);
                }
            }

            initialized = true;
        } catch (Throwable ignored) {
        }
    }

    /**
     * Ghi nhận một lệnh hoặc tin nhắn vào lịch sử (cả bộ nhớ game và lưu file đĩa).
     */
    public static synchronized void record(String message) {
        if (message == null) return;
        String trimmed = message.trim();
        if (trimmed.isEmpty()) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.ingameGUI != null && mc.ingameGUI.getChatGUI() != null) {
            GuiNewChat chatGui = mc.ingameGUI.getChatGUI();
            List<String> sent = chatGui.getSentMessages();
            if (sent.isEmpty() || !sent.get(sent.size() - 1).equals(trimmed)) {
                chatGui.addToSentMessages(trimmed);
            }
        }

        // Lưu vào file đĩa để không bao giờ bị mất khi khởi động lại Minecraft
        saveToFile(trimmed);
    }

    private static void saveToFile(String newEntry) {
        try {
            File file = getHistoryFile();
            List<String> lines = new ArrayList<>();
            if (file.exists()) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty()) {
                            lines.add(line);
                        }
                    }
                }
            }

            // Tránh lưu 2 dòng giống hệt nhau liên tiếp
            if (lines.isEmpty() || !lines.get(lines.size() - 1).equals(newEntry)) {
                lines.add(newEntry);
            }

            // Giới hạn số lượng dòng tối đa
            while (lines.size() > MAX_HISTORY_LINES) {
                lines.remove(0);
            }

            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
                for (String l : lines) {
                    writer.write(l);
                    writer.newLine();
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static File getHistoryFile() {
        Minecraft mc = Minecraft.getMinecraft();
        File dir = mc.gameDir != null ? mc.gameDir : new File(".");
        return new File(dir, HISTORY_FILE_NAME);
    }
}
