package com.zorotool.macrorecorder;

import com.autorefill.BaritoneIntegration;
import com.autorefill.SignAimHandler;
import com.example.quizpeek.GuiTimingConfig;
import com.example.quizpeek.KeyBindings;
import com.example.quizpeek.TooltipDumpHandler;
import com.zorotool.macrorecorder.gui.GuiAccountOrder;
import com.zorotool.macrorecorder.gui.GuiMacroConfig;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.network.play.client.CPacketChatMessage;
import net.minecraft.network.play.client.CPacketPlayer.Rotation;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.RayTraceResult.Type;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;
import net.minecraftforge.client.event.RenderGameOverlayEvent.Post;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent.KeyInputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.gameevent.TickEvent.RenderTickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent.ClientConnectedToServerEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@Mod(
   modid = "macrorecorder",
   version = "1.0"
)
public class MacroRecorderMod {
   public static final String MODID = "macrorecorder";
   public static final String VERSION = "1.0";
   private static final int MAX_STEPS_PER_TICK = 20;
   private static final int MAX_CHEST_HOPS_SAFETY = 2000;
   private static final int MIN_MOVE_TICKS_BEFORE_CHECK = 4;
   private static final int MOVE_TIMEOUT_TICKS_SAFETY = 200;
   private int npcInteractionAttempts = 0;
   private boolean npcRetryNeedsLook = false;
   private static final int TICKS_PER_SECOND = 20;
   private static long serverCommandCooldownUntil = 0L;
   private static MacroRecorderMod instance;
   private KeyBinding openGuiKey;
   private KeyBinding runMacroKey;
   private KeyBinding stopMacroKey;
   private KeyBinding sellMacroKey;
   private KeyBinding accountOrderGuiKey;
   private int maxVaults;
   private float moveDelaySeconds;
   private float shopDelaySeconds;
   private int chestRetryIntervalTicks;
   private int lookWaitTicks;
   private int npcRightClickInitialDelayTicks;
   private int npcClickRetryIntervalTicks;
   private double npcReachDistance;
   private int homeWaitTicks;
   private int beforeShopDelayTicks;
   private int shopToSethomeDelayTicks;
   private int afterShopClickDelayTicks;
   private int afterShopGotoWaitTicks;
   private String shopGotoCommand;
   private String shopLookCommand;
   private String afterShopGotoCommand;
   private String afterShopLookCommand;
   private String nextChestGotoCommand;
   private List<String> accountOrder = new ArrayList<>();
   private float queueDistanceThreshold;
   private int queueWaitLogTimer = 0;
   private static final int SELL_ITEMS_DONE_THRESHOLD = 16;
   private boolean sellMacroRunning = false;
   private MacroRecorderMod.SellState sellState = MacroRecorderMod.SellState.SELLING_1E_ITEMS;
   private int sellVaultNumber = 1;
   private int sellWaitTimer = 0;
   private int sellRoundInVault = 0;
   private int lastSellTakenCount = 0;
   private int sellClickCooldown = 0;
   private int notSellingTimer = 0;
   private int homeYStableTicks = 0;
   private MacroRecorderMod.SellState stateBeforeRecovery = MacroRecorderMod.SellState.SELLING_1E_ITEMS;
   private boolean onlySell1E = false;
   private boolean lastChestHadRemainingItems = false;
   private boolean currentVaultExhausted = false;
   private int shopY = 151;
   private int vaultsSoldThisSession = 0;
   private int targetVaultsToFill = -1;
   private volatile boolean teleportCompleteMessageReceived = false;
   private Vec3d shopTeleportOriginPos = null;
   private long lastSellMoneyTime = 0L;
   private boolean npcJumpActive = false;
   private boolean isRunning = false;
   private MacroRecorderMod.MacroState state = MacroRecorderMod.MacroState.OPENING_CHEST;
   private boolean macroPaused = false;
   private boolean preStartActive = false;
   private int preStartTimer = 0;
   private int chestIndex = 0;
   private boolean inventoryTransferActive = false;
   private boolean inventoryTransferTaking = false;
   private int inventoryTransferCursor = -1;
   private int inventoryTransferEnd = -1;
   private int inventoryTransferDelayTicks = 0;
   private int inventoryTransferWaitTicks = 0;
   private long inventoryTransferNextClickNanos = 0L;
   private int inventoryTransferCount = 0;
   private int vaultNumber = 1;
   private int depositRoundInVault = 0;
   private int roundInChest = 1;
   private boolean finishing = false;
   private int itemsTakenThisChest = 0;
   private int waitTimer = 0;
   private int stableLookTicks = 0;
   private boolean sethomeSentBeforeShop = false;
   private Minecraft mc;
   private int totalSugarCaneTaken = 0;
   private int hudStacksSoldSession = 0;
   private long hudSessionStartMillis = 0L;
   private long hudAccumulatedMillis = 0L;
   private boolean hudSessionActive = false;
   private static final int HUD_STACKS_PER_VAULT = 54;
   private static final int HUD_ITEMS_PER_STACK = 64;
   private static final int HUD_ITEMS_PER_SELL_UNIT = 16;
   private static final double HUD_PRICE_PER_SELL_UNIT = 70.0;

   public static MacroRecorderMod getInstance() {
      return instance;
   }

   @EventHandler
   public void preInit(FMLPreInitializationEvent event) {
      ConfigHelper.init(event);
   }

   @EventHandler
   public void init(FMLInitializationEvent event) {
      instance = this;
      MinecraftForge.EVENT_BUS.register(this);
      MinecraftForge.EVENT_BUS.register(new TooltipDumpHandler());
      KeyBindings.register();
      this.mc = Minecraft.getMinecraft();
      this.openGuiKey = new KeyBinding("Mở GUI cấu hình", 66, "Macro Recorder");
      this.runMacroKey = new KeyBinding("Chạy Macro", 67, "Macro Recorder");
      this.stopMacroKey = new KeyBinding("Dừng Macro", 68, "Macro Recorder");
      this.sellMacroKey = new KeyBinding("Chạy Macro Bán Mía", 62, "Macro Recorder");
      this.accountOrderGuiKey = new KeyBinding("Mở GUI thứ tự tài khoản (đa nick)", 22, "Macro Recorder");
      ClientRegistry.registerKeyBinding(this.openGuiKey);
      ClientRegistry.registerKeyBinding(this.runMacroKey);
      ClientRegistry.registerKeyBinding(this.stopMacroKey);
      ClientRegistry.registerKeyBinding(this.sellMacroKey);
      ClientRegistry.registerKeyBinding(this.accountOrderGuiKey);
      this.reloadConfig();
      System.out.println("[MacroRecorder] ========================================");
      System.out.println("[MacroRecorder] Mod đã khởi tạo xong!");
      System.out.println("[MacroRecorder] F8: Mở GUI cấu hình");
      System.out.println("[MacroRecorder] F9: Bắt đầu chạy Macro (lấy mía từ rương -> /pv)");
      System.out.println("[MacroRecorder] F4: Bắt đầu chạy Macro Bán Mía (rút /pv -> túi đồ -> bán)");
      System.out.println("[MacroRecorder] F10: Dừng Macro (dừng cái đang chạy, F9 hoặc F4)");
      System.out.println("[MacroRecorder] U: Mở GUI thứ tự tài khoản (đa nick chạy cùng lúc)");
      System.out.println("[MacroRecorder] ========================================");
   }

   public void reloadConfig() {
      ConfigHelper.reloadConfig();
      this.maxVaults = ConfigHelper.getVaults();
      this.moveDelaySeconds = ConfigHelper.getMoveDelaySeconds();
      this.inventoryTransferDelayTicks = millisecondsToTicks(ConfigHelper.getItemTransferDelayMs());
      this.shopDelaySeconds = ConfigHelper.getShopDelaySeconds();
      this.chestRetryIntervalTicks = secondsToTicks(ConfigHelper.getChestRetryIntervalSeconds());
      this.lookWaitTicks = secondsToTicks(ConfigHelper.getLookWaitSeconds());
      this.npcRightClickInitialDelayTicks = Math.min(secondsToTicks(ConfigHelper.getNpcRightClickInitialDelaySeconds()), secondsToTicks(0.5F));
      this.npcClickRetryIntervalTicks = Math.min(secondsToTicks(ConfigHelper.getNpcClickRetryIntervalSeconds()), secondsToTicks(0.3F));
      this.npcReachDistance = (double)ConfigHelper.getNpcReachDistance();
      this.homeWaitTicks = secondsToTicks(ConfigHelper.getHomeWaitSeconds());
      this.beforeShopDelayTicks = secondsToTicks(ConfigHelper.getBeforeShopDelaySeconds());
      this.shopToSethomeDelayTicks = secondsToTicks(ConfigHelper.getShopToSethomeDelaySeconds());
      this.afterShopClickDelayTicks = secondsToTicks(ConfigHelper.getAfterShopClickDelaySeconds());
      this.afterShopGotoWaitTicks = secondsToTicks(ConfigHelper.getAfterShopGotoWaitSeconds());
      this.shopGotoCommand = ConfigHelper.getShopGotoCommand();
      this.shopLookCommand = ConfigHelper.getShopLookCommand();
      this.afterShopGotoCommand = ConfigHelper.getAfterShopGotoCommand();
      this.afterShopLookCommand = ConfigHelper.getAfterShopLookCommand();
      this.nextChestGotoCommand = ConfigHelper.getNextChestGotoCommand();
      this.accountOrder = ConfigHelper.getAccountOrder();
      this.queueDistanceThreshold = ConfigHelper.getQueueDistanceThreshold();
   }

   private static int secondsToTicks(float seconds) {
      return Math.max(1, Math.round(seconds * 20.0F));
   }

   private static int millisecondsToTicks(int milliseconds) {
      return milliseconds <= 0 ? 0 : Math.max(1, (milliseconds + 49) / 50);
   }

   public void setConfig(int vaults, float moveDelaySeconds, float shopDelaySeconds, float afterShopClickDelaySeconds) {
      this.maxVaults = vaults;
      this.moveDelaySeconds = moveDelaySeconds;
      this.shopDelaySeconds = shopDelaySeconds;
      this.afterShopClickDelayTicks = secondsToTicks(afterShopClickDelaySeconds);
      ConfigHelper.setConfig(vaults, moveDelaySeconds, shopDelaySeconds, afterShopClickDelaySeconds);
   }

   @SubscribeEvent
   public void onRightClickBlock(RightClickBlock event) {
      if (this.sellMacroRunning || SignAimHandler.isAimLockActive) {
         Minecraft mc = Minecraft.getMinecraft();
         if (mc.world != null) {
            BlockPos pos = event.getPos();
            if (pos != null) {
               TileEntity te = mc.world.getTileEntity(pos);
               if (te instanceof TileEntitySign) {
                  TileEntitySign sign = (TileEntitySign)te;
                  if (SignAimHandler.isBuySign(sign)) {
                     event.setCanceled(true);
                     this.releaseUseItemKey();
                     System.out.println("[MacroRecorder] (Anti-Buy Shield) ĐÃ CHẶN chuột phải vào bảng Buy tại " + pos + "!");
                     return;
                  }

                  if (!SignAimHandler.isMatchingSellSign(sign, SignAimHandler.lastItemTarget)) {
                     event.setCanceled(true);
                     this.releaseUseItemKey();
                     System.out.println("[MacroRecorder] (Anti-Buy Shield) ĐÃ CHẶN chuột phải vào bảng không đúng món tại " + pos + "!");
                     return;
                  }

                  if (SignAimHandler.targetSignPos != null && !pos.equals(SignAimHandler.targetSignPos)) {
                     event.setCanceled(true);
                     this.releaseUseItemKey();
                     System.out.println("[MacroRecorder] (Anti-Buy Shield) ĐÃ CHẶN chuột phải vào bảng khác tọa độ tại " + pos + "!");
                     return;
                  }
               }
            }
         }
      }
   }

   @SubscribeEvent
   public void onEntityInteract(EntityInteract event) {
      if (this.sellMacroRunning
         && (this.sellState == MacroRecorderMod.SellState.SELLING_ITEMS || this.sellState == MacroRecorderMod.SellState.SELLING_1E_ITEMS)) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent
   public void onEntityInteractSpecific(net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteractSpecific event) {
      if (this.sellMacroRunning
         && (this.sellState == MacroRecorderMod.SellState.SELLING_ITEMS || this.sellState == MacroRecorderMod.SellState.SELLING_1E_ITEMS)) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent
   public void onClientChatReceived(ClientChatReceivedEvent event) {
      if (event != null && event.getMessage() != null) {
         String raw = event.getMessage().getUnformattedText();
         if (raw != null) {
            String clean = SignAimHandler.cleanText(raw).toLowerCase();
            if (clean.contains("dịch chuyển hoàn tất") || clean.contains("teleportation complete")) {
               this.teleportCompleteMessageReceived = true;
            }

            if ((clean.contains("8 lệnh") || clean.contains("8 lenh")) && (clean.contains("10 giây") || clean.contains("10 giay") || clean.contains("10s"))) {
               serverCommandCooldownUntil = System.currentTimeMillis() + 10000L;
               System.out.println("[MacroRecorder] Phát hiện server cảnh báo: '" + raw + "' -> Tự động nghỉ đúng 10s...");
            }

            if (this.sellMacroRunning
               && (this.sellState == MacroRecorderMod.SellState.SELLING_ITEMS || this.sellState == MacroRecorderMod.SellState.SELLING_1E_ITEMS)
               && (clean.contains("đã được cộng vào tài khoản") || clean.contains("cộng vào tài khoản"))) {
               this.lastSellMoneyTime = System.currentTimeMillis();
            }
         }
      }
   }

   @SubscribeEvent
   public void onClientConnected(ClientConnectedToServerEvent event) {
      try {
         Channel channel = event.getManager().channel();
         if (channel != null && channel.pipeline() != null && channel.pipeline().get("macro_packet_filter") == null) {
            channel.pipeline().addBefore("packet_handler", "macro_packet_filter", new ChannelDuplexHandler() {
               public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                  if (msg instanceof CPacketChatMessage) {
                     String content = ((CPacketChatMessage)msg).getMessage();
                     if (content != null && (content.startsWith(".") || content.startsWith("#"))) {
                        System.out.println("[MacroRecorder] (Netty Filter) Đã chặn lệnh client '" + content + "' không cho gửi lên server!");
                        return;
                     }
                  }

                  super.write(ctx, msg, promise);
               }
            });
            System.out.println("[MacroRecorder] Đã kích hoạt bộ lọc Netty 2 tầng chống rò rỉ chat client.");
         }
      } catch (Throwable var3) {
         System.out.println("[MacroRecorder] Không thể cài đặt Netty packet filter: " + var3.getMessage());
      }
   }

   @SubscribeEvent
   @SideOnly(Side.CLIENT)
   public void onClientTick(ClientTickEvent event) {
      if (event.phase == Phase.END) {
         if (this.mc != null && this.mc.player != null && this.mc.world != null) {
            if (this.isRunning || this.sellMacroRunning || this.preStartActive) {
               if (!this.macroPaused) {
                  if (this.preStartActive) {
                     this.processPreStartSequence();
                  } else {
                     if (this.isRunning) {
                        int steps = 0;

                        for (boolean keepGoing = true; keepGoing && steps < 20; steps++) {
                           keepGoing = this.processState();
                        }
                     } else {
                        int steps = 0;

                        for (boolean keepGoing = true; keepGoing && steps < 20; steps++) {
                           keepGoing = this.processSellState();
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @SubscribeEvent
   @SideOnly(Side.CLIENT)
   public void onRenderTick(RenderTickEvent event) {
      if (event.phase == Phase.END) {
         if (this.mc != null && this.mc.player != null && this.mc.world != null) {
            if (!this.macroPaused) {
               if (this.inventoryTransferActive) {
                  if (ConfigHelper.getItemTransferDelayMs() > 0) {
                     this.processInventoryTransfer();
                  }
               }
            }
         }
      }
   }

   private boolean processState() {
      switch (this.state) {
         case WAITING_START_LOOK:
            this.waitTimer++;
            if (this.waitTimer >= this.lookWaitTicks) {
               this.state = MacroRecorderMod.MacroState.OPENING_CHEST;
               return true;
            }

            return false;
         case OPENING_CHEST:
            this.openRealChest();
            this.waitTimer = 0;
            this.state = MacroRecorderMod.MacroState.WAITING_CHEST_OPEN;
            return false;
         case WAITING_CHEST_OPEN:
            if (this.mc.currentScreen instanceof GuiChest) {
               this.state = MacroRecorderMod.MacroState.TAKING_ITEMS;
               return true;
            }

            this.waitTimer++;
            if (this.waitTimer % this.chestRetryIntervalTicks == 0 && this.mc.currentScreen == null) {
               this.openRealChest();
            }

            return false;
         case TAKING_ITEMS:
            int taken = this.takeAllFromChestSuperFast();
            this.itemsTakenThisChest = taken;
            this.totalSugarCaneTaken += taken;
            this.lastChestHadRemainingItems = this.hasRemainingItemsInChest();
            System.out
               .println("[MacroRecorder] Rương #" + (this.chestIndex + 1) + ": lấy " + taken + " stack (rương còn đồ=" + this.lastChestHadRemainingItems + ")");
            this.closeCurrentScreen();
            this.state = MacroRecorderMod.MacroState.CHECK_AFTER_TAKE;
            return true;
         case CHECK_AFTER_TAKE:
            boolean isFull1E = this.isInventoryFullFarm();
            int effectiveTargetVaults = this.targetVaultsToFill >= 0 ? this.targetVaultsToFill : (this.maxVaults > 0 ? this.maxVaults : 1);
            if (isFull1E) {
               if (this.vaultNumber <= effectiveTargetVaults) {
                  System.out
                     .println(
                        "[MacroRecorder] Túi đồ đã đầy 1E đồ farm! Mở /pv "
                           + this.vaultNumber
                           + " (lần "
                           + (this.depositRoundInVault + 1)
                           + "/2) để cất vào kho (mục tiêu bù "
                           + effectiveTargetVaults
                           + " kho)..."
                     );
                  this.state = MacroRecorderMod.MacroState.OPENING_VAULT;
                  return true;
               }

               System.out.println("[MacroRecorder] ========================================");
               System.out.println("[MacroRecorder] >>> ĐÃ ĐẠT MỤC TIÊU: BÙ XONG " + effectiveTargetVaults + " KHO /PV + FULL 1E ĐỒ FARM TRÊN NGƯỜI!");
               System.out.println("[MacroRecorder] >>> Bắt đầu chuyển sang đi Shop...");
               System.out.println("[MacroRecorder] ========================================");
               this.finishing = true;
               this.state = MacroRecorderMod.MacroState.WAITING_BEFORE_SHOP;
               return true;
            } else {
               if (this.lastChestHadRemainingItems && this.itemsTakenThisChest > 0) {
                  System.out.println("[MacroRecorder] Túi đồ chưa full 1E nhưng rương vẫn còn đồ farm -> tiếp tục mở rương hiện tại...");
                  this.state = MacroRecorderMod.MacroState.OPENING_CHEST;
                  return true;
               }

               System.out
                  .println("[MacroRecorder] Rương đã cạn đồ farm nhưng túi đồ chưa full 1E -> #goto rương tiếp theo (" + this.nextChestGotoCommand + ")...");
               this.state = MacroRecorderMod.MacroState.MOVING_TO_NEXT_CHEST;
               return true;
            }
         case CLOSING_AFTER_TAKE:
            this.closeCurrentScreen();
            this.state = MacroRecorderMod.MacroState.OPENING_VAULT;
            return false;
         case OPENING_VAULT:
            if (!this.trySendCommand("/pv " + this.vaultNumber)) {
               return false;
            }

            this.waitTimer = 0;
            this.state = MacroRecorderMod.MacroState.WAITING_VAULT_OPEN;
            return false;
         case WAITING_VAULT_OPEN:
            if (this.mc.currentScreen instanceof GuiChest) {
               this.state = MacroRecorderMod.MacroState.DEPOSITING_ITEMS;
               return true;
            }

            this.waitTimer++;
            if (this.waitTimer % 40 == 0 && this.mc.currentScreen == null) {
               this.trySendCommand("/pv " + this.vaultNumber);
            }

            if (this.waitTimer > 200) {
               System.out.println("[MacroRecorder] Không mở được kho /pv " + this.vaultNumber + ", dừng macro.");
               this.stopMacro();
            }

            return false;
         case DEPOSITING_ITEMS:
            this.depositRoundInVault++;
            int moved = this.depositOnlyFarmItemsSuperFast();
            boolean vaultFull = this.isCurrentVaultFull();
            System.out
               .println(
                  "[MacroRecorder] Kho /pv "
                     + this.vaultNumber
                     + " (lần "
                     + this.depositRoundInVault
                     + "/2): đã đẩy "
                     + moved
                     + " stack thuốc súng vào kho (kho đầy="
                     + vaultFull
                     + ", moved="
                     + moved
                     + ")"
               );
            if (!vaultFull && this.depositRoundInVault < 2 && moved != 0) {
               System.out
                  .println("[MacroRecorder] Kho /pv " + this.vaultNumber + " mới mở lần 1 (chưa đầy) -> Đi lấy rương cho FULL 1E rồi mở lần 2 nhồi cho đầy!");
            } else {
               if (vaultFull) {
                  System.out.println("[MacroRecorder] Kho /pv " + this.vaultNumber + " đã ĐẦY (54/54 stack)! Sẽ chuyển sang kho /pv " + (this.vaultNumber + 1));
               } else if (moved == 0) {
                  System.out
                     .println(
                        "[MacroRecorder] Kho /pv " + this.vaultNumber + " không thể chứa thêm item (moved=0)! Sẽ chuyển sang kho /pv " + (this.vaultNumber + 1)
                     );
               } else {
                  System.out
                     .println("[MacroRecorder] Kho /pv " + this.vaultNumber + " đã mở đủ tối đa 2 lần -> Sẽ chuyển sang kho /pv " + (this.vaultNumber + 1));
               }

               this.vaultNumber++;
               this.depositRoundInVault = 0;
            }

            this.closeCurrentScreen();
            if (this.lastChestHadRemainingItems) {
               this.state = MacroRecorderMod.MacroState.OPENING_CHEST;
            } else {
               this.state = MacroRecorderMod.MacroState.MOVING_TO_NEXT_CHEST;
            }

            return false;
         case MOVING_TO_NEXT_CHEST:
            if (this.chestIndex + 1 > 2000) {
               System.out.println("[MacroRecorder] Đã goto quá 2000 rương liên tiếp (an toàn), dừng macro để kiểm tra.");
               this.stopMacro();
               return false;
            }

            executeClientCommand(this.nextChestGotoCommand);
            this.chestIndex++;
            System.out.println("[MacroRecorder] Di chuyển sang rương #" + (this.chestIndex + 1) + " (" + this.nextChestGotoCommand + ")...");
            this.waitTimer = 0;
            this.stableLookTicks = 0;
            this.state = MacroRecorderMod.MacroState.WAITING_MOVE;
            return false;
         case WAITING_MOVE:
            this.waitTimer++;
            if (this.waitTimer >= 4) {
               boolean arrived = this.isAtDestination(this.nextChestGotoCommand);
               if (!this.isLookingAtChest() && !arrived) {
                  this.stableLookTicks = 0;
               } else {
                  this.stableLookTicks++;
               }

               int requiredStableTicks = Math.max(1, Math.round(this.moveDelaySeconds * 20.0F));
               if (this.stableLookTicks >= requiredStableTicks) {
                  this.state = MacroRecorderMod.MacroState.OPENING_CHEST;
                  return true;
               }
            }

            if (this.waitTimer > 200) {
               System.out.println("[MacroRecorder] Di chuyển quá lâu mà không thấy rương kế tiếp (an toàn), dừng macro.");
               this.stopMacro();
            }

            return false;
         case WAITING_BEFORE_SHOP:
            this.waitTimer++;
            int requiredBeforeShopTicks = Math.max(80, this.beforeShopDelayTicks);
            if (this.waitTimer >= requiredBeforeShopTicks) {
               this.state = MacroRecorderMod.MacroState.OPENING_SHOP;
               return true;
            }

            return false;
         case OPENING_SHOP:
            String shopCommand = ConfigHelper.getShopCommand();
            if (!this.trySendCommand(shopCommand)) {
               return false;
            } else {
               this.teleportCompleteMessageReceived = false;
               if (this.shopToSethomeDelayTicks <= 0) {
                  if (this.mc.player != null && Math.abs(this.mc.player.posY - 151.0) > 4.0) {
                     String sethomeCommand = ConfigHelper.getSethomeCommand();
                     this.trySendCommand(sethomeCommand);
                     System.out.println("[MacroRecorder] (Delay = 0s) Đã gửi " + shopCommand + " và " + sethomeCommand + " CÙNG LÚC tại farm!");
                  }

                  this.waitTimer = 0;
                  this.state = MacroRecorderMod.MacroState.WAITING_SHOP_TELEPORT;
                  return false;
               }

               System.out
                  .println(
                     "[MacroRecorder] Đã đạt mục tiêu (Full PV + Full 1E)! Gửi "
                        + shopCommand
                        + ", chờ delay "
                        + (double)this.shopToSethomeDelayTicks / 20.0
                        + "s để /sethome tại farm..."
                  );
               this.waitTimer = 0;
               this.state = MacroRecorderMod.MacroState.WAITING_SETHOME_DELAY;
               return false;
            }
         case WAITING_SETHOME_DELAY:
            this.waitTimer++;
            int targetDelay = Math.min(this.shopToSethomeDelayTicks, 80);
            if (this.waitTimer < targetDelay) {
               return false;
            } else {
               if (this.mc.player != null && Math.abs(this.mc.player.posY - 151.0) > 4.0) {
                  String sethomeCommand = ConfigHelper.getSethomeCommand();
                  if (!this.trySendCommand(sethomeCommand)) {
                     return false;
                  }

                  System.out
                     .println(
                        "[MacroRecorder] Đang chờ /shop dịch chuyển (Y="
                           + (int)this.mc.player.posY
                           + " != 151), đã gửi "
                           + sethomeCommand
                           + " thành công tại farm!"
                     );
               } else {
                  System.out
                     .println(
                        "[MacroRecorder] Y="
                           + (this.mc.player != null ? (int)this.mc.player.posY : 0)
                           + " (gần 151 hoặc đã tới Truce territory), bỏ qua /sethome để tránh lỗi!"
                     );
               }

               this.waitTimer = 0;
               this.state = MacroRecorderMod.MacroState.WAITING_SHOP_TELEPORT;
               return false;
            }
         case WAITING_SHOP_TELEPORT:
            this.waitTimer++;
            BlockPos shopDest = this.parseGotoPos(this.shopGotoCommand);
            boolean arrivedAtShop = false;
            if (this.mc.player != null) {
               if (Math.abs(this.mc.player.posY - 150.0) <= 2.0 || Math.abs(this.mc.player.posY - 151.0) <= 2.0) {
                  arrivedAtShop = true;
               }

               if (shopDest != null) {
                  double dx = this.mc.player.posX - ((double)shopDest.getX() + 0.5);
                  double dz = this.mc.player.posZ - ((double)shopDest.getZ() + 0.5);
                  if (dx * dx + dz * dz <= 10000.0) {
                     arrivedAtShop = true;
                  }
               }
            }

            if (!arrivedAtShop && !this.teleportCompleteMessageReceived) {
               if (this.waitTimer >= 140) {
                  String cmd = ConfigHelper.getShopCommand();
                  System.out.println("[MacroRecorder] Chưa tới shop sau 7s, retry gửi lại " + cmd + "...");
                  this.trySendCommand(cmd);
                  this.waitTimer = 0;
                  this.state = MacroRecorderMod.MacroState.WAITING_SETHOME_DELAY;
               }

               return false;
            }

            System.out
               .println(
                  "[MacroRecorder] ĐÃ XÁC NHẬN TỚI SHOP THÀNH CÔNG! (Tọa độ X="
                     + (int)this.mc.player.posX
                     + ", Y="
                     + (int)this.mc.player.posY
                     + ", Z="
                     + (int)this.mc.player.posZ
                     + ") -> Chạy ngay tới quầy shop!"
               );
            this.teleportCompleteMessageReceived = false;
            this.waitTimer = 0;
            this.queueWaitLogTimer = 0;
            this.state = MacroRecorderMod.MacroState.WAITING_QUEUE_TURN;
            return true;
         case WAITING_SHOP_DELAY:
            this.queueWaitLogTimer = 0;
            this.state = MacroRecorderMod.MacroState.WAITING_QUEUE_TURN;
            return true;
         case WAITING_QUEUE_TURN:
            if (this.canProceedToShop()) {
               executeClientCommand(this.shopGotoCommand);
               System.out.println("[MacroRecorder] Gửi " + this.shopGotoCommand + " tới vị trí shop...");
               this.waitTimer = 0;
               this.state = MacroRecorderMod.MacroState.WAITING_LOOK_DELAY;
               return false;
            }

            this.queueWaitLogTimer++;
            if (this.queueWaitLogTimer % 20 == 0) {
               System.out.println("[MacroRecorder] (Hàng đợi) Chưa tới lượt goto/shop, đang chờ tài khoản phía trước...");
            }

            return false;
         case WAITING_LOOK_DELAY:
            this.waitTimer++;
            if (this.isAtDestination(this.shopGotoCommand) || this.waitTimer >= 300) {
               if (this.shopLookCommand != null && !this.shopLookCommand.trim().isEmpty()) {
                  executeClientCommand(this.shopLookCommand);
                  System.out.println("[MacroRecorder] Gửi lệnh nhìn NPC: " + this.shopLookCommand);
               }

               this.waitTimer = 0;
               this.state = MacroRecorderMod.MacroState.WAITING_NPC_GUI;
               this.npcInteractionAttempts = 0;
               this.npcRetryNeedsLook = false;
               this.npcJumpActive = false;
            }

            return false;
         case WAITING_NPC_GUI:
            if (this.mc.currentScreen instanceof GuiChest) {
               GuiChest chestGui = (GuiChest)this.mc.currentScreen;
               if (chestGui.inventorySlots instanceof ContainerChest) {
                  ContainerChest chest = (ContainerChest)chestGui.inventorySlots;
                  String title = chest.getLowerChestInventory().getDisplayName().getUnformattedText();
                  System.out.println("[MacroRecorder] Đã mở GUI NPC/shop: " + title);
                  TooltipDumpHandler.setAnswerClicked(false);
                  TooltipDumpHandler.armForNextNpcGui();
                  this.waitTimer = 0;
                  this.npcJumpActive = false;
                  this.state = MacroRecorderMod.MacroState.WAITING_AFTER_SHOP_CLICK_DELAY;
                  if (this.mc.gameSettings != null) {
                     KeyBinding.setKeyBindState(this.mc.gameSettings.keyBindJump.getKeyCode(), false);
                  }

                  return false;
               }
            }

            if (this.npcJumpActive) {
               if (this.mc.player != null && this.mc.player.onGround) {
                  this.npcJumpActive = false;
                  this.waitTimer = 0;
                  System.out.println("[MacroRecorder] Đã đáp đất an toàn sau cú nhảy click NPC.");
               }

               return false;
            }

            this.waitTimer++;
            int retryInterval = this.npcInteractionAttempts == 0 ? this.npcRightClickInitialDelayTicks : Math.max(12, this.npcClickRetryIntervalTicks);
            if (this.waitTimer >= retryInterval) {
               this.npcInteractionAttempts++;
               this.waitTimer = 0;
               boolean someoneBlocking = this.isPlayerBlockingNpc();
               if (someoneBlocking) {
                  if (this.mc.player != null && this.mc.player.onGround) {
                     this.mc.player.jump();
                     this.npcJumpActive = true;
                     TooltipDumpHandler.armForNextNpcGui();
                     boolean clicked = this.rightClickNpc();
                     System.out.println("[MacroRecorder] Có người chơi che trước mặt! Nhảy lên click NPC (interact=" + clicked + "), đợi đáp đất an toàn...");
                  }
               } else {
                  TooltipDumpHandler.armForNextNpcGui();
                  boolean clicked = this.rightClickNpc();
                  System.out.println("[MacroRecorder] Chuột phải NPC bình thường (attempt #" + this.npcInteractionAttempts + ", interact=" + clicked + ")...");
               }

               if (this.npcInteractionAttempts % 10 == 0 && this.shopLookCommand != null && !this.shopLookCommand.trim().isEmpty()) {
                  executeClientCommand(this.shopLookCommand);
                  System.out.println("[MacroRecorder] Chưa mở GUI sau " + this.npcInteractionAttempts + " lần click -> gửi lại: " + this.shopLookCommand);
               }
            }

            return false;
         case WAITING_AFTER_SHOP_CLICK_DELAY:
            boolean answerClicked = TooltipDumpHandler.hasClickedAnswer();
            boolean guiClosed = !(this.mc.currentScreen instanceof GuiContainer);
            if (answerClicked || guiClosed) {
               this.waitTimer++;
               if (this.waitTimer >= 20) {
                  if (this.mc.currentScreen instanceof GuiContainer) {
                     this.closeCurrentScreen();
                  }

                  TooltipDumpHandler.setAnswerClicked(false);
                  executeClientCommand(this.afterShopGotoCommand);
                  System.out.println("[MacroRecorder] Đã click đáp án NPC (chờ đúng 1s), gửi " + this.afterShopGotoCommand + " để rời khỏi khu shop...");
                  this.waitTimer = 0;
                  this.state = MacroRecorderMod.MacroState.WAITING_AFTER_SHOP_GOTO_DELAY;
               }
            }

            return false;
         case WAITING_AFTER_SHOP_GOTO_DELAY:
            this.waitTimer++;
            if (this.isAtDestination(this.afterShopGotoCommand) || this.waitTimer >= 200) {
               System.out.println("[MacroRecorder] Đã tới vị trí bán đồ sau shop, kích hoạt AutoAim và bắt đầu bán F4...");
               this.isRunning = false;
               this.resetAndStartSellMacro();
            }

            return false;
         default:
            return false;
      }
   }

   private boolean processSellState() {
      if (this.sellState != MacroRecorderMod.SellState.SENDING_HOME
         && this.sellState != MacroRecorderMod.SellState.WAITING_TELEPORT_HOME
         && this.sellState != MacroRecorderMod.SellState.WAITING_HOME_SETTLE
         && this.hasTrashInInventory()) {
         if (!this.onlySell1E) {
            this.onlySell1E = true;
            System.out
               .println("[MacroRecorder] (F4) CẢNH BÁO: Phát hiện túi đồ dính rác! Kích hoạt chế độ CHỈ BÁN HẾT INVENTORY rồi về nhà ngay, hủy toàn bộ /pv!");
         }

         if (this.sellState == MacroRecorderMod.SellState.OPENING_VAULT
            || this.sellState == MacroRecorderMod.SellState.WAITING_VAULT_OPEN
            || this.sellState == MacroRecorderMod.SellState.TAKING_ITEMS
            || this.sellState == MacroRecorderMod.SellState.CHECK_VAULT_DONE
            || this.sellState == MacroRecorderMod.SellState.SELLING_ITEMS) {
            this.closeCurrentScreen();
            this.releaseUseItemKey();
            SignAimHandler.isWarmupActive = false;
            this.sellState = MacroRecorderMod.SellState.SELLING_1E_ITEMS;
            this.lastSellMoneyTime = System.currentTimeMillis();
            this.notSellingTimer = 0;
         }
      }

      switch (this.sellState) {
         case SELLING_1E_ITEMS:
            if (this.countReedsInInventory() < 16) {
               System.out.println("[MacroRecorder] (F4) Trong 36 ô không còn thuốc súng để bán (hoặc đã bán sạch 1E)! Chuẩn bị gửi /home về nhà...");
               this.releaseUseItemKey();
               this.notSellingTimer = 0;
               if (this.onlySell1E) {
                  System.out
                     .println(
                        "[MacroRecorder] (F4) Chế độ dính rác: Đã bán hết đồ trên người! Chuẩn bị gửi /home về nhà (đã bán "
                           + this.vaultsSoldThisSession
                           + " kho /pv)..."
                     );
                  this.sellState = MacroRecorderMod.SellState.SENDING_HOME;
               } else {
                  System.out.println("[MacroRecorder] (F4) Bắt đầu mở các kho /pv 1 -> " + this.maxVaults + " để bán hết...");
                  this.sellVaultNumber = 1;
                  this.sellRoundInVault = 0;
                  this.sellWaitTimer = 0;
                  this.sellState = MacroRecorderMod.SellState.WAITING_NEXT_VAULT_DELAY;
               }

               return true;
            } else {
               boolean lookingAtBuySign1E = SignAimHandler.isLookingAtAnyBuySign(this.mc);
               boolean isNearTargetSign1E = this.mc.player != null && SignAimHandler.targetSignPos != null
                  && this.mc.player.getDistanceSq(SignAimHandler.targetSignPos) <= 20.25;
               boolean aimingAtSellSign1E = !lookingAtBuySign1E && SignAimHandler.isAimingAtSign(this.mc);
               boolean blockedByEntity1E = !lookingAtBuySign1E && isNearTargetSign1E && this.mc.objectMouseOver != null
                  && this.mc.objectMouseOver.typeOfHit == Type.ENTITY;

               if (aimingAtSellSign1E || blockedByEntity1E) {
                  this.holdUseItemKey();
               } else {
                  this.releaseUseItemKey();
               }

               if (!aimingAtSellSign1E && !blockedByEntity1E) {
                  this.notSellingTimer++;
               } else if (System.currentTimeMillis() - this.lastSellMoneyTime >= 2000L) {
                  this.notSellingTimer++;
               } else {
                  this.notSellingTimer = 0;
               }

               if (blockedByEntity1E && this.notSellingTimer >= 30 && this.notSellingTimer % 20 == 0 && this.mc.player != null && this.mc.player.onGround) {
                  this.mc.player.jump();
                  System.out.println("[MacroRecorder] (F4) Có người chơi che bảng -> Nhảy lên để nhìn qua đầu...");
               }

               if (this.notSellingTimer >= 140) {
                  System.out
                     .println(
                        "[MacroRecorder] (F4) Sell không cộng tiền hoặc bị che khuất tầm nhìn ("
                           + this.notSellingTimer
                           + " ticks)! Gửi "
                           + this.afterShopGotoCommand
                           + " để Baritone bước lại quầy bán..."
                     );
                  this.releaseUseItemKey();
                  executeClientCommand(this.afterShopGotoCommand);
                  this.stateBeforeRecovery = MacroRecorderMod.SellState.SELLING_1E_ITEMS;
                  this.sellState = MacroRecorderMod.SellState.RECOVERING_TO_COUNTER;
                  this.sellWaitTimer = 0;
                  this.notSellingTimer = 0;
                  this.lastSellMoneyTime = System.currentTimeMillis();
                  return false;
               }

               return false;
            }
         case OPENING_VAULT:
            this.releaseUseItemKey();
            long nowOpening = System.currentTimeMillis();
            if (nowOpening < serverCommandCooldownUntil) {
               this.sellWaitTimer++;
               if (this.sellWaitTimer % 20 == 0) {
                  long remSec = (serverCommandCooldownUntil - nowOpening) / 1000L + 1L;
                  System.out.println("[MacroRecorder] (F4) Server cảnh báo giới hạn lệnh trong chat -> Tạm nghỉ " + remSec + "s...");
               }

               return false;
            } else {
               if (!this.trySendCommand("/pv " + this.sellVaultNumber)) {
                  return false;
               }

               System.out.println("[MacroRecorder] (F4) Gửi lệnh /pv " + this.sellVaultNumber + "...");
               this.sellWaitTimer = 0;
               this.sellClickCooldown = 0;
               this.sellState = MacroRecorderMod.SellState.WAITING_VAULT_OPEN;
               SignAimHandler.isWarmupActive = true;
               SignAimHandler.stopAutoWalk(this.mc);
               return false;
            }
         case WAITING_VAULT_OPEN:
            this.releaseUseItemKey();
            if (this.mc.currentScreen instanceof GuiChest) {
               SignAimHandler.isWarmupActive = false;
               this.sellState = MacroRecorderMod.SellState.TAKING_ITEMS;
               return true;
            }

            this.sellWaitTimer++;
            int vaultRetryTicks = 130;
            if (this.sellWaitTimer % vaultRetryTicks == 0 && this.mc.currentScreen == null && System.currentTimeMillis() >= serverCommandCooldownUntil) {
               this.trySendCommand("/pv " + this.sellVaultNumber);
            }

            if (this.sellWaitTimer > 400) {
               System.out.println("[MacroRecorder] (F4) Không mở được kho /pv " + this.sellVaultNumber + ", dừng macro bán.");
               SignAimHandler.isWarmupActive = false;
               this.stopSellMacro();
            }

            return false;
         case TAKING_ITEMS:
            this.releaseUseItemKey();
            SignAimHandler.isWarmupActive = false;
            int taken = this.takeAllSugarCaneSuperFast();
            boolean remainingInVault = this.hasRemainingFarmItemsInVault();
            this.currentVaultExhausted = !remainingInVault;
            this.closeCurrentScreen();
            if (taken == 0) {
               this.currentVaultExhausted = true;
               this.vaultsSoldThisSession = this.sellVaultNumber;
               System.out
                  .println(
                     "[MacroRecorder] (F4) Kho /pv " + this.sellVaultNumber + " không có item nào! Đã đóng GUI và tính là đã bán xong (sẽ bù khi về nhà)."
                  );
               if (this.sellVaultNumber >= this.maxVaults) {
                  System.out.println("[MacroRecorder] (F4) ĐÃ KIỂM TRA HẾT TOÀN BỘ " + this.maxVaults + " KHO! Chuẩn bị gửi /home về nhà...");
                  this.sellState = MacroRecorderMod.SellState.SENDING_HOME;
                  return true;
               }

               this.sellVaultNumber++;
               this.sellRoundInVault = 0;
               this.currentVaultExhausted = false;
               this.lastSellTakenCount = 0;
               this.sellWaitTimer = 0;
               this.sellState = MacroRecorderMod.SellState.WAITING_NEXT_VAULT_DELAY;
               return true;
            }

            if (this.currentVaultExhausted) {
               System.out
                  .println(
                     "[MacroRecorder] (F4) Kho /pv "
                        + this.sellVaultNumber
                        + " đã được rút HẾT SẠCH thuốc súng (không còn stack nào)! Bán xong túi này sẽ không mở lại nữa."
                  );
            }

            this.lastSellTakenCount = taken;
            this.totalSugarCaneTaken += taken;
            this.hudStacksSoldSession += taken;
            this.sellRoundInVault++;
            if (this.sellVaultNumber > this.vaultsSoldThisSession) {
               this.vaultsSoldThisSession = this.sellVaultNumber;
            }

            System.out
               .println("[MacroRecorder] (F4) Kho /pv " + this.sellVaultNumber + " (lần " + this.sellRoundInVault + "/2): rút " + taken + " stack thuốc súng");
            this.sellClickCooldown = 0;
            this.sellState = MacroRecorderMod.SellState.SELLING_ITEMS;
            this.lastSellMoneyTime = System.currentTimeMillis();
            this.notSellingTimer = 0;
            return true;
         case SELLING_ITEMS:
            if (this.countReedsInInventory() < 16) {
               this.releaseUseItemKey();
               this.notSellingTimer = 0;
               if (this.onlySell1E) {
                  System.out.println("[MacroRecorder] (F4) Trong 36 ô không còn thuốc súng để bán (do dính rác)! Gửi /home về nhà ngay...");
                  this.vaultsSoldThisSession = this.sellVaultNumber;
                  this.sellState = MacroRecorderMod.SellState.SENDING_HOME;
               } else {
                  this.sellState = MacroRecorderMod.SellState.CHECK_VAULT_DONE;
               }

               return true;
            } else {
               boolean lookingAtBuySign = SignAimHandler.isLookingAtAnyBuySign(this.mc);
               boolean isNearTargetSign = this.mc.player != null && SignAimHandler.targetSignPos != null
                  && this.mc.player.getDistanceSq(SignAimHandler.targetSignPos) <= 20.25;
               boolean aimingAtSellSign = !lookingAtBuySign && SignAimHandler.isAimingAtSign(this.mc);
               boolean blockedByEntity = !lookingAtBuySign && isNearTargetSign && this.mc.objectMouseOver != null
                  && this.mc.objectMouseOver.typeOfHit == Type.ENTITY;

               if (aimingAtSellSign || blockedByEntity) {
                  this.holdUseItemKey();
               } else {
                  this.releaseUseItemKey();
               }

               if (!aimingAtSellSign && !blockedByEntity) {
                  this.notSellingTimer++;
               } else if (System.currentTimeMillis() - this.lastSellMoneyTime >= 2000L) {
                  this.notSellingTimer++;
               } else {
                  this.notSellingTimer = 0;
               }

               if (blockedByEntity && this.notSellingTimer >= 30 && this.notSellingTimer % 20 == 0 && this.mc.player != null && this.mc.player.onGround) {
                  this.mc.player.jump();
                  System.out.println("[MacroRecorder] (F4) Có người chơi che bảng -> Nhảy lên để nhìn qua đầu...");
               }

               if (this.notSellingTimer >= 140) {
                  System.out
                     .println(
                        "[MacroRecorder] (F4) Sell không cộng tiền hoặc bị che khuất tầm nhìn ("
                           + this.notSellingTimer
                           + " ticks)! Gửi "
                           + this.afterShopGotoCommand
                           + " để Baritone bước lại quầy bán..."
                     );
                  this.releaseUseItemKey();
                  executeClientCommand(this.afterShopGotoCommand);
                  this.stateBeforeRecovery = MacroRecorderMod.SellState.SELLING_ITEMS;
                  this.sellState = MacroRecorderMod.SellState.RECOVERING_TO_COUNTER;
                  this.sellWaitTimer = 0;
                  this.notSellingTimer = 0;
                  this.lastSellMoneyTime = System.currentTimeMillis();
                  return false;
               }

               return false;
            }
         case RECOVERING_TO_COUNTER:
            this.releaseUseItemKey();
            this.sellWaitTimer++;
            BlockPos counterTarget = this.parseGotoPos(this.afterShopGotoCommand);
            double distSqToCenter = 999.0;
            if (counterTarget != null && this.mc.player != null) {
               double ddx = this.mc.player.posX - ((double)counterTarget.getX() + 0.5);
               double ddz = this.mc.player.posZ - ((double)counterTarget.getZ() + 0.5);
               distSqToCenter = ddx * ddx + ddz * ddz;
            }

            boolean atCenter = distSqToCenter <= 0.64;
            boolean canAimAgain = SignAimHandler.isAimingAtSign(this.mc) && !SignAimHandler.isLookingAtAnyBuySign(this.mc);
            if (this.sellWaitTimer >= 8 && (atCenter || canAimAgain || this.sellWaitTimer >= 60)) {
               executeClientCommand("#stop");
               if (this.mc.gameSettings != null) {
                  KeyBinding.setKeyBindState(this.mc.gameSettings.keyBindForward.getKeyCode(), false);
                  KeyBinding.setKeyBindState(this.mc.gameSettings.keyBindBack.getKeyCode(), false);
                  KeyBinding.setKeyBindState(this.mc.gameSettings.keyBindLeft.getKeyCode(), false);
                  KeyBinding.setKeyBindState(this.mc.gameSettings.keyBindRight.getKeyCode(), false);
                  KeyBinding.setKeyBindState(this.mc.gameSettings.keyBindJump.getKeyCode(), false);
                  KeyBinding.setKeyBindState(this.mc.gameSettings.keyBindSprint.getKeyCode(), false);
               }

               if (this.afterShopLookCommand != null && !this.afterShopLookCommand.isEmpty()) {
                  executeClientCommand(this.afterShopLookCommand);
               }

               int farmId = ConfigHelper.getFarmItemId();
               String target = farmId == 338 ? "reeds" : "gunpowder";
               SignAimHandler.startAimLock(target);
               System.out.println("[MacroRecorder] (F4) Đã thoát góc chết về lại sát quầy bán đồ! Tiếp tục bán...");
               this.notSellingTimer = 0;
               this.sellWaitTimer = 0;
               this.lastSellMoneyTime = System.currentTimeMillis();
               this.sellState = this.stateBeforeRecovery;
            }

            return false;
         case CHECK_VAULT_DONE:
            this.releaseUseItemKey();
            if (this.onlySell1E) {
               System.out.println("[MacroRecorder] (F4) Chế độ chỉ bán 1E (do dính rác): Dừng mở kho /pv, chuẩn bị gửi /home về nhà...");
               this.sellState = MacroRecorderMod.SellState.SENDING_HOME;
               return true;
            } else {
               this.vaultsSoldThisSession = this.sellVaultNumber;
               if (!this.currentVaultExhausted && this.sellRoundInVault < 2 && this.lastSellTakenCount != 0) {
                  System.out.println("[MacroRecorder] (F4) Kho /pv " + this.sellVaultNumber + " vẫn còn đồ -> Mở lần 2 để rút nốt...");
                  this.sellWaitTimer = 0;
                  this.sellState = MacroRecorderMod.SellState.WAITING_NEXT_VAULT_DELAY;
                  return false;
               } else {
                  if (this.sellVaultNumber >= this.maxVaults) {
                     System.out.println("[MacroRecorder] (F4) ĐÃ BÁN XONG TOÀN BỘ CÁC KHO /PV + 1E (KHO ĐÃ HẾT ĐỒ)! Chuẩn bị gửi /home về nhà...");
                     this.sellState = MacroRecorderMod.SellState.SENDING_HOME;
                     return true;
                  }

                  this.sellVaultNumber++;
                  this.sellRoundInVault = 0;
                  this.currentVaultExhausted = false;
                  this.lastSellTakenCount = 0;
                  this.sellWaitTimer = 0;
                  this.sellState = MacroRecorderMod.SellState.WAITING_NEXT_VAULT_DELAY;
                  return false;
               }
            }
         case WAITING_NEXT_VAULT_DELAY:
            this.releaseUseItemKey();
            this.sellWaitTimer++;
            long nowWait = System.currentTimeMillis();
            if (nowWait < serverCommandCooldownUntil) {
               if (this.sellWaitTimer % 20 == 0) {
                  long remSec = (serverCommandCooldownUntil - nowWait) / 1000L + 1L;
                  System.out.println("[MacroRecorder] (F4) Server cảnh báo giới hạn lệnh trong chat -> Tạm nghỉ " + remSec + "s...");
               }

               return false;
            } else {
               if (this.sellWaitTimer < 20) {
                  return false;
               }

               this.sellWaitTimer = 0;
               this.sellState = MacroRecorderMod.SellState.OPENING_VAULT;
               return true;
            }
         case SENDING_HOME:
            this.releaseUseItemKey();
            String homeMiaCommand = ConfigHelper.getHomeMiaCommand();
            if (!this.trySendCommand(homeMiaCommand)) {
               return false;
            }

            try {
               SignAimHandler.stopAimLock(this.mc);
            } catch (Exception var20) {
            }

            SignAimHandler.isWarmupActive = true;
            SignAimHandler.stopAutoWalk(this.mc);
            this.shopY = (int)this.mc.player.posY;
            this.shopTeleportOriginPos = new Vec3d(this.mc.player.posX, this.mc.player.posY, this.mc.player.posZ);
            this.teleportCompleteMessageReceived = false;
            System.out
               .println(
                  "[MacroRecorder] (F4) Đã gửi "
                     + homeMiaCommand
                     + " (Shop X="
                     + (int)this.shopTeleportOriginPos.x
                     + ", Y="
                     + this.shopY
                     + ", Z="
                     + (int)this.shopTeleportOriginPos.z
                     + "), chờ server đếm 5s về nhà..."
               );
            this.sellWaitTimer = 0;
            this.homeYStableTicks = 0;
            this.sellState = MacroRecorderMod.SellState.WAITING_TELEPORT_HOME;
            return false;
         case WAITING_TELEPORT_HOME:
            this.sellWaitTimer++;
            if (this.mc.gameSettings != null) {
               KeyBinding.setKeyBindState(this.mc.gameSettings.keyBindForward.getKeyCode(), false);
               KeyBinding.setKeyBindState(this.mc.gameSettings.keyBindBack.getKeyCode(), false);
               KeyBinding.setKeyBindState(this.mc.gameSettings.keyBindLeft.getKeyCode(), false);
               KeyBinding.setKeyBindState(this.mc.gameSettings.keyBindRight.getKeyCode(), false);
               KeyBinding.setKeyBindState(this.mc.gameSettings.keyBindJump.getKeyCode(), false);
               KeyBinding.setKeyBindState(this.mc.gameSettings.keyBindSprint.getKeyCode(), false);
            }

            if (this.sellWaitTimer >= 140) {
               boolean isStillAtShop = this.mc.player != null && Math.abs(this.mc.player.posY - 151.0) <= 2.0;
               if (!isStillAtShop) {
                  System.out
                     .println(
                        "[MacroRecorder] (Home) Đã qua 7s, Y hiện tại ("
                           + (this.mc.player != null ? (int)this.mc.player.posY : 0)
                           + ") khác 151 -> Đã về nhà thành công! Chuẩn bị xả rác và lấy đồ..."
                     );
                  this.sellWaitTimer = 0;
                  this.homeYStableTicks = 0;
                  this.teleportCompleteMessageReceived = false;
                  this.sellState = MacroRecorderMod.SellState.WAITING_HOME_SETTLE;
                  return false;
               }

               String cmd = ConfigHelper.getHomeMiaCommand();
               System.out.println("[MacroRecorder] (Home) Đã qua 7s nhưng Y vẫn là 151 (chưa về được nhà), retry gửi lại " + cmd + "...");
               this.trySendCommand(cmd);
               this.sellWaitTimer = 0;
               this.homeYStableTicks = 0;
            }

            return false;
         case WAITING_HOME_SETTLE:
            this.sellWaitTimer++;
            if ((this.sellWaitTimer == 5 || this.sellWaitTimer == 15) && this.hasTrashInInventory()) {
               int dropped = this.dropAllTrashFromInventory();
               System.out.println("[MacroRecorder] (Home) Đã Q vứt " + dropped + " stack rác ra ngoài đất!");
            }

            if (this.sellWaitTimer >= 30) {
               if (this.hasTrashInInventory()) {
                  this.dropAllTrashFromInventory();
               }

               System.out.println("[MacroRecorder] (Home) Đã qua 1.5s ổn định tại nhà, túi đồ đã sạch sẽ! Bắt đầu chu trình lấy đồ...");
               SignAimHandler.isWarmupActive = false;
               this.onlySell1E = false;
               this.completeSellMacro();
               this.startMacro();
            }

            return false;
         default:
            return false;
      }
   }

   private boolean isFarmItem(ItemStack stack) {
      if (stack != null && !stack.isEmpty()) {
         int configuredId = ConfigHelper.getFarmItemId();
         Item configured = Item.getItemById(configuredId);
         if (configured != null && stack.getItem() == configured) {
            return true;
         } else {
            return SignAimHandler.isGunpowderTarget(SignAimHandler.lastItemTarget) && stack.getItem() == Items.GUNPOWDER
               ? true
               : SignAimHandler.isReedsTarget(SignAimHandler.lastItemTarget) && stack.getItem() == Items.REEDS;
         }
      } else {
         return false;
      }
   }

   private int countReedsInInventory() {
      try {
         int total = 0;

         for (ItemStack stack : this.mc.player.inventory.mainInventory) {
            if (stack != null && !stack.isEmpty() && this.isFarmItem(stack)) {
               total += stack.getCount();
            }
         }

         return total;
      } catch (Exception var4) {
         System.out.println("[MacroRecorder] (F4) Lỗi khi kiểm tra túi đồ: " + var4);
         var4.printStackTrace();
         return 0;
      }
   }

   private BlockPos parseGotoPos(String command) {
      if (command == null) {
         return null;
      } else {
         String[] parts = command.trim().split("\\s+");
         if (parts.length >= 4 && parts[0].equalsIgnoreCase("#goto")) {
            try {
               int x = Integer.parseInt(parts[1]);
               int y = Integer.parseInt(parts[2]);
               int z = Integer.parseInt(parts[3]);
               return new BlockPos(x, y, z);
            } catch (Exception var7) {
            }
         } else if (parts.length == 3 && parts[0].equalsIgnoreCase("#goto")) {
            try {
               int x = Integer.parseInt(parts[1]);
               int z = Integer.parseInt(parts[2]);
               return new BlockPos(x, 0, z);
            } catch (Exception var6) {
            }
         }

         return null;
      }
   }

   private boolean isAtDestination(String gotoCommand) {
      if (this.mc != null && this.mc.player != null) {
         BlockPos target = this.parseGotoPos(gotoCommand);
         if (target == null) {
            return true;
         } else {
            double dx = this.mc.player.posX - ((double)target.getX() + 0.5);
            double dz = this.mc.player.posZ - ((double)target.getZ() + 0.5);
            return dx * dx + dz * dz <= 4.0;
         }
      } else {
         return false;
      }
   }

   private boolean isPushedFarFromCounter() {
      if (this.mc != null && this.mc.player != null) {
         BlockPos target = this.parseGotoPos(this.afterShopGotoCommand);
         if (target == null) {
            return false;
         } else {
            double dx = this.mc.player.posX - ((double)target.getX() + 0.5);
            double dz = this.mc.player.posZ - ((double)target.getZ() + 0.5);
            return dx * dx + dz * dz > 9.0;
         }
      } else {
         return false;
      }
   }

   private boolean isInventoryFull() {
      if (this.mc != null && this.mc.player != null) {
         int emptySlots = 0;

         for (ItemStack stack : this.mc.player.inventory.mainInventory) {
            if (stack == null || stack.isEmpty()) {
               emptySlots++;
            }
         }

         return emptySlots == 0;
      } else {
         return false;
      }
   }

   private boolean isInventoryFullFarm() {
      if (this.mc != null && this.mc.player != null) {
         int emptySlots = 0;
         int farmItemCount = 0;

         for (ItemStack stack : this.mc.player.inventory.mainInventory) {
            if (stack == null || stack.isEmpty()) {
               emptySlots++;
            } else if (this.isFarmItem(stack)) {
               farmItemCount++;
            }
         }

         return emptySlots == 0 && farmItemCount >= 30;
      } else {
         return false;
      }
   }

   public boolean isTrashStack(int slotIndex, ItemStack stack, int heldHotbarIndex) {
      if (stack == null || stack.isEmpty()) {
         return false;
      } else if (this.isFarmItem(stack)) {
         return false;
      } else {
         Item item = stack.getItem();
         return slotIndex != heldHotbarIndex || !(item instanceof ItemSword) && !(item instanceof ItemTool)
            ? slotIndex < 0 || slotIndex > 8 || !(item instanceof ItemFood)
            : false;
      }
   }

   private boolean hasTrashInInventory() {
      if (this.mc != null && this.mc.player != null) {
         int heldSlot = this.mc.player.inventory.currentItem;

         for (int i = 0; i < this.mc.player.inventory.mainInventory.size(); i++) {
            ItemStack stack = (ItemStack)this.mc.player.inventory.mainInventory.get(i);
            if (this.isTrashStack(i, stack, heldSlot)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private int dropAllTrashFromInventory() {
      if (this.mc != null && this.mc.player != null && this.mc.playerController != null) {
         int droppedCount = 0;
         int heldSlot = this.mc.player.inventory.currentItem;
         if (this.mc.currentScreen != null) {
            this.mc.player.closeScreen();
         }

         Container container = this.mc.player.inventoryContainer;
         if (container == null) {
            return 0;
         } else {
            for (Slot slot : container.inventorySlots) {
               if (slot != null && slot.getHasStack() && slot.slotNumber >= 9 && slot.slotNumber <= 44) {
                  ItemStack stack = slot.getStack();
                  if (stack != null && !stack.isEmpty()) {
                     int mainInvIndex = slot.slotNumber >= 36 ? slot.slotNumber - 36 : slot.slotNumber;
                     if (this.isTrashStack(mainInvIndex, stack, heldSlot)) {
                        this.mc.playerController.windowClick(0, slot.slotNumber, 1, ClickType.THROW, this.mc.player);
                        droppedCount++;
                     }
                  }
               }
            }

            return droppedCount;
         }
      } else {
         return 0;
      }
   }

   private int takeAllFromChestSuperFast() {
      if (!(this.mc.currentScreen instanceof GuiContainer)) {
         return 0;
      } else {
         try {
            GuiContainer gui = (GuiContainer)this.mc.currentScreen;
            if (!(gui.inventorySlots instanceof ContainerChest)) {
               return 0;
            } else {
               ContainerChest container = (ContainerChest)gui.inventorySlots;
               int totalTaken = 0;
               int chestSlotCount = container.getLowerChestInventory().getSizeInventory();

               for (int i = 0; i < chestSlotCount && !this.isInventoryFull(); i++) {
                  Slot slot = (Slot)container.inventorySlots.get(i);
                  if (slot != null && slot.getHasStack() && !slot.getStack().isEmpty() && this.isFarmItem(slot.getStack())) {
                     this.mc.playerController.windowClick(container.windowId, i, 0, ClickType.QUICK_MOVE, this.mc.player);
                     totalTaken++;
                  }
               }

               return totalTaken;
            }
         } catch (Exception var7) {
            System.out.println("[MacroRecorder] Lỗi khi lấy đồ từ rương: " + var7);
            return 0;
         }
      }
   }

   private boolean hasRemainingItemsInChest() {
      if (!(this.mc.currentScreen instanceof GuiContainer)) {
         return false;
      } else {
         try {
            GuiContainer gui = (GuiContainer)this.mc.currentScreen;
            if (!(gui.inventorySlots instanceof ContainerChest)) {
               return false;
            }

            ContainerChest container = (ContainerChest)gui.inventorySlots;
            int chestSlotCount = container.getLowerChestInventory().getSizeInventory();

            for (int i = 0; i < chestSlotCount; i++) {
               Slot slot = (Slot)container.inventorySlots.get(i);
               if (slot != null && slot.getHasStack() && !slot.getStack().isEmpty() && this.isFarmItem(slot.getStack())) {
                  return true;
               }
            }
         } catch (Exception var6) {
         }

         return false;
      }
   }

   private int depositOnlyFarmItemsSuperFast() {
      if (!(this.mc.currentScreen instanceof GuiContainer)) {
         return 0;
      } else {
         try {
            GuiContainer gui = (GuiContainer)this.mc.currentScreen;
            if (!(gui.inventorySlots instanceof ContainerChest)) {
               return 0;
            } else {
               ContainerChest container = (ContainerChest)gui.inventorySlots;
               int totalMoved = 0;
               int chestSlotCount = container.getLowerChestInventory().getSizeInventory();
               int totalSlots = container.inventorySlots.size();

               for (int i = chestSlotCount; i < totalSlots; i++) {
                  Slot slot = (Slot)container.inventorySlots.get(i);
                  if (slot != null && slot.getHasStack() && this.isFarmItem(slot.getStack())) {
                     this.mc.playerController.windowClick(container.windowId, i, 0, ClickType.QUICK_MOVE, this.mc.player);
                     totalMoved++;
                  }
               }

               return totalMoved;
            }
         } catch (Exception var8) {
            System.out.println("[MacroRecorder] Lỗi khi chuyển thuốc súng vào kho /pv: " + var8);
            return 0;
         }
      }
   }

   private boolean isCurrentVaultFull() {
      if (!(this.mc.currentScreen instanceof GuiContainer)) {
         return false;
      } else {
         try {
            GuiContainer gui = (GuiContainer)this.mc.currentScreen;
            if (!(gui.inventorySlots instanceof ContainerChest)) {
               return false;
            } else {
               ContainerChest container = (ContainerChest)gui.inventorySlots;
               int chestSlotCount = container.getLowerChestInventory().getSizeInventory();
               if (chestSlotCount <= 0) {
                  return false;
               } else {
                  for (int i = 0; i < chestSlotCount; i++) {
                     Slot slot = (Slot)container.inventorySlots.get(i);
                     if (slot == null || !slot.getHasStack() || slot.getStack().isEmpty()) {
                        return false;
                     }

                     if (slot.getStack().getCount() < slot.getStack().getMaxStackSize()) {
                        return false;
                     }
                  }

                  return true;
               }
            }
         } catch (Exception var6) {
            return false;
         }
      }
   }

   private void holdUseItemKey() {
      if (this.mc != null && this.mc.gameSettings != null) {
         KeyBinding.setKeyBindState(this.mc.gameSettings.keyBindUseItem.getKeyCode(), true);
      }
   }

   private void releaseUseItemKey() {
      if (this.mc != null && this.mc.gameSettings != null) {
         KeyBinding.setKeyBindState(this.mc.gameSettings.keyBindUseItem.getKeyCode(), false);
      }
   }

   public static void ensureNettyFilterInstalled() {
      Minecraft mc = Minecraft.getMinecraft();
      if (mc != null) {
         try {
            if (mc.getConnection() != null && mc.getConnection().getNetworkManager() != null) {
               Channel channel = mc.getConnection().getNetworkManager().channel();
               if (channel != null && channel.pipeline() != null && channel.pipeline().get("macro_packet_filter") == null) {
                  channel.pipeline().addBefore("packet_handler", "macro_packet_filter", new ChannelDuplexHandler() {
                     public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                        if (msg instanceof CPacketChatMessage) {
                           String content = ((CPacketChatMessage)msg).getMessage();
                           if (content != null && (content.startsWith(".") || content.startsWith("#"))) {
                              System.out.println("[MacroRecorder] (Netty Filter) Đã chặn lệnh client '" + content + "' không cho gửi lên server!");
                              return;
                           }
                        }

                        super.write(ctx, msg, promise);
                     }
                  });
                  System.out.println("[MacroRecorder] Đã kích hoạt bộ lọc Netty chống rò rỉ chat client.");
               }
            }
         } catch (Throwable var2) {
         }
      }
   }

   public static void executeClientCommand(String command) {
      if (command != null && !command.trim().isEmpty()) {
         Minecraft mc = Minecraft.getMinecraft();
         if (mc.player != null) {
            ensureNettyFilterInstalled();
            String clean = command.trim();
            if (clean.toLowerCase().startsWith(".look") || clean.toLowerCase().startsWith("#look")) {
               handleDirectLook(clean);
               BaritoneIntegration.executeMinClientOrImpact(clean);
               MinecraftForge.EVENT_BUS.post(new ClientChatEvent(clean));
            } else if (!clean.startsWith("#") && !clean.startsWith(".")) {
               ClientChatEvent event = new ClientChatEvent(clean);
               boolean canceled = MinecraftForge.EVENT_BUS.post(event);
               if (!canceled) {
                  String resultMsg = event.getMessage();
                  if (resultMsg != null && !resultMsg.trim().isEmpty() && !resultMsg.startsWith(".") && !resultMsg.startsWith("#")) {
                     if (ClientCommandHandler.instance.executeCommand(mc.player, resultMsg) == 0) {
                        mc.player.sendChatMessage(resultMsg);
                     }
                  }
               }
            } else {
               BaritoneIntegration.execute(clean);
               MinecraftForge.EVENT_BUS.post(new ClientChatEvent(clean));
               System.out.println("[MacroRecorder] Da thuc thi lenh client API: '" + clean + "' (khong chat len server)");
            }
         }
      }
   }

   private static void handleDirectLook(String cmd) {
      if (cmd != null) {
         String clean = cmd.trim();
         String lower = clean.toLowerCase();
         if (lower.startsWith(".look") || lower.startsWith("#look")) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.player != null) {
               try {
                  String[] parts = clean.split("\\s+");
                  if (parts.length >= 4) {
                     double tx = Double.parseDouble(parts[1]);
                     double ty = Double.parseDouble(parts[2]);
                     double tz = Double.parseDouble(parts[3]);
                     if (!parts[1].contains(".")) {
                        tx += 0.5;
                     }

                     if (!parts[2].contains(".")) {
                        ty += 0.5;
                     }

                     if (!parts[3].contains(".")) {
                        tz += 0.5;
                     }

                     double dx = tx - mc.player.posX;
                     double dy = ty - (mc.player.posY + (double)mc.player.getEyeHeight());
                     double dz = tz - mc.player.posZ;
                     double dist = (double)MathHelper.sqrt(dx * dx + dz * dz);
                     float targetYaw = (float)(MathHelper.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
                     float targetPitch = (float)(-(MathHelper.atan2(dy, dist) * (180.0 / Math.PI)));
                     applyPlayerRotation(mc, targetYaw, targetPitch);
                     System.out
                        .println("[MacroRecorder] Da xoay camera toi (" + tx + ", " + ty + ", " + tz + ") | Yaw: " + targetYaw + " Pitch: " + targetPitch);
                  } else if (parts.length == 3) {
                     float targetYaw = parseAngle(parts[1], mc.player.rotationYaw);
                     float targetPitch = parseAngle(parts[2], mc.player.rotationPitch);
                     applyPlayerRotation(mc, targetYaw, targetPitch);
                     System.out.println("[MacroRecorder] Da dat camera Yaw: " + targetYaw + " Pitch: " + targetPitch);
                  }
               } catch (Exception var21) {
                  System.out.println("[MacroRecorder] Khong parse duoc toa do .look: " + clean + " (" + var21.getMessage() + ")");
               }
            }
         }
      }
   }

   private static void applyPlayerRotation(Minecraft mc, float yaw, float pitch) {
      pitch = MathHelper.clamp(pitch, -90.0F, 90.0F);
      yaw = MathHelper.wrapDegrees(yaw);
      mc.player.rotationYaw = yaw;
      mc.player.rotationPitch = pitch;
      mc.player.prevRotationYaw = yaw;
      mc.player.prevRotationPitch = pitch;
      mc.player.rotationYawHead = yaw;
      mc.player.prevRotationYawHead = yaw;
      mc.player.renderYawOffset = yaw;
      mc.player.prevRenderYawOffset = yaw;
      if (mc.getConnection() != null) {
         mc.getConnection().sendPacket(new Rotation(mc.player.rotationYaw, mc.player.rotationPitch, mc.player.onGround));
      }

      try {
         Class<?> apiClass = Class.forName("baritone.api.BaritoneAPI");
         Object provider = apiClass.getMethod("getProvider").invoke(null);
         if (provider != null) {
            Object primary = provider.getClass().getMethod("getPrimaryBaritone").invoke(provider);
            if (primary != null) {
               Object lookBehavior = primary.getClass().getMethod("getLookBehavior").invoke(primary);
               if (lookBehavior != null) {
                  Class<?> rotClass = Class.forName("baritone.api.utils.Rotation");
                  Object rot = rotClass.getConstructor(float.class, float.class).newInstance(yaw, pitch);
                  lookBehavior.getClass().getMethod("updateTarget", rotClass, boolean.class).invoke(lookBehavior, rot, true);
               }
            }
         }
      } catch (Throwable var9) {
      }
   }

   private static float parseAngle(String input, float current) throws NumberFormatException {
      if (input.startsWith("~")) {
         return input.length() == 1 ? current : current + Float.parseFloat(input.substring(1));
      } else {
         return Float.parseFloat(input);
      }
   }

   private boolean trySendCommand(String message) {
      if (message == null || message.trim().isEmpty()) {
         return true;
      } else if (!message.startsWith(".") && !message.startsWith("#")) {
         long now = System.currentTimeMillis();
         if (now < serverCommandCooldownUntil) {
            return false;
         } else {
            this.mc.player.sendChatMessage(message);
            return true;
         }
      } else {
         executeClientCommand(message);
         return true;
      }
   }

   private void lookAtBlock(BlockPos pos) {
      if (this.mc != null && this.mc.player != null && pos != null) {
         double tx = (double)pos.getX() + 0.5;
         double ty = (double)pos.getY() + 0.5;
         double tz = (double)pos.getZ() + 0.5;
         double dx = tx - this.mc.player.posX;
         double dy = ty - (this.mc.player.posY + (double)this.mc.player.getEyeHeight());
         double dz = tz - this.mc.player.posZ;
         double dist = (double)MathHelper.sqrt(dx * dx + dz * dz);
         float targetYaw = (float)(MathHelper.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
         float targetPitch = (float)(-(MathHelper.atan2(dy, dist) * (180.0 / Math.PI)));
         applyPlayerRotation(this.mc, targetYaw, targetPitch);
      }
   }

   private BlockPos findNearestChestPos(double maxDist) {
      if (this.mc != null && this.mc.player != null && this.mc.world != null) {
         BlockPos playerPos = new BlockPos(this.mc.player.posX, this.mc.player.posY, this.mc.player.posZ);
         int r = (int)Math.ceil(maxDist);
         BlockPos bestPos = null;
         double bestDistSq = maxDist * maxDist;

         for (int dx = -r; dx <= r; dx++) {
            for (int dy = -2; dy <= 3; dy++) {
               for (int dz = -r; dz <= r; dz++) {
                  BlockPos p = playerPos.add(dx, dy, dz);
                  if (this.mc.world.getBlockState(p).getBlock() instanceof BlockChest) {
                     double dSq = this.mc.player.getDistanceSqToCenter(p);
                     if (dSq < bestDistSq) {
                        bestDistSq = dSq;
                        bestPos = p;
                     }
                  }
               }
            }
         }

         return bestPos;
      } else {
         return null;
      }
   }

   private void openRealChest() {
      try {
         if (this.mc.objectMouseOver != null
            && this.mc.objectMouseOver.typeOfHit == Type.BLOCK
            && this.mc.world.getBlockState(this.mc.objectMouseOver.getBlockPos()).getBlock() instanceof BlockChest) {
            this.mc
               .playerController
               .processRightClickBlock(
                  this.mc.player,
                  this.mc.world,
                  this.mc.objectMouseOver.getBlockPos(),
                  this.mc.objectMouseOver.sideHit,
                  this.mc.objectMouseOver.hitVec,
                  EnumHand.MAIN_HAND
               );
            return;
         }

         BlockPos chestPos = this.findNearestChestPos(4.0);
         if (chestPos != null) {
            Vec3d hitVec = new Vec3d((double)chestPos.getX() + 0.5, (double)chestPos.getY() + 0.5, (double)chestPos.getZ() + 0.5);
            this.mc.playerController.processRightClickBlock(this.mc.player, this.mc.world, chestPos, EnumFacing.UP, hitVec, EnumHand.MAIN_HAND);
         }
      } catch (Exception var3) {
         System.out.println("[MacroRecorder] Lỗi khi mở rương: " + var3);
         var3.printStackTrace();
      }
   }

   private boolean rightClickNpc() {
      try {
         RayTraceResult trace = this.mc.objectMouseOver;
         Entity target = null;
         if (trace != null && trace.typeOfHit == Type.ENTITY && trace.entityHit != null && !(trace.entityHit instanceof EntityPlayer)) {
            target = trace.entityHit;
         } else if (trace != null && trace.typeOfHit == Type.ENTITY && trace.entityHit instanceof EntityPlayer) {
            target = this.findNpcBehindBlockingPlayer((EntityPlayer)trace.entityHit, this.npcReachDistance);
            if (target != null) {
               System.out.println("[MacroRecorder] Phát hiện người chơi đang che NPC -> tương tác với NPC phía sau.");
            }
         }

         if (target == null) {
            target = this.findLookedAtEntity(this.npcReachDistance);
         }

         if (target == null) {
            return false;
         } else {
            RayTraceResult targetTrace = null;
            if (trace != null && trace.typeOfHit == Type.ENTITY && trace.entityHit == target) {
               targetTrace = trace;
            }

            if (targetTrace == null) {
               Vec3d eye = this.mc.player.getPositionEyes(1.0F);
               Vec3d look = this.mc.player.getLook(1.0F);
               Vec3d end = eye.add(look.x * this.npcReachDistance, look.y * this.npcReachDistance, look.z * this.npcReachDistance);
               RayTraceResult hit = target.getEntityBoundingBox().grow((double)target.getCollisionBorderSize()).calculateIntercept(eye, end);
               targetTrace = hit == null ? new RayTraceResult(target, target.getPositionEyes(1.0F)) : new RayTraceResult(target, hit.hitVec);
            }

            EnumActionResult result = this.mc.playerController.interactWithEntity(this.mc.player, target, targetTrace, EnumHand.MAIN_HAND);
            if (result != EnumActionResult.SUCCESS) {
               result = this.mc.playerController.interactWithEntity(this.mc.player, target, EnumHand.MAIN_HAND);
            }

            return result == EnumActionResult.SUCCESS;
         }
      } catch (Exception var8) {
         System.out.println("[MacroRecorder] Lỗi khi click NPC: " + var8);
         var8.printStackTrace();
         return false;
      }
   }

   private boolean isPlayerBlockingNpc() {
      if (this.mc.objectMouseOver != null && this.mc.objectMouseOver.typeOfHit == Type.ENTITY && this.mc.objectMouseOver.entityHit instanceof EntityPlayer) {
         return true;
      } else {
         if (this.mc.player != null && this.mc.world != null) {
            Vec3d eyePos = this.mc.player.getPositionEyes(1.0F);
            Vec3d look = this.mc.player.getLook(1.0F);
            double reach = this.npcReachDistance;
            Vec3d endPos = eyePos.add(look.x * reach, look.y * reach, look.z * reach);
            AxisAlignedBB searchBox = this.mc.player.getEntityBoundingBox().expand(look.x * reach, look.y * reach, look.z * reach).grow(0.5);

            for (EntityPlayer p : this.mc.world.getEntitiesWithinAABB(EntityPlayer.class, searchBox)) {
               if (p != null && p != this.mc.player && p.isEntityAlive()) {
                  AxisAlignedBB aabb = p.getEntityBoundingBox().grow(0.2);
                  RayTraceResult hit = aabb.calculateIntercept(eyePos, endPos);
                  if (hit != null) {
                     return true;
                  }
               }
            }
         }

         return false;
      }
   }

   private Entity findNpcBehindBlockingPlayer(EntityPlayer blockingPlayer, double reachDistance) {
      try {
         if (blockingPlayer != null && this.mc.player != null && this.mc.world != null) {
            Vec3d eyePos = this.mc.player.getPositionEyes(1.0F);
            Vec3d look = this.mc.player.getLook(1.0F);
            Vec3d endPos = eyePos.add(look.x * reachDistance, look.y * reachDistance, look.z * reachDistance);
            AxisAlignedBB searchBox = this.mc
               .player
               .getEntityBoundingBox()
               .expand(look.x * reachDistance, look.y * reachDistance, look.z * reachDistance)
               .grow(1.0);
            List<Entity> candidates = this.mc.world.getEntitiesWithinAABBExcludingEntity(this.mc.player, searchBox);
            double blockingDist = eyePos.squareDistanceTo(blockingPlayer.getPositionEyes(1.0F));
            Entity closestBehind = null;
            double closestBehindDistSq = Double.MAX_VALUE;

            for (Entity entity : candidates) {
               if (entity != null && entity != blockingPlayer && !(entity instanceof EntityPlayer) && entity.canBeCollidedWith()) {
                  AxisAlignedBB aabb = entity.getEntityBoundingBox().grow((double)entity.getCollisionBorderSize());
                  RayTraceResult hit = aabb.calculateIntercept(eyePos, endPos);
                  if (hit != null) {
                     double distSq = eyePos.squareDistanceTo(hit.hitVec);
                     if (!(distSq <= blockingDist + 1.0E-4) && distSq < closestBehindDistSq) {
                        closestBehindDistSq = distSq;
                        closestBehind = entity;
                     }
                  }
               }
            }

            return closestBehind;
         } else {
            return null;
         }
      } catch (Exception var20) {
         System.out.println("[MacroRecorder] Lỗi khi tìm NPC phía sau người che: " + var20);
         return null;
      }
   }

   private Entity findLookedAtEntity(double reachDistance) {
      try {
         Vec3d eyePos = this.mc.player.getPositionEyes(1.0F);
         Vec3d look = this.mc.player.getLook(1.0F);
         Vec3d endPos = eyePos.add(look.x * reachDistance, look.y * reachDistance, look.z * reachDistance);
         AxisAlignedBB searchBox = this.mc
            .player
            .getEntityBoundingBox()
            .expand(look.x * reachDistance, look.y * reachDistance, look.z * reachDistance)
            .grow(1.0);
         List<Entity> candidates = this.mc.world.getEntitiesWithinAABBExcludingEntity(this.mc.player, searchBox);
         Entity closest = null;
         double closestDistSq = reachDistance * reachDistance;

         for (Entity entity : candidates) {
            if (entity != null && entity.canBeCollidedWith()) {
               AxisAlignedBB aabb = entity.getEntityBoundingBox().grow((double)entity.getCollisionBorderSize());
               RayTraceResult hit = aabb.calculateIntercept(eyePos, endPos);
               if (hit != null) {
                  double distSq = eyePos.squareDistanceTo(hit.hitVec);
                  if (distSq < closestDistSq) {
                     closestDistSq = distSq;
                     closest = entity;
                  }
               }
            }
         }

         return closest;
      } catch (Exception var17) {
         System.out.println("[MacroRecorder] Lỗi khi tự raytrace tìm NPC: " + var17);
         var17.printStackTrace();
         return null;
      }
   }

   private void debugLogContainerItems(GuiContainer gui, String label) {
      try {
         List<Slot> slots = gui.inventorySlots.inventorySlots;
         System.out.println("[MacroRecorder] (debug) === " + label + ": " + slots.size() + " slot, liệt kê item ===");
         int nonEmptyCount = 0;

         for (Slot slot : slots) {
            ItemStack stack = slot.getStack();
            if (stack != null && !stack.isEmpty()) {
               nonEmptyCount++;
               String registryName = stack.getItem().getRegistryName() != null ? stack.getItem().getRegistryName().toString() : "unknown";
               String nbtInfo = stack.hasTagCompound() ? "NBT: " + stack.getTagCompound().getKeySet().size() + " tag(s)" : "không có NBT";
               System.out
                  .println(
                     "[MacroRecorder] (debug) Slot "
                        + slot.slotNumber
                        + ": x"
                        + stack.getCount()
                        + " "
                        + stack.getDisplayName()
                        + " ("
                        + registryName
                        + ") | "
                        + nbtInfo
                  );
            }
         }

         if (nonEmptyCount == 0) {
            System.out.println("[MacroRecorder] (debug) " + label + ": KHÔNG có item nào (toàn bộ slot trống).");
         }
      } catch (Exception var10) {
         System.out.println("[MacroRecorder] Lỗi khi debug item trong GUI: " + var10);
         var10.printStackTrace();
      }
   }

   private void debugChat(String message) {
      try {
         System.out.println("[MacroRecorder] " + message);
         if (this.mc != null && this.mc.player != null) {
            this.mc.player.sendMessage(new TextComponentString("[MacroRecorder] " + message));
         }
      } catch (Exception var3) {
         System.out.println("[MacroRecorder] Lỗi khi in debug chat: " + var3);
      }
   }

   private boolean isLookingAtChest() {
      try {
         if (this.mc.objectMouseOver != null && this.mc.objectMouseOver.typeOfHit == Type.BLOCK) {
            BlockPos pos = this.mc.objectMouseOver.getBlockPos();
            Block block = this.mc.world.getBlockState(pos).getBlock();
            return block instanceof BlockChest;
         } else {
            return false;
         }
      } catch (Exception var3) {
         System.out.println("[MacroRecorder] Lỗi khi kiểm tra rương kế tiếp: " + var3);
         var3.printStackTrace();
         return false;
      }
   }

   private boolean canProceedToShop() {
      try {
         if (this.accountOrder != null && this.accountOrder.size() > 1 && this.mc.player != null && this.mc.world != null) {
            String myName = this.mc.player.getName();
            int myIndex = -1;

            for (int i = 0; i < this.accountOrder.size(); i++) {
               if (this.accountOrder.get(i).equalsIgnoreCase(myName)) {
                  myIndex = i;
                  break;
               }
            }

            if (myIndex < 0) {
               return true;
            } else if (myIndex == 0) {
               return true;
            } else {
               EntityPlayer previous = this.findPlayerEntityByName(this.accountOrder.get(myIndex - 1));
               if (previous == null) {
                  return false;
               } else {
                  double distance = this.mc.player.getDistance(previous.posX, previous.posY, previous.posZ);
                  return distance >= (double)this.queueDistanceThreshold;
               }
            }
         } else {
            return true;
         }
      } catch (Exception var6) {
         System.out.println("[MacroRecorder] (Hàng đợi) Lỗi khi kiểm tra khoảng cách: " + var6);
         var6.printStackTrace();
         return true;
      }
   }

   private EntityPlayer findPlayerEntityByName(String username) {
      if (username != null && this.mc.world != null) {
         try {
            for (EntityPlayer p : this.mc.world.playerEntities) {
               if (p != null && p.getName() != null && p.getName().equalsIgnoreCase(username)) {
                  return p;
               }
            }
         } catch (Exception var4) {
            System.out.println("[MacroRecorder] (Hàng đợi) Lỗi khi tìm entity người chơi \"" + username + "\": " + var4);
         }

         return null;
      } else {
         return null;
      }
   }

   private void debugPrintQueueDistances() {
      try {
         if (this.mc.player == null || this.mc.world == null) {
            return;
         }

         if (this.accountOrder == null || this.accountOrder.isEmpty()) {
            String msg = "[Debug KC] Danh sách tài khoản đang trống (mở GUI phím U để thêm).";
            System.out.println("[MacroRecorder] " + msg);
            this.mc.player.sendMessage(new TextComponentString(msg));
            return;
         }

         String myName = this.mc.player.getName();
         System.out.println("[MacroRecorder] ===== DEBUG KHOẢNG CÁCH (phím O) =====");
         this.mc.player.sendMessage(new TextComponentString("[Debug KC] Ngưỡng chờ hiện tại: " + this.queueDistanceThreshold + "m"));

         for (String username : this.accountOrder) {
            if (username.equalsIgnoreCase(myName)) {
               String msg = "- " + username + " (chính mình)";
               System.out.println("[MacroRecorder] " + msg);
               this.mc.player.sendMessage(new TextComponentString("[Debug KC] " + msg));
            } else {
               EntityPlayer target = this.findPlayerEntityByName(username);
               String msg;
               if (target == null) {
                  msg = "- " + username + ": KHÔNG THẤY (quá xa / không cùng thế giới / offline)";
               } else {
                  double distance = this.mc.player.getDistance(target.posX, target.posY, target.posZ);
                  msg = "- "
                     + username
                     + ": "
                     + String.format("%.1f", distance)
                     + "m"
                     + (distance >= (double)this.queueDistanceThreshold ? " (ĐỦ xa)" : " (CHƯA đủ xa)");
               }

               System.out.println("[MacroRecorder] " + msg);
               this.mc.player.sendMessage(new TextComponentString("[Debug KC] " + msg));
            }
         }

         System.out.println("[MacroRecorder] =======================================");
      } catch (Exception var8) {
         System.out.println("[MacroRecorder] Lỗi khi debug khoảng cách: " + var8);
         var8.printStackTrace();
      }
   }

   private void closeCurrentScreen() {
      try {
         if (this.mc.currentScreen != null) {
            this.mc.player.closeScreen();
         }
      } catch (Exception var2) {
         System.out.println("[MacroRecorder] Lỗi khi đóng màn hình: " + var2);
         var2.printStackTrace();
      }
   }

   private void beginInventoryTransfer(boolean taking) {
      this.inventoryTransferActive = true;
      this.inventoryTransferTaking = taking;
      this.inventoryTransferWaitTicks = 0;
      this.inventoryTransferCount = 0;
      if (!(this.mc.currentScreen instanceof GuiContainer)) {
         this.inventoryTransferActive = false;
      } else {
         GuiContainer gui = (GuiContainer)this.mc.currentScreen;
         if (!(gui.inventorySlots instanceof ContainerChest)) {
            this.inventoryTransferActive = false;
         } else {
            ContainerChest container = (ContainerChest)gui.inventorySlots;
            int chestSlotCount = container.getLowerChestInventory().getSizeInventory();
            if (taking) {
               this.inventoryTransferCursor = chestSlotCount - 1;
               this.inventoryTransferEnd = -1;
            } else {
               this.inventoryTransferCursor = chestSlotCount;
               this.inventoryTransferEnd = container.inventorySlots.size();
            }
         }
      }
   }

   private boolean processInventoryTransfer() {
      if (!this.inventoryTransferActive) {
         return true;
      } else if (!(this.mc.currentScreen instanceof GuiContainer)) {
         this.inventoryTransferActive = false;
         return true;
      } else {
         GuiContainer gui = (GuiContainer)this.mc.currentScreen;
         if (!(gui.inventorySlots instanceof ContainerChest)) {
            this.inventoryTransferActive = false;
            return true;
         } else {
            ContainerChest container = (ContainerChest)gui.inventorySlots;
            if (ConfigHelper.getItemTransferDelayMs() <= 0) {
               if (this.inventoryTransferTaking) {
                  for (int i = this.inventoryTransferCursor; i >= 0; i--) {
                     Slot slot = (Slot)container.inventorySlots.get(i);
                     if (slot != null && slot.getHasStack() && this.isFarmItem(slot.getStack())) {
                        this.mc.playerController.windowClick(container.windowId, i, 0, ClickType.QUICK_MOVE, this.mc.player);
                        this.inventoryTransferCount++;
                     }
                  }
               } else {
                  for (int ix = this.inventoryTransferCursor; ix < this.inventoryTransferEnd; ix++) {
                     Slot slot = (Slot)container.inventorySlots.get(ix);
                     if (slot != null && slot.getHasStack() && this.isFarmItem(slot.getStack())) {
                        this.mc.playerController.windowClick(container.windowId, ix, 0, ClickType.QUICK_MOVE, this.mc.player);
                        this.inventoryTransferCount++;
                     }
                  }
               }

               this.inventoryTransferActive = false;
               return true;
            } else {
               long now = System.nanoTime();
               if (this.inventoryTransferNextClickNanos > now) {
                  return false;
               } else {
                  int selectedSlot = -1;
                  if (this.inventoryTransferTaking) {
                     while (this.inventoryTransferCursor >= 0) {
                        int ixx = this.inventoryTransferCursor--;
                        Slot slot = (Slot)container.inventorySlots.get(ixx);
                        if (slot != null && slot.getHasStack() && this.isFarmItem(slot.getStack())) {
                           selectedSlot = ixx;
                           break;
                        }
                     }
                  } else {
                     while (this.inventoryTransferCursor < this.inventoryTransferEnd) {
                        int ixx = this.inventoryTransferCursor++;
                        Slot slot = (Slot)container.inventorySlots.get(ixx);
                        if (slot != null && slot.getHasStack() && this.isFarmItem(slot.getStack())) {
                           selectedSlot = ixx;
                           break;
                        }
                     }
                  }

                  if (selectedSlot < 0) {
                     this.inventoryTransferActive = false;
                     return true;
                  } else {
                     this.mc.playerController.windowClick(container.windowId, selectedSlot, 0, ClickType.QUICK_MOVE, this.mc.player);
                     this.inventoryTransferCount++;
                     int delayMs = ConfigHelper.getItemTransferDelayMs();
                     this.inventoryTransferNextClickNanos = now + (long)delayMs * 1000000L;
                     return false;
                  }
               }
            }
         }
      }
   }

   private int takeAllSugarCaneSuperFast() {
      if (!(this.mc.currentScreen instanceof GuiContainer)) {
         return 0;
      } else {
         try {
            GuiContainer gui = (GuiContainer)this.mc.currentScreen;
            if (!(gui.inventorySlots instanceof ContainerChest)) {
               return 0;
            } else {
               ContainerChest container = (ContainerChest)gui.inventorySlots;
               int totalTaken = 0;
               int chestSlotCount = container.getLowerChestInventory().getSizeInventory();

               for (int i = chestSlotCount - 1; i >= 0; i--) {
                  Slot slot = (Slot)container.inventorySlots.get(i);
                  if (slot != null && slot.getHasStack() && this.isFarmItem(slot.getStack())) {
                     this.mc.playerController.windowClick(container.windowId, i, 0, ClickType.QUICK_MOVE, this.mc.player);
                     totalTaken++;
                  }
               }

               return totalTaken;
            }
         } catch (Exception var7) {
            System.out.println("[MacroRecorder] Lỗi khi lấy đồ trong rương: " + var7);
            var7.printStackTrace();
            return 0;
         }
      }
   }

   private boolean hasRemainingFarmItemsInVault() {
      if (!(this.mc.currentScreen instanceof GuiContainer)) {
         return false;
      } else {
         try {
            GuiContainer gui = (GuiContainer)this.mc.currentScreen;
            if (!(gui.inventorySlots instanceof ContainerChest)) {
               return false;
            }

            ContainerChest container = (ContainerChest)gui.inventorySlots;
            int chestSlotCount = container.getLowerChestInventory().getSizeInventory();

            for (int i = 0; i < chestSlotCount; i++) {
               Slot slot = (Slot)container.inventorySlots.get(i);
               if (slot != null && slot.getHasStack() && this.isFarmItem(slot.getStack())) {
                  return true;
               }
            }
         } catch (Exception var6) {
            System.out.println("[MacroRecorder] Lỗi khi kiểm tra đồ trong kho /pv: " + var6);
         }

         return false;
      }
   }

   private int moveAllSugarCaneSuperFast() {
      if (!(this.mc.currentScreen instanceof GuiContainer)) {
         return 0;
      } else {
         try {
            GuiContainer gui = (GuiContainer)this.mc.currentScreen;
            if (!(gui.inventorySlots instanceof ContainerChest)) {
               return 0;
            } else {
               ContainerChest container = (ContainerChest)gui.inventorySlots;
               int totalMoved = 0;
               int startInventorySlot = container.getLowerChestInventory().getSizeInventory();

               for (int i = startInventorySlot; i < container.inventorySlots.size(); i++) {
                  Slot slot = (Slot)container.inventorySlots.get(i);
                  if (slot != null && slot.getHasStack() && this.isFarmItem(slot.getStack())) {
                     this.mc.playerController.windowClick(container.windowId, i, 0, ClickType.QUICK_MOVE, this.mc.player);
                     totalMoved++;
                  }
               }

               return totalMoved;
            }
         } catch (Exception var7) {
            System.out.println("[MacroRecorder] Lỗi khi bỏ đồ vào kho: " + var7);
            var7.printStackTrace();
            return 0;
         }
      }
   }

   public void startMacro() {
      if (this.isRunning) {
         System.out.println("[MacroRecorder] Macro đang chạy!");
      } else if (this.sellMacroRunning) {
         System.out.println("[MacroRecorder] Macro Bán Mía (F4) đang chạy, dừng nó (F10) trước khi chạy F9!");
      } else if (this.mc != null && this.mc.player != null) {
         this.reloadConfig();
         this.chestIndex = 0;
         this.vaultNumber = 1;
         this.roundInChest = 1;
         this.depositRoundInVault = 0;
         this.finishing = false;
         this.totalSugarCaneTaken = 0;
         this.stableLookTicks = 0;
         this.sethomeSentBeforeShop = false;
         this.dropAllTrashFromInventory();
         if (this.targetVaultsToFill < 0) {
            this.targetVaultsToFill = this.maxVaults > 0 ? this.maxVaults : 1;
         }

         System.out.println("[MacroRecorder] Mục tiêu lấy đồ: Bù " + this.targetVaultsToFill + " kho /pv + full 1E trên người.");
         System.out.println("[MacroRecorder] Chờ " + this.lookWaitTicks + " tick rồi mở rương đầu tiên...");
         this.waitTimer = 0;
         this.state = MacroRecorderMod.MacroState.WAITING_START_LOOK;
         this.isRunning = true;
         System.out.println("[MacroRecorder] ========================================");
         if (this.targetVaultsToFill == 0) {
            System.out.println("[MacroRecorder] >>> BẮT ĐẦU MACRO - chỉ lấy 1E đồ farm trên người (0 kho /pv cần bù) -> đi thẳng ra shop!");
         } else {
            System.out.println("[MacroRecorder] >>> BẮT ĐẦU MACRO - chạy bù /pv 1 -> " + this.targetVaultsToFill + " (kho cuối #goto xong đi thẳng ra shop)");
         }

         System.out
            .println(
               "[MacroRecorder] >>> Lấy đủ "
                  + this.targetVaultsToFill
                  + " kho -> /shop -> tự động chuyển sang chạy logic bán của F4 -> /HOME MIA -> lặp lại vô hạn."
            );
         System.out.println("[MacroRecorder] >>> Nhấn F10 để dừng giữa chừng");
         System.out.println("[MacroRecorder] ========================================");
      } else {
         System.out.println("[MacroRecorder] Không thể bắt đầu: player null");
      }
   }

   private void completeMacro() {
      this.isRunning = false;
      System.out.println("[MacroRecorder] ========================================");
      System.out.println("[MacroRecorder] >>> HOÀN THÀNH MACRO! Đã dùng hết " + this.maxVaults + " kho theo cấu hình.");
      System.out.println("[MacroRecorder] >>> Đã xử lý " + this.chestIndex + " rương");
      System.out.println("[MacroRecorder] >>> Tổng mía: " + this.totalSugarCaneTaken + " stacks");
      System.out.println("[MacroRecorder] ========================================");
   }

   public void startF4Sequence() {
      if (this.isRunning || this.sellMacroRunning || this.preStartActive) {
         System.out.println("[MacroRecorder] Có macro đang chạy rồi!");
      } else if (this.mc != null && this.mc.player != null) {
         this.reloadConfig();
         this.preStartTimer = 0;
         this.macroPaused = false;
         this.hudStacksSoldSession = 0;
         this.hudAccumulatedMillis = 0L;
         this.hudSessionActive = true;
         this.hudSessionStartMillis = System.currentTimeMillis();
         if (this.isAtDestination(this.afterShopGotoCommand)) {
            System.out.println("[MacroRecorder] >>> (F4) Đang đứng tại quầy bán đồ, kích hoạt AutoAim và bắt đầu bán ngay!");
            this.preStartActive = false;
            this.resetAndStartSellMacro();
         } else {
            this.preStartActive = true;
            executeClientCommand(this.afterShopGotoCommand);
            System.out.println("[MacroRecorder] ========================================");
            System.out.println("[MacroRecorder] >>> (F4) Gửi " + this.afterShopGotoCommand + " để di chuyển tới quầy bán đồ...");
         }
      } else {
         System.out.println("[MacroRecorder] (F4) Không thể bắt đầu: player null");
      }
   }

   private void processPreStartSequence() {
      this.preStartTimer++;
      if (this.isAtDestination(this.afterShopGotoCommand)) {
         System.out.println("[MacroRecorder] >>> (F4) Đã tới quầy bán đồ, kích hoạt Macro...");
         this.preStartActive = false;
         this.resetAndStartSellMacro();
      } else if (this.preStartTimer >= 600) {
         System.out.println("[MacroRecorder] >>> (F4) Quá 30s chưa tới quầy bán, gửi lại " + this.afterShopGotoCommand);
         executeClientCommand(this.afterShopGotoCommand);
         this.preStartTimer = 0;
      }
   }

   public void startSellMacro() {
      if (this.sellMacroRunning) {
         System.out.println("[MacroRecorder] (F4) Macro Bán Đồ đang chạy!");
      } else if (this.isRunning) {
         System.out.println("[MacroRecorder] Macro F9 đang chạy, dừng nó (F10) trước khi chạy Macro Bán Đồ (F4)!");
      } else if (this.mc != null && this.mc.player != null) {
         this.resetAndStartSellMacro();
         this.hudStacksSoldSession = 0;
         this.hudAccumulatedMillis = 0L;
         this.macroPaused = false;
         this.hudSessionActive = true;
         this.hudSessionStartMillis = System.currentTimeMillis();
         System.out.println("[MacroRecorder] ========================================");
         System.out.println("[MacroRecorder] >>> BẮT ĐẦU MACRO BÁN ĐỒ (F4)");
         System.out.println("[MacroRecorder] >>> Nhấn F10 để dừng giữa chừng");
         System.out.println("[MacroRecorder] ========================================");
      } else {
         System.out.println("[MacroRecorder] (F4) Không thể bắt đầu: player null");
      }
   }

   private void resetAndStartSellMacro() {
      this.reloadConfig();
      this.vaultsSoldThisSession = 0;
      this.sellVaultNumber = 1;
      this.sellRoundInVault = 0;
      this.lastSellTakenCount = 0;
      this.currentVaultExhausted = false;
      this.sellWaitTimer = 0;
      this.sellClickCooldown = 0;
      this.notSellingTimer = 0;
      this.lastSellMoneyTime = System.currentTimeMillis();
      this.sellMacroRunning = true;
      SignAimHandler.anchorPos = new Vec3d(this.mc.player.posX, this.mc.player.posY, this.mc.player.posZ);
      executeClientCommand("#stop");
      if (this.mc.gameSettings != null) {
         KeyBinding.setKeyBindState(this.mc.gameSettings.keyBindForward.getKeyCode(), false);
         KeyBinding.setKeyBindState(this.mc.gameSettings.keyBindBack.getKeyCode(), false);
         KeyBinding.setKeyBindState(this.mc.gameSettings.keyBindLeft.getKeyCode(), false);
         KeyBinding.setKeyBindState(this.mc.gameSettings.keyBindRight.getKeyCode(), false);
         KeyBinding.setKeyBindState(this.mc.gameSettings.keyBindJump.getKeyCode(), false);
         KeyBinding.setKeyBindState(this.mc.gameSettings.keyBindSprint.getKeyCode(), false);
      }

      try {
         int farmId = ConfigHelper.getFarmItemId();
         String target = farmId == 338 ? "reeds" : "gunpowder";
         SignAimHandler.startAimLock(target);
         System.out.println("[MacroRecorder] >>> (F4) Tự động kích hoạt AutoAim ghim bảng Sell (" + target + ")");
      } catch (Exception var3) {
         System.out.println("[MacroRecorder] Lỗi kích hoạt AutoAim: " + var3);
      }

      this.onlySell1E = this.hasTrashInInventory();
      if (this.onlySell1E) {
         System.out.println("[MacroRecorder] (F4) Phát hiện item rác trong túi đồ (xẻng/cúp/chai nước...)! Kích hoạt chế độ CHỈ SELL 1E rồi về nhà ngay!");
      } else {
         System.out.println("[MacroRecorder] (F4) Túi đồ sạch rác. Bán 1E có sẵn, sau đó mở /pv 1 -> " + this.maxVaults + " để bán hết.");
      }

      this.sellState = MacroRecorderMod.SellState.SELLING_1E_ITEMS;
   }

   private void completeSellMacro() {
      this.releaseUseItemKey();
      this.sellMacroRunning = false;

      try {
         SignAimHandler.stopAimLock(this.mc);
      } catch (Exception var2) {
      }

      this.targetVaultsToFill = this.vaultsSoldThisSession;
      System.out.println("[MacroRecorder] ========================================");
      System.out.println("[MacroRecorder] >>> HOÀN THÀNH MACRO BÁN (F4)! Đã bán xong: " + this.vaultsSoldThisSession + " kho.");
      System.out.println("[MacroRecorder] >>> Khi về nhà sẽ bù đúng: " + this.targetVaultsToFill + " kho /pv + 1E vào túi đồ.");
      System.out.println("[MacroRecorder] >>> Tự động chuyển sang lấy đồ (F9), sẽ lặp lại vô hạn cho tới khi bấm F10...");
      System.out.println("[MacroRecorder] ========================================");
   }

   public void stopSellMacro() {
      this.releaseUseItemKey();
      if (this.sellMacroRunning) {
         this.sellMacroRunning = false;
         this.targetVaultsToFill = -1;
         this.vaultsSoldThisSession = 0;

         try {
            SignAimHandler.stopAimLock(this.mc);
         } catch (Exception var3) {
         }

         SignAimHandler.isWarmupActive = false;
         this.notSellingTimer = 0;
         if (this.mc != null && this.mc.gameSettings != null) {
            KeyBinding.setKeyBindState(this.mc.gameSettings.keyBindJump.getKeyCode(), false);
         }

         this.resetHud();

         try {
            if (this.mc != null && this.mc.currentScreen != null) {
               this.mc.player.closeScreen();
            }
         } catch (Exception var2) {
            System.out.println("[MacroRecorder] (F4) Lỗi khi dừng macro bán mía: " + var2);
            var2.printStackTrace();
         }

         System.out.println("[MacroRecorder] ========================================");
         System.out.println("[MacroRecorder] >>> ĐÃ DỪNG Macro Bán Mía (F4) tại kho /pv " + this.sellVaultNumber);
         System.out.println("[MacroRecorder] >>> Tổng mía đã rút xuống: " + this.totalSugarCaneTaken + " stacks");
         System.out.println("[MacroRecorder] ========================================");
      }
   }

   public boolean isSellMacroRunning() {
      return this.sellMacroRunning;
   }

   private void pauseMacro() {
      this.releaseUseItemKey();
      if (!this.macroPaused) {
         if (this.isRunning || this.sellMacroRunning || this.preStartActive) {
            this.macroPaused = true;

            try {
               SignAimHandler.stopAimLock(this.mc);
            } catch (Exception var2) {
            }

            if (this.hudSessionStartMillis > 0L) {
               this.hudAccumulatedMillis = this.hudAccumulatedMillis + (System.currentTimeMillis() - this.hudSessionStartMillis);
               this.hudSessionStartMillis = 0L;
            }

            System.out.println("[MacroRecorder] >>> TẠM DỪNG (F4). Nhấn F4 để chạy tiếp, F10 để dừng hẳn + reset.");
         }
      }
   }

   private void resumeMacro() {
      if (this.macroPaused) {
         this.macroPaused = false;
         this.hudSessionStartMillis = System.currentTimeMillis();
         if (this.sellMacroRunning) {
            try {
               int farmId = ConfigHelper.getFarmItemId();
               String target = farmId == 338 ? "reeds" : "gunpowder";
               SignAimHandler.startAimLock(target);
            } catch (Exception var3) {
            }
         }

         System.out.println("[MacroRecorder] >>> CHẠY TIẾP (F4).");
      }
   }

   private void stopPreStartSequence() {
      if (this.preStartActive) {
         this.preStartActive = false;
         this.macroPaused = false;
         this.resetHud();
         System.out.println("[MacroRecorder] >>> ĐÃ DỪNG (F10) khi đang định vị lại trước khi chạy Macro.");
      }
   }

   private void resetHud() {
      this.macroPaused = false;
      this.hudSessionActive = false;
      this.hudStacksSoldSession = 0;
      this.hudAccumulatedMillis = 0L;
      this.hudSessionStartMillis = 0L;
   }

   @SubscribeEvent
   @SideOnly(Side.CLIENT)
   public void onRenderOverlay(Post event) {
      if (event.getType() == ElementType.ALL) {
         if (this.hudSessionActive) {
            if (this.mc != null && this.mc.player != null && this.mc.currentScreen == null) {
               ScaledResolution sr = new ScaledResolution(this.mc);
               int screenWidth = sr.getScaledWidth();
               long runningPartMillis = !this.macroPaused && this.hudSessionStartMillis > 0L ? System.currentTimeMillis() - this.hudSessionStartMillis : 0L;
               long elapsedSeconds = Math.max(0L, (this.hudAccumulatedMillis + runningPartMillis) / 1000L);
               long hh = elapsedSeconds / 3600L;
               long mm = elapsedSeconds % 3600L / 60L;
               long ss = elapsedSeconds % 60L;
               String timeStr = String.format("%02d:%02d:%02d", hh, mm, ss);
               String line = "Thời gian: " + timeStr + (this.macroPaused ? " (tạm dừng)" : "");
               float scale = 1.1F;
               GlStateManager.pushMatrix();
               GlStateManager.scale(scale, scale, 1.0F);
               int screenHeight = sr.getScaledHeight();
               int rightMarginScaled = (int)((double)screenWidth * 0.2 / (double)scale);
               int topMarginScaled = (int)((double)screenHeight * 0.22 / (double)scale);
               int xRightScaled = (int)((float)screenWidth / scale) - rightMarginScaled;
               int lineHeight = this.mc.fontRenderer.FONT_HEIGHT + 3;
               this.drawHudLineRightAligned(line, xRightScaled, topMarginScaled, 5635925);
               GlStateManager.popMatrix();
            }
         }
      }
   }

   private void drawHudLineRightAligned(String text, int rightX, int y, int color) {
      int width = this.mc.fontRenderer.getStringWidth(text);
      this.mc.fontRenderer.drawStringWithShadow(text, (float)(rightX - width), (float)y, color);
   }

   private static String formatHudMoney(double value) {
      return String.format("%,.0f", value);
   }

   @SubscribeEvent
   @SideOnly(Side.CLIENT)
   public void onKeyInput(KeyInputEvent event) {
      if (KeyBindings.OPEN_TIMING_CONFIG != null && KeyBindings.OPEN_TIMING_CONFIG.isPressed()) {
         this.mc.displayGuiScreen(new GuiTimingConfig(this.mc.currentScreen));
      }

      if (this.openGuiKey != null && this.openGuiKey.isPressed()) {
         if (!this.isRunning && !this.sellMacroRunning && !this.preStartActive) {
            this.mc.displayGuiScreen(new GuiMacroConfig());
         } else {
            System.out.println("[MacroRecorder] Macro đang chạy! Dừng trước khi mở GUI");
         }
      }

      if (this.runMacroKey != null && this.runMacroKey.isPressed()) {
         if (!this.isRunning && !this.sellMacroRunning && !this.preStartActive) {
            this.startMacro();
         } else {
            System.out.println("[MacroRecorder] Có macro đang chạy rồi!");
         }
      }

      if (this.sellMacroKey != null && this.sellMacroKey.isPressed()) {
         if (!this.isRunning && !this.sellMacroRunning && !this.preStartActive) {
            this.startF4Sequence();
         } else {
            System.out.println("[MacroRecorder] F4: Macro dang bat - bo qua, khong tat/pause.");
         }
      }

      if (this.stopMacroKey != null && this.stopMacroKey.isPressed()) {
         if (this.isRunning) {
            this.stopMacro();
         } else if (this.sellMacroRunning) {
            this.stopSellMacro();
         } else if (this.preStartActive) {
            this.stopPreStartSequence();
         } else {
            System.out.println("[MacroRecorder] Không có macro nào đang chạy!");
         }
      }

      if (this.accountOrderGuiKey != null && this.accountOrderGuiKey.isPressed()) {
         this.mc.displayGuiScreen(new GuiAccountOrder());
      }
   }

   public void stopMacro() {
      this.releaseUseItemKey();
      if (this.isRunning) {
         this.isRunning = false;
         this.targetVaultsToFill = -1;
         this.vaultsSoldThisSession = 0;
         SignAimHandler.isWarmupActive = false;
         if (this.mc != null && this.mc.gameSettings != null) {
            KeyBinding.setKeyBindState(this.mc.gameSettings.keyBindJump.getKeyCode(), false);
         }

         this.resetHud();

         try {
            if (this.mc != null && this.mc.currentScreen != null) {
               this.mc.player.closeScreen();
            }
         } catch (Exception var2) {
            System.out.println("[MacroRecorder] Lỗi khi dừng macro: " + var2);
            var2.printStackTrace();
         }

         System.out.println("[MacroRecorder] ========================================");
         System.out.println("[MacroRecorder] >>> ĐÃ DỪNG tại rương #" + (this.chestIndex + 1) + ", kho /pv " + this.vaultNumber);
         System.out.println("[MacroRecorder] >>> Tổng mía: " + this.totalSugarCaneTaken + " stacks");
         System.out.println("[MacroRecorder] ========================================");
      }
   }

   public boolean isRunning() {
      return this.isRunning;
   }

   private static enum MacroState {
      WAITING_START_LOOK,
      OPENING_CHEST,
      WAITING_CHEST_OPEN,
      TAKING_ITEMS,
      CHECK_AFTER_TAKE,
      CLOSING_AFTER_TAKE,
      MOVING_TO_NEXT_CHEST,
      WAITING_MOVE,
      OPENING_VAULT,
      WAITING_VAULT_OPEN,
      DEPOSITING_ITEMS,
      WAITING_BEFORE_SHOP,
      OPENING_SHOP,
      WAITING_SHOP_TELEPORT,
      WAITING_SETHOME_DELAY,
      WAITING_SHOP_DELAY,
      WAITING_QUEUE_TURN,
      WAITING_LOOK_DELAY,
      WAITING_NPC_GUI,
      WAITING_AFTER_SHOP_CLICK_DELAY,
      WAITING_AFTER_SHOP_GOTO_DELAY;
   }

   private static enum SellState {
      SELLING_1E_ITEMS,
      OPENING_VAULT,
      WAITING_VAULT_OPEN,
      TAKING_ITEMS,
      SELLING_ITEMS,
      CHECK_VAULT_DONE,
      WAITING_NEXT_VAULT_DELAY,
      SENDING_HOME,
      WAITING_TELEPORT_HOME,
      WAITING_HOME_SETTLE,
      RECOVERING_TO_COUNTER;
   }
}
