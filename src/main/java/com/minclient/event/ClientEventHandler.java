package com.minclient.event;

import com.minclient.MinClientMod;
import com.minclient.command.CommandManager;
import com.minclient.gui.ImpactClickGui;
import com.minclient.module.ModuleManager;
import com.minclient.pathfinding.MotorController;
import com.minclient.pathfinding.PathRenderer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.CPacketChatMessage;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.PlayerSPPushOutOfBlocksEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import org.lwjgl.input.Keyboard;

public class ClientEventHandler {
    private final ModuleManager moduleManager;
    private final CommandManager commandManager;
    private final MotorController motorController;

    public ClientEventHandler(ModuleManager moduleManager, CommandManager commandManager, MotorController motorController) {
        this.moduleManager = moduleManager;
        this.commandManager = commandManager;
        this.motorController = motorController;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            moduleManager.onTick();
            motorController.onTick();
        }
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        // Mở Impact ClickGUI khi nhấn phím RSHIFT (Right Shift)
        if (Keyboard.getEventKeyState() && Keyboard.getEventKey() == Keyboard.KEY_RSHIFT) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.currentScreen == null) {
                mc.displayGuiScreen(new ImpactClickGui(moduleManager));
            }
        }
    }

    /**
     * Tầng 1: Chặn triệt để tại ClientChatEvent với EventPriority.HIGHEST
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onClientChat(ClientChatEvent event) {
        String msg = event.getMessage();
        // TUYỆT ĐỐI HỦY GỬI LÊN SERVER nếu tin nhắn bắt đầu bằng '.' hoặc '#'
        if (msg != null && (msg.startsWith(".") || msg.startsWith("#"))) {
            event.setCanceled(true);
            event.setMessage(""); // Xóa rỗng để ngay cả khi có mod khác can thiệp cũng không gửi được
            commandManager.handleChat(msg);
        }
    }

    /**
     * Tầng 2: Chặn triệt để tại tầng Netty Pipeline Socket (Zero Packet Leak)
     * Bất kỳ gói tin CPacketChatMessage nào gửi bằng '.' hoặc '#' đều bị vứt bỏ trước khi chạm vào dây mạng
     */
    @SubscribeEvent
    public void onClientConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        try {
            Channel channel = event.getManager().channel();
            if (channel != null && channel.pipeline() != null) {
                if (channel.pipeline().get("minclient_packet_filter") == null) {
                    channel.pipeline().addBefore("packet_handler", "minclient_packet_filter", new ChannelDuplexHandler() {
                        @Override
                        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                            if (msg instanceof CPacketChatMessage) {
                                String content = ((CPacketChatMessage) msg).getMessage();
                                if (content != null && (content.startsWith(".") || content.startsWith("#"))) {
                                    // Chặn hoàn toàn ở mức Socket Netty, không bao giờ gửi ra Server
                                    Minecraft.getMinecraft().addScheduledTask(() -> commandManager.handleChat(content));
                                    return;
                                }
                            }
                            super.write(ctx, msg, promise);
                        }
                    });
                    MinClientMod.LOGGER.info("[MinClient] Đã cài đặt Netty Packet Filter (Chống rò rỉ lệnh chat lên Server 100%).");
                }
            }
        } catch (Throwable t) {
            MinClientMod.LOGGER.warn("[MinClient] Không thể cài đặt Netty packet filter: " + t.getMessage());
        }
    }

    @SubscribeEvent
    public void onPushOutOfBlocks(PlayerSPPushOutOfBlocksEvent event) {
        if (moduleManager.getNoPushModule().isEnabled()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (moduleManager.getPathRenderModule().isEnabled()) {
            PathRenderer.render(event, motorController);
        }
    }
}
