package com.autorefill;

import net.minecraft.item.ItemBlock;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class ChatHandler {

    @SubscribeEvent
    public void onClientChat(ClientChatEvent event) {
        if (!ConfigManager.enabled || !ConfigManager.autoDetectBaritone) return;

        String message = event.getMessage();
        if (message == null) return;
        message = message.trim();
        String lower = message.toLowerCase();

        // Kiem tra cac lenh Baritone
        String blockName = null;

        // Chuẩn hóa lệnh nếu dùng prefix Impact (.b ...)
        String normalized = lower;
        if (normalized.startsWith(".b ")) {
            normalized = "#" + normalized.substring(3).trim();
        }

        if (normalized.startsWith("#set set ") || normalized.startsWith(".set set ") || normalized.startsWith("@set set ")) {
            blockName = message.substring(message.toLowerCase().indexOf("set set ") + 8).trim();
        } else if (normalized.startsWith("#sel set ") || normalized.startsWith(".sel set ") || normalized.startsWith("@sel set ")) {
            blockName = message.substring(message.toLowerCase().indexOf("sel set ") + 8).trim();
        } else if (normalized.startsWith("#sel walls ") || normalized.startsWith(".sel walls ") || normalized.startsWith("@sel walls ")) {
            blockName = message.substring(message.toLowerCase().indexOf("sel walls ") + 10).trim();
        } else if (normalized.startsWith("#sel wall ") || normalized.startsWith(".sel wall ") || normalized.startsWith("@sel wall ")) {
            blockName = message.substring(message.toLowerCase().indexOf("sel wall ") + 9).trim();
        } else if (normalized.startsWith("#sel fill ") || normalized.startsWith(".sel fill ") || normalized.startsWith("@sel fill ")) {
            blockName = message.substring(message.toLowerCase().indexOf("sel fill ") + 9).trim();
        } else if (normalized.startsWith("#sel replace ") || normalized.startsWith(".sel replace ") || normalized.startsWith("@sel replace ")) {
            String[] parts = message.substring(message.toLowerCase().indexOf("sel replace ") + 12).trim().split("\\s+");
            if (parts.length >= 2) {
                blockName = parts[1];
            }
        } else if (normalized.startsWith("#set ") || normalized.startsWith(".set ") || normalized.startsWith("@set ")) {
            String arg = message.substring(message.toLowerCase().indexOf("set ") + 4).trim();
            if (!arg.isEmpty() && !isBaritoneInternalSetting(arg.toLowerCase())) {
                blockName = arg;
            }
        } else if (normalized.equals("#stop") || normalized.equals(".stop") || normalized.equals("@stop")
                || normalized.equals("#cancel") || normalized.equals(".cancel") || normalized.equals("@cancel")
                || normalized.equals("#sel clear") || normalized.equals(".sel clear") || normalized.equals("@sel clear")) {
            BaritoneIntegration.clearLastCommand();
            if (RefillHandler.hasTarget()) {
                RefillHandler.clearTarget();
                RefillHandler.sendMessage(TextFormatting.YELLOW + "[AutoRefill] " + TextFormatting.WHITE
                        + "Đã dừng theo dõi block mục tiêu.");
            }
            return;
        }

        if (blockName != null && !blockName.isEmpty()) {
            InventoryUtils.ParsedItem parsed = InventoryUtils.parseItemString(blockName);
            // CHI CHAP NHAN NEU LA BLOCK (khong phai vu khi hay do dung)
            if (parsed != null && parsed.item instanceof ItemBlock) {
                BaritoneIntegration.setLastCommand(message);
                RefillHandler.setTarget(parsed.item, parsed.meta, parsed.getDisplayName());
                RefillHandler.sendMessage(TextFormatting.GREEN + "[AutoRefill] " + TextFormatting.WHITE
                        + "Đã nhận diện lệnh Baritone: " + TextFormatting.YELLOW + message);
                RefillHandler.sendMessage(TextFormatting.GREEN + "[AutoRefill] " + TextFormatting.WHITE
                        + "Mục tiêu đặt block: " + TextFormatting.AQUA + parsed.getDisplayName()
                        + TextFormatting.WHITE + ". Mod sẽ chỉ lấy block này khi hotbar hết sạch!");
            }
        }
    }

    private boolean isBaritoneInternalSetting(String s) {
        return s.startsWith("allowinventory")
                || s.startsWith("buildinlayers")
                || s.startsWith("chatcontrol")
                || s.startsWith("rightclickspeed")
                || s.startsWith("blockspersecond")
                || s.startsWith("render")
                || s.startsWith("desktopnotifications");
    }
}
