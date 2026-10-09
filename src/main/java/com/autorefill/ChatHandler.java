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

        if (lower.startsWith("#set set ") || lower.startsWith(".set set ") || lower.startsWith("@set set ")) {
            blockName = message.substring(9).trim();
        } else if (lower.startsWith("#sel set ") || lower.startsWith(".sel set ") || lower.startsWith("@sel set ")) {
            blockName = message.substring(9).trim();
        } else if (lower.startsWith("#sel walls ") || lower.startsWith(".sel walls ") || lower.startsWith("@sel walls ")) {
            blockName = message.substring(11).trim();
        } else if (lower.startsWith("#sel wall ") || lower.startsWith(".sel wall ") || lower.startsWith("@sel wall ")) {
            blockName = message.substring(10).trim();
        } else if (lower.startsWith("#sel fill ") || lower.startsWith(".sel fill ") || lower.startsWith("@sel fill ")) {
            blockName = message.substring(10).trim();
        } else if (lower.startsWith("#sel replace ") || lower.startsWith(".sel replace ") || lower.startsWith("@sel replace ")) {
            String[] parts = message.substring(13).trim().split("\\s+");
            if (parts.length >= 2) {
                blockName = parts[1];
            }
        } else if (lower.startsWith("#set ") || lower.startsWith(".set ") || lower.startsWith("@set ")) {
            // Neu nguoi choi go #set <block> (nhu #set cobblestone)
            String arg = message.substring(5).trim();
            if (!arg.isEmpty() && !isBaritoneInternalSetting(arg.toLowerCase())) {
                blockName = arg;
            }
        } else if (lower.equals("#stop") || lower.equals(".stop") || lower.equals("@stop")
                || lower.equals("#cancel") || lower.equals(".cancel") || lower.equals("@cancel")
                || lower.equals("#sel clear") || lower.equals(".sel clear") || lower.equals("@sel clear")) {
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
