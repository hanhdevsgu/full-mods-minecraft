package com.minclient.event;

import com.minclient.MinClientMod;
import com.minclient.command.CommandManager;
import com.minclient.gui.ImpactClickGui;
import com.minclient.module.ModuleManager;
import com.minclient.util.CameraLockManager;
import com.minclient.util.ChatHistoryManager;
import com.minclient.util.LockableMouseHelper;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.CPacketChatMessage;
import net.minecraft.network.play.server.SPacketPlayerPosLook;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.PlayerSPPushOutOfBlocksEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import org.lwjgl.input.Keyboard;

public class ClientEventHandler {
    private final ModuleManager moduleManager;
    private final CommandManager commandManager;
    private boolean historyLoaded = false;

    public ClientEventHandler(ModuleManager moduleManager, CommandManager commandManager) {
        this.moduleManager = moduleManager;
        this.commandManager = commandManager;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        LockableMouseHelper.hook(mc);

        if (CameraLockManager.isLocked()) {
            if (mc.player != null && mc.player.isDead) {
                CameraLockManager.unlock("Nhân vật đã hồi sinh");
            } else {
                CameraLockManager.applyToPlayer();
            }
        }

        if (event.phase == TickEvent.Phase.END) {
            moduleManager.onTick();
            if (!historyLoaded && mc.world != null && mc.ingameGUI != null) {
                historyLoaded = true;
                ChatHistoryManager.init();
            }
        }
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        LockableMouseHelper.hook(mc);

        if (CameraLockManager.isLocked()) {
            if (mc.mouseHelper != null) {
                mc.mouseHelper.deltaX = 0;
                mc.mouseHelper.deltaY = 0;
            }
            CameraLockManager.applyToPlayer();
        }
    }

    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        if (CameraLockManager.isLocked()) {
            // Khi di chuyển chuột (không bấm phím chuột) trong game: chặn sự kiện để giữ cứng camera
            if (Minecraft.getMinecraft().currentScreen == null && event.getButton() == -1) {
                event.setCanceled(true);
            }
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
        if (msg != null) {
            // Luôn lưu vào lịch sử để khi ấn T + Mũi tên lên có thể xem lại
            ChatHistoryManager.record(msg);

            // Tự động mở khóa chuột khi gửi lệnh về nhà /home
            String lower = msg.trim().toLowerCase();
            if (lower.startsWith("/home")) {
                CameraLockManager.unlock("Về nhà /home");
            }

            // TUYỆT ĐỐI HỦY GỬI LÊN SERVER nếu tin nhắn bắt đầu bằng '.' hoặc '#'
            if (msg.startsWith(".") || msg.startsWith("#")) {
                event.setCanceled(true);
                event.setMessage("");
                commandManager.handleChat(msg);
            }
        }
    }

    /**
     * Tầng 2: Chặn triệt để tại tầng Netty Pipeline Socket (Zero Packet Leak)
     */
    @SubscribeEvent
    public void onClientConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        historyLoaded = false;
        Minecraft.getMinecraft().addScheduledTask(ChatHistoryManager::init);
        try {
            Channel channel = event.getManager().channel();
            if (channel != null && channel.pipeline() != null) {
                if (channel.pipeline().get("minclient_packet_filter") == null) {
                    channel.pipeline().addBefore("packet_handler", "minclient_packet_filter", new ChannelDuplexHandler() {
                        @Override
                        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                            if (msg instanceof CPacketChatMessage) {
                                String content = ((CPacketChatMessage) msg).getMessage();
                                if (content != null) {
                                    ChatHistoryManager.record(content);
                                    String lower = content.trim().toLowerCase();
                                    if (lower.startsWith("/home")) {
                                        Minecraft.getMinecraft().addScheduledTask(() -> CameraLockManager.unlock("Về nhà /home"));
                                    }
                                    if (content.startsWith(".") || content.startsWith("#")) {
                                        Minecraft.getMinecraft().addScheduledTask(() -> commandManager.handleChat(content));
                                        return;
                                    }
                                }
                            }
                            super.write(ctx, msg, promise);
                        }

                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                            if (msg instanceof SPacketPlayerPosLook) {
                                // Server dịch chuyển người chơi (hoàn tất /home hoặc teleport)
                                if (CameraLockManager.isLocked()) {
                                    Minecraft.getMinecraft().addScheduledTask(() -> CameraLockManager.unlock("Đã dịch chuyển /home"));
                                }
                            }
                            super.channelRead(ctx, msg);
                        }
                    });
                    MinClientMod.LOGGER.info("[MinClient] Đã kích hoạt bộ lọc Netty chống rò rỉ chat & tự mở khóa /home.");
                }
            }
        } catch (Throwable t) {
            MinClientMod.LOGGER.warn("[MinClient] Không thể cài đặt Netty packet filter: " + t.getMessage());
        }
    }

    @SubscribeEvent
    public void onClientDisconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        CameraLockManager.unlock("Rời server");
    }

    @SubscribeEvent
    public void onPushOutOfBlocks(PlayerSPPushOutOfBlocksEvent event) {
        if (moduleManager.getNoPushModule().isEnabled()) {
            event.setCanceled(true);
        }
    }
}
