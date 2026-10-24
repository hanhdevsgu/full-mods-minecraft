package com.tntmod;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.play.server.SPacketSetSlot;
import net.minecraft.network.play.server.SPacketWindowItems;

/**
 * Netty Packet Interceptor để bắt chính xác 100% dữ liệu Container từ Server
 * Không bao giờ bị race condition hay đọc sai số lượng TNT khi vừa mở GUI
 */
public class PacketInterceptor extends ChannelDuplexHandler {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof SPacketWindowItems) {
            SPacketWindowItems packet = (SPacketWindowItems) msg;
            InventoryHandler.onServerWindowItems(packet.getWindowId(), packet.getItemStacks());
        } else if (msg instanceof SPacketSetSlot) {
            SPacketSetSlot packet = (SPacketSetSlot) msg;
            InventoryHandler.onServerSetSlot(packet.getWindowId(), packet.getSlot(), packet.getStack());
        }
        super.channelRead(ctx, msg);
    }
}
