package com.autorefill;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.RayTraceResult.Type;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.gameevent.TickEvent.RenderTickEvent;

public class SignAimHandler {
   public static boolean isAimLockActive = false;
   public static BlockPos targetSignPos = null;
   public static String lastItemTarget = "gunpowder";
   public static double yOffset = 0.0;
   public static Vec3d anchorPos = null;
   public static boolean isWarmupActive = false;
   public static boolean autoReturnEnabled = true;
   private static boolean isAutoWalking = false;
   private static int outOfRangeTicks = 0;
   private static int stuckTicks = 0;
   private static double lastCheckX = 0.0;
   private static double lastCheckZ = 0.0;
   private static final double TRIGGER_WALK_DIST_XZ = 2.4;
   private static final double IDEAL_SELL_DIST_XZ = 1.7;

   public static String cleanText(String input) {
      return input == null ? "" : input.replaceAll("§[0-9a-fk-orA-FK-OR]", "").trim();
   }

   public static boolean isGunpowderTarget(String target) {
      if (target == null) {
         return false;
      } else {
         String t = target.toLowerCase().trim();
         return t.equals("gunpowder") || t.equals("thuoc sung") || t.equals("thuốc súng") || t.equals("thuocsung") || t.equals("powder");
      }
   }

   public static boolean isReedsTarget(String target) {
      if (target == null) {
         return false;
      } else {
         String t = target.toLowerCase().trim();
         return t.equals("reeds")
            || t.equals("reed")
            || t.equals("sugar_cane")
            || t.equals("sugarcane")
            || t.equals("sugar cane")
            || t.equals("mia")
            || t.equals("mía");
      }
   }

   public static boolean isBuySign(TileEntitySign sign) {
      if (sign == null) {
         return false;
      } else {
         for (int i = 0; i < 4; i++) {
            String raw = cleanText(sign.signText[i].getUnformattedText()).toLowerCase();
            if (raw.contains("buy")
               || raw.contains("[buy]")
               || raw.contains("mua")
               || raw.contains("[mua]")
               || raw.contains("b ")
               || raw.startsWith("b ")
               || raw.endsWith(" b")
               || raw.equals("b")
               || raw.contains("b:")
               || raw.contains(":b")
               || raw.contains("giá mua")
               || raw.contains("mua:")
               || raw.contains("buy:")) {
               return true;
            }
         }

         return false;
      }
   }

   public static boolean isAnySellSign(TileEntitySign sign) {
      if (sign == null) {
         return false;
      } else if (isBuySign(sign)) {
         return false;
      } else {
         boolean hasSell = false;

         for (int i = 0; i < 4; i++) {
            String raw = cleanText(sign.signText[i].getUnformattedText()).toLowerCase();
            if (raw.contains("sell") || raw.contains("[sell]") || raw.contains("bán") || raw.contains("[bán]") || raw.contains("s ") || raw.startsWith("s ")) {
               hasSell = true;
            }
         }

         return hasSell;
      }
   }

   public static boolean isMatchingSellSign(TileEntitySign sign, String itemTarget) {
      if (sign == null) {
         return false;
      } else if (isBuySign(sign)) {
         return false;
      } else {
         StringBuilder sb = new StringBuilder();
         boolean hasSell = false;

         for (int i = 0; i < 4; i++) {
            String raw = cleanText(sign.signText[i].getUnformattedText()).toLowerCase();
            sb.append(" ").append(raw);
            if (raw.contains("sell") || raw.contains("[sell]") || raw.contains("bán") || raw.contains("[bán]") || raw.contains("s ") || raw.startsWith("s ")) {
               hasSell = true;
            }
         }

         if (!hasSell) {
            return false;
         } else if (itemTarget != null && !itemTarget.trim().isEmpty() && !itemTarget.equals("any") && !itemTarget.equals("all")) {
            String combined = sb.toString();
            if (isGunpowderTarget(itemTarget)) {
               return combined.contains("gunpowder")
                  || combined.contains("thuoc sung")
                  || combined.contains("thuốc súng")
                  || combined.contains("thuocsung")
                  || combined.contains("powder");
            } else {
               return !isReedsTarget(itemTarget)
                  ? combined.contains(itemTarget.toLowerCase())
                  : combined.contains("reeds")
                     || combined.contains("reed")
                     || combined.contains("sugar")
                     || combined.contains("cane")
                     || combined.contains("mia")
                     || combined.contains("mía");
            }
         } else {
            return true;
         }
      }
   }

   public static boolean isSignVisible(EntityPlayerSP player, BlockPos pos) {
      Minecraft mc = Minecraft.getMinecraft();
      if (mc.world == null) {
         return false;
      } else {
         Vec3d eyePos = player.getPositionEyes(1.0F);
         IBlockState state = mc.world.getBlockState(pos);
         AxisAlignedBB bbox = state.getBoundingBox(mc.world, pos);
         Vec3d signCenter = new Vec3d(
            (double)pos.getX() + (bbox.minX + bbox.maxX) / 2.0, (double)pos.getY() + 0.35 + yOffset, (double)pos.getZ() + (bbox.minZ + bbox.maxZ) / 2.0
         );
         RayTraceResult hit = mc.world.rayTraceBlocks(eyePos, signCenter, false, true, false);
         return hit == null || hit.typeOfHit == Type.MISS || hit.getBlockPos().equals(pos);
      }
   }

   public static boolean findTargetSign(String itemTarget) {
      Minecraft mc = Minecraft.getMinecraft();
      if (mc.world != null && mc.player != null) {
         EntityPlayerSP player = mc.player;
         if (itemTarget == null || itemTarget.trim().isEmpty()) {
            itemTarget = "gunpowder";
         }

         itemTarget = itemTarget.trim().toLowerCase();
         lastItemTarget = itemTarget;
         List<TileEntitySign> matchedSigns = new ArrayList<>();
         double maxDistSq = 256.0;

         for (TileEntity te : mc.world.loadedTileEntityList) {
            if (te instanceof TileEntitySign) {
               TileEntitySign sign = (TileEntitySign)te;
               double distSq = player.getDistanceSq(sign.getPos());
               if (!(distSq > maxDistSq) && isMatchingSellSign(sign, itemTarget)) {
                  matchedSigns.add(sign);
               }
            }
         }

         if (matchedSigns.isEmpty() && (itemTarget.equals("any") || itemTarget.equals("all"))) {
            for (TileEntity tex : mc.world.loadedTileEntityList) {
               if (tex instanceof TileEntitySign) {
                  TileEntitySign sign = (TileEntitySign)tex;
                  double distSq = player.getDistanceSq(sign.getPos());
                  if (!(distSq > 144.0) && isAnySellSign(sign)) {
                     matchedSigns.add(sign);
                  }
               }
            }
         }

         if (matchedSigns.isEmpty()) {
            String name = isGunpowderTarget(itemTarget) ? "Thuốc súng (Gunpowder)" : (isReedsTarget(itemTarget) ? "Mía (Sugar Cane)" : itemTarget);
            player.sendMessage(new TextComponentString(TextFormatting.RED + "[AutoAim] Không tìm thấy bảng Sell cho '" + name + "' trong khu vực xung quanh!"));
            return false;
         } else {
            TileEntitySign bestSign = null;
            double bestDistSq = Double.MAX_VALUE;

            for (TileEntitySign sign : matchedSigns) {
               double dSq = player.getDistanceSq(sign.getPos());
               if (dSq < bestDistSq) {
                  bestDistSq = dSq;
                  bestSign = sign;
               }
            }

            if (bestSign == null) {
               bestSign = matchedSigns.get(0);
            }

            targetSignPos = bestSign.getPos();
            return true;
         }
      } else {
         return false;
      }
   }

   private static void scanAndSwitchTargetSign(Minecraft mc) {
      if (mc.world != null && mc.player != null) {
         EntityPlayerSP player = mc.player;
         if (targetSignPos != null) {
            TileEntity te = mc.world.getTileEntity(targetSignPos);
            if (te instanceof TileEntitySign) {
               TileEntitySign currentSign = (TileEntitySign)te;
               if (isMatchingSellSign(currentSign, lastItemTarget) && player.getDistanceSq(targetSignPos) <= 64.0) {
                  return;
               }
            }
         }

         TileEntitySign closestSign = null;
         double closestDistSq = Double.MAX_VALUE;

         for (TileEntity te : mc.world.loadedTileEntityList) {
            if (te instanceof TileEntitySign) {
               TileEntitySign sign = (TileEntitySign)te;
               if (isMatchingSellSign(sign, lastItemTarget)) {
                  double dSq = player.getDistanceSq(sign.getPos());
                  if (!(dSq > 64.0) && dSq < closestDistSq) {
                     closestDistSq = dSq;
                     closestSign = sign;
                  }
               }
            }
         }

         if (closestSign != null) {
            targetSignPos = closestSign.getPos();
         }
      }
   }

   public static void printDebugSigns() {
      Minecraft mc = Minecraft.getMinecraft();
      if (mc.world != null && mc.player != null) {
         EntityPlayerSP player = mc.player;
         player.sendMessage(new TextComponentString(TextFormatting.GOLD + "[AutoAim Debug] Đang quét các bảng xung quanh..."));
         int count = 0;

         for (TileEntity te : mc.world.loadedTileEntityList) {
            if (te instanceof TileEntitySign) {
               TileEntitySign sign = (TileEntitySign)te;
               double dist = Math.sqrt(player.getDistanceSq(sign.getPos()));
               if (!(dist > 12.0)) {
                  StringBuilder sb = new StringBuilder();

                  for (int i = 0; i < 4; i++) {
                     String line = cleanText(sign.signText[i].getUnformattedText());
                     if (!line.isEmpty()) {
                        sb.append("[").append(line).append("] ");
                     }
                  }

                  player.sendMessage(
                     new TextComponentString(
                        TextFormatting.GRAY
                           + String.format("%.1fm: %s tai (%d, %d, %d)", dist, sb.toString(), sign.getPos().getX(), sign.getPos().getY(), sign.getPos().getZ())
                     )
                  );
                  count++;
               }
            }
         }

         player.sendMessage(new TextComponentString(TextFormatting.YELLOW + "[AutoAim Debug] Tìm thấy " + count + " bảng."));
      }
   }

   public static void startAimLock(String itemTarget) {
      Minecraft mc = Minecraft.getMinecraft();
      if (mc.player != null) {
         if (itemTarget == null || itemTarget.trim().isEmpty()) {
            itemTarget = "gunpowder";
         }

         lastItemTarget = itemTarget.trim().toLowerCase();
         boolean found = findTargetSign(lastItemTarget);
         if (found) {
            isAimLockActive = true;
            outOfRangeTicks = 0;
            stuckTicks = 0;
            isWarmupActive = false;
            if (mc.player != null) {
               anchorPos = new Vec3d(mc.player.posX, mc.player.posY, mc.player.posZ);
            }

            if (mc.gameSettings != null) {
               mc.gameSettings.pauseOnLostFocus = false;
            }

            mc.player.setSprinting(false);
            String displayName = isGunpowderTarget(lastItemTarget) ? "THUỐC SÚNG (Gunpowder)" : lastItemTarget.toUpperCase();
            mc.player.sendMessage(new TextComponentString(TextFormatting.GREEN + "[AutoAim] ĐÃ BẬT GHIM BẢNG [Sell] " + displayName));
            mc.player
               .sendMessage(
                  new TextComponentString(TextFormatting.AQUA + "-> Camera ghim vào bảng mượt như người thật. Bạn thoải mái đè chuột phải để bán đồ!")
               );
            mc.player
               .sendMessage(new TextComponentString(TextFormatting.GOLD + "-> Tự bước lại quầy nếu bị đẩy. Chuột hoàn toàn tự do, không bị chiếm chuột."));
         }
      }
   }

   public static void toggleAimLock(String itemTarget) {
      Minecraft mc = Minecraft.getMinecraft();
      if (mc.player != null) {
         if (isAimLockActive) {
            stopAimLock(mc);
            mc.player.sendMessage(new TextComponentString(TextFormatting.RED + "[AutoAim] ĐÃ TẮT GHIM CAMERA."));
         } else {
            startAimLock(itemTarget);
         }
      }
   }

   public static void stopAimLock(Minecraft mc) {
      isAimLockActive = false;
      outOfRangeTicks = 0;
      stuckTicks = 0;
      isWarmupActive = false;
      anchorPos = null;
      stopAutoWalk(mc);
   }

   public static void stopAutoWalk(Minecraft mc) {
      isAutoWalking = false;
      if (mc != null && mc.player != null) {
         mc.player.setSprinting(false);
      }
   }

   private static void updateCameraSmooth(Minecraft mc, float partialTicks) {
      if (isAimLockActive && targetSignPos != null && mc.player != null && mc.world != null) {
         if (mc.currentScreen == null) {
            if (!mc.inGameHasFocus || mc.mouseHelper == null || Math.abs(mc.mouseHelper.deltaX) <= 1 && Math.abs(mc.mouseHelper.deltaY) <= 1) {
               if (!isAimingAtSign(mc) || isAutoWalking) {
                  EntityPlayerSP player = mc.player;
                  double eyeX = player.lastTickPosX + (player.posX - player.lastTickPosX) * (double)partialTicks;
                  double eyeY = player.lastTickPosY + (player.posY - player.lastTickPosY) * (double)partialTicks + (double)player.getEyeHeight();
                  double eyeZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * (double)partialTicks;
                  Vec3d eyePos = new Vec3d(eyeX, eyeY, eyeZ);

                  IBlockState state = mc.world.getBlockState(targetSignPos);
                  AxisAlignedBB bbox = state.getBoundingBox(mc.world, targetSignPos);

                  double centerX = (double)targetSignPos.getX() + (bbox.minX + bbox.maxX) / 2.0;
                  double centerZ = (double)targetSignPos.getZ() + (bbox.minZ + bbox.maxZ) / 2.0;
                  double topY = (double)targetSignPos.getY() + Math.max(bbox.maxY - 0.08, 0.65) + yOffset;
                  double midY = (double)targetSignPos.getY() + 0.35 + yOffset;

                  Vec3d[] candidates = new Vec3d[] {
                     new Vec3d(centerX, topY, centerZ),
                     new Vec3d(centerX, midY, centerZ),
                     new Vec3d((double)targetSignPos.getX() + bbox.minX + 0.1, topY, centerZ),
                     new Vec3d((double)targetSignPos.getX() + bbox.maxX - 0.1, topY, centerZ),
                     new Vec3d(centerX, topY, (double)targetSignPos.getZ() + bbox.minZ + 0.1),
                     new Vec3d(centerX, topY, (double)targetSignPos.getZ() + bbox.maxZ - 0.1)
                  };

                  Vec3d target = null;
                  for (Vec3d cand : candidates) {
                     boolean occluded = false;
                     for (EntityPlayer other : mc.world.playerEntities) {
                        if (other != null && other != player && !other.isSpectator()) {
                           AxisAlignedBB obb = other.getEntityBoundingBox();
                           if (obb != null && obb.calculateIntercept(eyePos, cand) != null) {
                              occluded = true;
                              break;
                           }
                        }
                     }
                     if (!occluded) {
                        target = cand;
                        break;
                     }
                  }

                  if (target == null) {
                     target = candidates[0];
                  }

                  double dx = eyeX - target.x;
                  double dy = eyeY - target.y;
                  double dz = eyeZ - target.z;
                  float targetYaw = (float)(MathHelper.atan2(dx, -dz) * (180.0 / Math.PI));
                  double dist = Math.sqrt(dx * dx + dz * dz);
                  float targetPitch = (float)(MathHelper.atan2(dy, dist) * (180.0 / Math.PI));
                  targetYaw = MathHelper.wrapDegrees(targetYaw);
                  float deltaYaw = MathHelper.wrapDegrees(targetYaw - player.rotationYaw);
                  float deltaPitch = targetPitch - player.rotationPitch;
                  boolean aimingAtSign = isAimingAtSign(mc);
                  float sens = mc.gameSettings != null ? mc.gameSettings.mouseSensitivity : 0.5F;
                  float f = sens * 0.6F + 0.2F;
                  float gcd = f * f * f * 8.0F * 0.15F;
                  float totalDiff = (float)Math.sqrt((double)(deltaYaw * deltaYaw + deltaPitch * deltaPitch));
                  float smoothFactor;
                  if (aimingAtSign) {
                     smoothFactor = 0.14F;
                  } else if (totalDiff > 60.0F) {
                     smoothFactor = 0.18F;
                  } else if (totalDiff > 25.0F) {
                     smoothFactor = 0.26F;
                  } else if (totalDiff > 8.0F) {
                     smoothFactor = 0.32F;
                  } else {
                     smoothFactor = 0.22F;
                  }

                  float stepYaw = deltaYaw * smoothFactor;
                  float stepPitch = deltaPitch * smoothFactor;
                  float maxTurnRate = aimingAtSign ? 3.0F : 9.5F;
                  stepYaw = MathHelper.clamp(stepYaw, -maxTurnRate, maxTurnRate);
                  stepPitch = MathHelper.clamp(stepPitch, -maxTurnRate, maxTurnRate);
                  if (gcd > 1.0E-4F) {
                     stepYaw = (float)Math.round(stepYaw / gcd) * gcd;
                     stepPitch = (float)Math.round(stepPitch / gcd) * gcd;
                  }

                  player.rotationYaw += stepYaw;
                  player.rotationPitch += stepPitch;
               }
            }
         }
      }
   }

   public static boolean isAimingAtSign(Minecraft mc) {
      if (mc != null && mc.world != null && mc.objectMouseOver != null && targetSignPos != null) {
         if (mc.objectMouseOver.typeOfHit != Type.BLOCK) {
            return false;
         } else {
            BlockPos hitPos = mc.objectMouseOver.getBlockPos();
            if (!targetSignPos.equals(hitPos)) {
               return false;
            } else {
               TileEntity te = mc.world.getTileEntity(hitPos);
               if (te instanceof TileEntitySign) {
                  TileEntitySign sign = (TileEntitySign)te;
                  if (isBuySign(sign)) {
                     return false;
                  }

                  if (!isMatchingSellSign(sign, lastItemTarget)) {
                     return false;
                  }
               }

               return true;
            }
         }
      } else {
         return false;
      }
   }

   public static boolean isLookingAtAnyBuySign(Minecraft mc) {
      if (mc == null || mc.world == null || mc.objectMouseOver == null) {
         return false;
      } else if (mc.objectMouseOver.typeOfHit != Type.BLOCK) {
         return false;
      } else {
         BlockPos pos = mc.objectMouseOver.getBlockPos();
         TileEntity te = mc.world.getTileEntity(pos);
         if (te instanceof TileEntitySign) {
            TileEntitySign sign = (TileEntitySign)te;
            if (isBuySign(sign)) {
               return true;
            }

            if (targetSignPos != null && pos.getY() < targetSignPos.getY()) {
               return true;
            }
         }

         return false;
      }
   }

   public static boolean clickTargetSign() {
      Minecraft mc = Minecraft.getMinecraft();
      if (mc != null && mc.world != null && mc.player != null && mc.playerController != null && targetSignPos != null) {
         if (!isAimingAtSign(mc)) {
            return false;
         } else {
            EnumFacing facing = mc.objectMouseOver.sideHit;
            Vec3d hitVec = mc.objectMouseOver.hitVec;
            mc.playerController.processRightClickBlock(mc.player, mc.world, targetSignPos, facing, hitVec, EnumHand.MAIN_HAND);
            if (mc.player != null && (!mc.player.isSwingInProgress || mc.player.swingProgressInt >= 3)) {
               mc.player.isSwingInProgress = true;
               mc.player.swingProgressInt = -1;
               mc.player.swingingHand = EnumHand.MAIN_HAND;
            }

            return true;
         }
      } else {
         return false;
      }
   }

   @SubscribeEvent
   public void onRenderTick(RenderTickEvent event) {
      if (event.phase == Phase.START) {
         Minecraft mc = Minecraft.getMinecraft();
         updateCameraSmooth(mc, event.renderTickTime);
      }
   }

   private static boolean isAnotherPlayerBlocking(Minecraft mc, EntityPlayerSP player) {
      if (mc.world != null && player != null && targetSignPos != null) {
         double toSignX = (double)targetSignPos.getX() + 0.5 - player.posX;
         double toSignZ = (double)targetSignPos.getZ() + 0.5 - player.posZ;

         for (EntityPlayer other : mc.world.playerEntities) {
            if (other != null && other != player) {
               double d = (double)player.getDistance(other);
               if (d < 1.3) {
                  double dx = other.posX - player.posX;
                  double dz = other.posZ - player.posZ;
                  double dot = dx * toSignX + dz * toSignZ;
                  if (dot > 0.0) {
                     return true;
                  }
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   @SubscribeEvent
   public void onInputUpdate(InputUpdateEvent event) {
      if (isAimLockActive && targetSignPos != null) {
         Minecraft mc = Minecraft.getMinecraft();
         if (mc.currentScreen == null) {
            if (isAutoWalking && !isWarmupActive && targetSignPos != null && mc.player != null) {
               if (isAimingAtSign(mc)) {
                  return;
               }

               EntityPlayerSP player = mc.player;
               double dx = player.posX - ((double)targetSignPos.getX() + 0.5);
               double dz = player.posZ - ((double)targetSignPos.getZ() + 0.5);
               double distXZ = Math.sqrt(dx * dx + dz * dz);
               boolean playerBlockingAhead = isAnotherPlayerBlocking(mc, player);
               if (distXZ > 2.2) {
                  double toSignX = (double)targetSignPos.getX() + 0.5 - player.posX;
                  double toSignZ = (double)targetSignPos.getZ() + 0.5 - player.posZ;
                  float moveAngle = (float)(MathHelper.atan2(toSignZ, toSignX) * (180.0 / Math.PI)) - 90.0F;
                  float diff = MathHelper.wrapDegrees(moveAngle - player.rotationYaw);
                  if (Math.abs(diff) <= 60.0F) {
                     event.getMovementInput().moveForward = 0.8F;
                     event.getMovementInput().forwardKeyDown = true;
                     event.getMovementInput().moveStrafe = 0.0F;
                  } else if (diff > 60.0F && diff < 120.0F) {
                     event.getMovementInput().moveForward = 0.2F;
                     event.getMovementInput().moveStrafe = -0.8F;
                  } else if (diff < -60.0F && diff > -120.0F) {
                     event.getMovementInput().moveForward = 0.2F;
                     event.getMovementInput().moveStrafe = 0.8F;
                  } else {
                     event.getMovementInput().moveForward = -0.8F;
                  }
               } else if (playerBlockingAhead) {
                  event.getMovementInput().moveForward = 0.0F;
                  event.getMovementInput().moveStrafe = 0.6F;
               }

               mc.player.setSprinting(false);
               if (mc.gameSettings != null) {
                  KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
               }
            }
         }
      }
   }

   @SubscribeEvent
   public void onClientTick(ClientTickEvent event) {
      if (event.phase == Phase.END) {
         if (isAimLockActive) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.player != null && mc.world != null && mc.gameSettings != null) {
               if (mc.currentScreen == null) {
                  EntityPlayerSP player = mc.player;
                  if (player.ticksExisted % 5 == 0) {
                     scanAndSwitchTargetSign(mc);
                  }

                  if (targetSignPos == null) {
                     if (isAutoWalking) {
                        stopAutoWalk(mc);
                     }
                  } else {
                     if (isAimingAtSign(mc)) {
                        if (isAutoWalking) {
                           stopAutoWalk(mc);
                        }

                        outOfRangeTicks = 0;
                        stuckTicks = 0;
                     } else if (autoReturnEnabled && !isWarmupActive) {
                        outOfRangeTicks++;
                        if (!isAutoWalking && outOfRangeTicks >= 8) {
                           isAutoWalking = true;
                           stuckTicks = 0;
                           lastCheckX = player.posX;
                           lastCheckZ = player.posZ;
                        }

                        if (isAutoWalking) {
                           player.setSprinting(false);
                           if (isAimingAtSign(mc)) {
                              stopAutoWalk(mc);
                              outOfRangeTicks = 0;
                              stuckTicks = 0;
                           } else if (player.collidedHorizontally) {
                              double moved = Math.sqrt(Math.pow(player.posX - lastCheckX, 2.0) + Math.pow(player.posZ - lastCheckZ, 2.0));
                              if (moved < 0.03) {
                                 stuckTicks++;
                              }

                              if (stuckTicks >= 6) {
                                 stopAutoWalk(mc);
                                 outOfRangeTicks = 0;
                                 stuckTicks = 0;
                              }
                           } else {
                              stuckTicks = 0;
                           }

                           lastCheckX = player.posX;
                           lastCheckZ = player.posZ;
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @SubscribeEvent
   public void onChatReceived(ClientChatReceivedEvent event) {
      if (event.getMessage() != null) {
         String text = cleanText(event.getMessage().getUnformattedText()).toLowerCase();
         if (text.contains("dịch chuyển sẽ diễn ra") || text.contains("đừng chạy nhảy")) {
            isWarmupActive = true;
            stopAutoWalk(Minecraft.getMinecraft());
         } else if (text.contains("yêu cầu dịch chuyển đã bị hủy") || text.contains("đã dịch chuyển") || text.contains("mở kho")) {
            isWarmupActive = false;
         }
      }
   }
}
