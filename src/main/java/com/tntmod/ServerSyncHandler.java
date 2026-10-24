package com.tntmod;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CPacketCustomPayload;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

import java.nio.charset.StandardCharsets;

/**
 * Tự động 100% — In-Game Server Sync:
 * Đồng bộ hóa trực tiếp toàn bộ người chơi cài mod trên server play.toichoi.com
 * Không cần bất kỳ IP, Port, Radmin VPN hay thao tác cài đặt nào!
 */
public class ServerSyncHandler {

    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null && event.getEntity() == mc.player) {
            String name = mc.player.getName();
            if (name != null && !name.isEmpty() && !"player".equals(name)) {
                TeamSyncClient.updatePlayer(name);
            }
        }
    }
    public static final String CHANNEL_NAME = "TNTMOD_SYNC";
    // Tiền tố tàng hình tuyệt đối bằng mã màu Minecraft: §0§0§t§n§t
    public static final String CHAT_SYNC_PREFIX = "\u00a70\u00a70\u00a7t\u00a7n\u00a7t";

    /**
     * Tự động phát sóng tọa độ máy phân phát / rương tới tất cả người chơi cài mod trong server
     */
    public static void broadcastToServer(BlockPos pos, int dim, String type, int count) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.getConnection() == null) return;

        String playerName = mc.player.getName();
        // Cấu trúc gói: type;dim;x;y;z;count;player
        String payloadStr = type + ";" + dim + ";" + pos.getX() + ";" + pos.getY() + ";" + pos.getZ() + ";" + count + ";" + playerName;

        // 1. Kênh Plugin Channel của Minecraft Forge (an toàn, không lộ log chat)
        try {
            byte[] bytes = payloadStr.getBytes(StandardCharsets.UTF_8);
            ByteBuf buffer = Unpooled.wrappedBuffer(bytes);
            mc.getConnection().sendPacket(new CPacketCustomPayload(CHANNEL_NAME, new PacketBuffer(buffer)));
        } catch (Throwable ignored) {}
    }

    /**
     * Nhận gói tin Custom Payload từ server
     */
    @SubscribeEvent
    public void onCustomPayload(FMLNetworkEvent.ClientCustomPacketEvent event) {
        if (!CHANNEL_NAME.equals(event.getPacket().channel())) return;

        try {
            ByteBuf buf = event.getPacket().payload();
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            String payloadStr = new String(bytes, StandardCharsets.UTF_8);
            handleIncomingPayload(payloadStr);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    /**
     * Bắt gói tin chat ngầm: TỰ ĐỘNG TRIỆT TIÊU KHỎI MÀN HÌNH CHAT VÀ KÍCH HOẠT ESP ĐỎ!
     */
    @SubscribeEvent
    public void onChatReceived(ClientChatReceivedEvent event) {
        String unformatted = event.getMessage().getUnformattedText();
        String formatted = event.getMessage().getFormattedText();

        if (formatted.contains(CHAT_SYNC_PREFIX) || unformatted.contains("\u00a70\u00a70\u00a7t\u00a7n\u00a7t") || unformatted.contains("§0§0§t§n§t")) {
            // Hủy tin nhắn để KHÔNG HIỆN LÊN KHUNG CHAT của người chơi
            event.setCanceled(true);

            int idx = formatted.indexOf(CHAT_SYNC_PREFIX);
            if (idx == -1) {
                idx = unformatted.indexOf("§0§0§t§n§t");
            }
            if (idx != -1) {
                String full = formatted.contains(CHAT_SYNC_PREFIX) ? formatted : unformatted;
                String data = full.substring(idx + CHAT_SYNC_PREFIX.length()).trim();
                handleIncomingPayload(data);
            }
        }
    }

    private static void handleIncomingPayload(String payloadStr) {
        try {
            String[] p = payloadStr.split(";");
            if (p.length < 6) return;

            String type = p[0];
            if ("CLEAR_ALL".equals(type)) {
                BlockStateManager.onRemoteClear();
                return;
            }
            int dim = Integer.parseInt(p[1]);
            int x = Integer.parseInt(p[2]);
            int y = Integer.parseInt(p[3]);
            int z = Integer.parseInt(p[4]);
            int count = Integer.parseInt(p[5]);
            String sender = (p.length >= 7) ? p[6] : "teammate";

            Minecraft mc = Minecraft.getMinecraft();
            if (mc.player != null && sender.equalsIgnoreCase(mc.player.getName())) {
                // Không xử lý lại gói của chính mình
                return;
            }

            // CẬP NHẬT NGAY VÀO BỘ NHỚ -> MÀN HÌNH TỰ ĐỘNG CẬP NHẬT
            BlockStateManager.onRemoteUpdate(dim, x, y, z, type, count);

        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
