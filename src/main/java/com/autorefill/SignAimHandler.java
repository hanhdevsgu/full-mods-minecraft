package com.autorefill;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

public class SignAimHandler {

    // Trạng thái ghim camera theo thời gian thực (AimLock)
    public static boolean isAimLockActive = false;
    public static BlockPos targetSignPos = null;
    public static String configuredTarget = "gunpowder";
    public static boolean hasUserConfiguredTarget = false;
    public static String lastItemTarget = "gunpowder";
    public static int targetItemId = 289;
    public static String targetDisplayName = "Thuốc súng (Gunpowder) [ID: 289]";
    public static final List<String> targetAliases = new ArrayList<String>();
    public static double yOffset = 0.0;

    public static String getEffectiveTarget() {
        if (hasUserConfiguredTarget && configuredTarget != null && !configuredTarget.trim().isEmpty()) {
            return configuredTarget;
        }
        int cfgId = com.zorotool.macrorecorder.ConfigHelper.getFarmItemId();
        if (cfgId > 0 && cfgId != 289) {
            return String.valueOf(cfgId);
        }
        return "gunpowder";
    }

    public static void resetToDefaultTarget() {
        hasUserConfiguredTarget = false;
        configuredTarget = "gunpowder";
        lastItemTarget = "gunpowder";
        targetItemId = 289;
        targetDisplayName = "Thuốc súng (Gunpowder) [ID: 289]";
        resolveTarget("gunpowder");
        try {
            com.zorotool.macrorecorder.ConfigHelper.setFarmItemId(289);
        } catch (Exception ignored) {}
    }

    // Vị trí gốc đứng bán (anchor) để chỉ bước lại khi bị đẩy lùi khỏi điểm này
    public static Vec3d anchorPos = null;
    public static boolean isWarmupActive = false;

    // Tự động đi lại quầy khi bị người khác đẩy ra xa (Anti-Push / Auto-Return)
    public static boolean autoReturnEnabled = true;
    private static boolean isAutoWalking = false;

    // Bộ đếm trễ phản xạ tự nhiên của con người
    private static int outOfRangeTicks = 0;
    private static int stuckTicks = 0;
    private static double lastCheckX = 0;
    private static double lastCheckZ = 0;

    // Cự ly chuẩn tự nhiên:
    private static final double TRIGGER_WALK_DIST_XZ = 2.4; 
    private static final double IDEAL_SELL_DIST_XZ = 1.7;   

    public static String cleanText(String input) {
        if (input == null) return "";
        return input.replaceAll("§[0-9a-fk-orA-FK-OR]", "").trim();
    }

    public static boolean isGunpowderTarget(String target) {
        if (target == null) return false;
        String t = target.toLowerCase().trim();
        return t.equals("gunpowder") || t.equals("thuoc sung") || t.equals("thuốc súng") 
            || t.equals("thuocsung") || t.equals("powder") || t.equals("289");
    }

    public static boolean isReedsTarget(String target) {
        if (target == null) return false;
        String t = target.toLowerCase().trim();
        return t.equals("reeds") || t.equals("reed") || t.equals("sugar_cane") 
            || t.equals("sugarcane") || t.equals("sugar cane") || t.equals("mia") || t.equals("mía") || t.equals("338");
    }

    public static void resolveTarget(String input) {
        targetAliases.clear();
        if (input == null || input.trim().isEmpty()) {
            input = "gunpowder";
        }
        String clean = input.replaceAll("[<>\\[\\]]", "").trim();
        if (clean.isEmpty()) {
            clean = "gunpowder";
        }
        lastItemTarget = clean.toLowerCase();

        int parsedId = -1;
        int parsedMeta = -1;

        if (clean.matches("^\\d+(:\\d+)?$")) {
            if (clean.contains(":")) {
                String[] parts = clean.split(":");
                try {
                    parsedId = Integer.parseInt(parts[0]);
                    parsedMeta = Integer.parseInt(parts[1]);
                } catch (Exception ignored) {}
            } else {
                try {
                    parsedId = Integer.parseInt(clean);
                } catch (Exception ignored) {}
            }
        } else {
            String lower = clean.toLowerCase();
            if (isGunpowderTarget(lower)) {
                parsedId = 289;
            } else if (isReedsTarget(lower)) {
                parsedId = 338;
            } else if (lower.equals("beef") || lower.equals("raw_beef") || lower.equals("raw beef") || lower.equals("thit bo") || lower.equals("thịt bò") || lower.equals("bo") || lower.equals("bò")) {
                parsedId = 363;
            } else if (lower.equals("steak") || lower.equals("cooked_beef") || lower.equals("cooked beef") || lower.equals("thit bo chin") || lower.equals("thịt bò chín") || lower.equals("bit tet") || lower.equals("bít tết")) {
                parsedId = 364;
            } else if (lower.equals("porkchop") || lower.equals("raw_porkchop") || lower.equals("raw porkchop") || lower.equals("thit heo") || lower.equals("thịt heo") || lower.equals("thit lon") || lower.equals("thịt lợn")) {
                parsedId = 319;
            } else if (lower.equals("cooked_porkchop") || lower.equals("cooked porkchop") || lower.equals("thit heo chin") || lower.equals("thịt heo chín") || lower.equals("thit lon chin") || lower.equals("thịt lợn chín")) {
                parsedId = 320;
            } else if (lower.equals("chicken") || lower.equals("raw_chicken") || lower.equals("raw chicken") || lower.equals("thit ga") || lower.equals("thịt gà") || lower.equals("ga") || lower.equals("gà")) {
                parsedId = 365;
            } else if (lower.equals("cooked_chicken") || lower.equals("cooked chicken") || lower.equals("thit ga chin") || lower.equals("thịt gà chín")) {
                parsedId = 366;
            } else if (lower.equals("mutton") || lower.equals("raw_mutton") || lower.equals("raw mutton") || lower.equals("thit cuu") || lower.equals("thịt cừu")) {
                parsedId = 423;
            } else if (lower.equals("cooked_mutton") || lower.equals("cooked mutton") || lower.equals("thit cuu chin") || lower.equals("thịt cừu chín")) {
                parsedId = 424;
            } else if (lower.equals("rabbit") || lower.equals("raw_rabbit") || lower.equals("thit tho") || lower.equals("thịt thỏ")) {
                parsedId = 411;
            } else if (lower.equals("cooked_rabbit") || lower.equals("thit tho chin") || lower.equals("thịt thỏ chín")) {
                parsedId = 412;
            } else if (lower.equals("leather") || lower.equals("da") || lower.equals("da bo") || lower.equals("da bò") || lower.equals("da thuoc") || lower.equals("da thuộc")) {
                parsedId = 334;
            } else if (lower.equals("feather") || lower.equals("long ga") || lower.equals("lông gà") || lower.equals("long vu") || lower.equals("lông vũ")) {
                parsedId = 288;
            } else if (lower.equals("string") || lower.equals("to") || lower.equals("tơ") || lower.equals("to nhen") || lower.equals("tơ nhện") || lower.equals("chi") || lower.equals("chỉ")) {
                parsedId = 287;
            } else if (lower.equals("bone") || lower.equals("xuong") || lower.equals("xương")) {
                parsedId = 352;
            } else if (lower.equals("spider_eye") || lower.equals("spider eye") || lower.equals("mat nhen") || lower.equals("mắt nhện")) {
                parsedId = 375;
            } else if (lower.equals("rotten_flesh") || lower.equals("rotten flesh") || lower.equals("thit thoi") || lower.equals("thịt thối")) {
                parsedId = 367;
            } else if (lower.equals("egg") || lower.equals("trung") || lower.equals("trứng")) {
                parsedId = 344;
            } else if (lower.equals("slimeball") || lower.equals("slime ball") || lower.equals("bong slime") || lower.equals("bóng slime")) {
                parsedId = 341;
            } else if (lower.equals("sat") || lower.equals("sắt") || lower.equals("khoi sat") || lower.equals("khối sắt") || lower.equals("iron_block") || lower.equals("iron block")) {
                parsedId = 42;
            } else if (lower.equals("thoi sat") || lower.equals("thỏi sắt") || lower.equals("iron_ingot") || lower.equals("iron ingot") || lower.equals("iron")) {
                parsedId = 265;
            } else if (lower.equals("vang") || lower.equals("vàng") || lower.equals("khoi vang") || lower.equals("khối vàng") || lower.equals("gold_block") || lower.equals("gold block")) {
                parsedId = 41;
            } else if (lower.equals("thoi vang") || lower.equals("thỏi vàng") || lower.equals("gold_ingot") || lower.equals("gold ingot") || lower.equals("gold")) {
                parsedId = 266;
            } else if (lower.equals("kim cuong") || lower.equals("kim cương") || lower.equals("diamond")) {
                parsedId = 264;
            } else if (lower.equals("khoi kim cuong") || lower.equals("khối kim cương") || lower.equals("diamond_block") || lower.equals("diamond block")) {
                parsedId = 57;
            } else if (lower.equals("tnt") || lower.equals("thuoc no") || lower.equals("thuốc nổ")) {
                parsedId = 46;
            } else {
                try {
                    Item regItem = Item.getByNameOrId(clean);
                    if (regItem != null) {
                        parsedId = Item.getIdFromItem(regItem);
                    } else {
                        Block regBlock = Block.getBlockFromName(clean);
                        if (regBlock != null) {
                            parsedId = Block.getIdFromBlock(regBlock);
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        targetItemId = parsedId;
        String nameResult = clean;

        if (parsedId > 0) {
            targetAliases.add(String.valueOf(parsedId));
            if (parsedMeta >= 0) {
                targetAliases.add(parsedId + ":" + parsedMeta);
            }
            try {
                Item item = Item.getItemById(parsedId);
                if (item != null) {
                    if (item.getRegistryName() != null) {
                        String path = item.getRegistryName().getResourcePath().toLowerCase();
                        targetAliases.add(path);
                        targetAliases.add(path.replace('_', ' '));
                        targetAliases.add(path.replace("_", ""));
                    }
                    ItemStack stack = new ItemStack(item, 1, parsedMeta >= 0 ? parsedMeta : 0);
                    String disp = stack.getDisplayName();
                    if (disp != null && !disp.isEmpty()) {
                        nameResult = disp;
                        targetAliases.add(disp.toLowerCase());
                    }
                }
            } catch (Exception ignored) {}

            try {
                Block block = Block.getBlockById(parsedId);
                if (block != null && block != Blocks.AIR) {
                    if (block.getRegistryName() != null) {
                        String path = block.getRegistryName().getResourcePath().toLowerCase();
                        targetAliases.add(path);
                        targetAliases.add(path.replace('_', ' '));
                        targetAliases.add(path.replace("_", ""));
                    }
                    String loc = block.getLocalizedName();
                    if (loc != null && !loc.isEmpty()) {
                        targetAliases.add(loc.toLowerCase());
                    }
                }
            } catch (Exception ignored) {}

            populateCommonAliases(parsedId);
        }

        targetAliases.add(clean.toLowerCase());
        targetAliases.add(clean.toLowerCase().replace('_', ' '));

        if (parsedId == 42) {
            targetDisplayName = "Khối sắt (Iron Block) [ID: 42]";
        } else if (parsedId == 289) {
            targetDisplayName = "Thuốc súng (Gunpowder) [ID: 289]";
        } else if (parsedId == 338) {
            targetDisplayName = "Mía (Sugar Cane) [ID: 338]";
        } else if (parsedId == 363) {
            targetDisplayName = "Thịt bò (Raw Beef) [ID: 363]";
        } else if (parsedId == 364) {
            targetDisplayName = "Bít tết (Steak) [ID: 364]";
        } else if (parsedId == 319) {
            targetDisplayName = "Thịt heo (Raw Porkchop) [ID: 319]";
        } else if (parsedId == 320) {
            targetDisplayName = "Thịt heo chín (Cooked Porkchop) [ID: 320]";
        } else if (parsedId == 365) {
            targetDisplayName = "Thịt gà (Raw Chicken) [ID: 365]";
        } else if (parsedId == 366) {
            targetDisplayName = "Thịt gà chín (Cooked Chicken) [ID: 366]";
        } else if (parsedId == 423) {
            targetDisplayName = "Thịt cừu (Raw Mutton) [ID: 423]";
        } else if (parsedId == 424) {
            targetDisplayName = "Thịt cừu chín (Cooked Mutton) [ID: 424]";
        } else if (parsedId == 411) {
            targetDisplayName = "Thịt thỏ (Raw Rabbit) [ID: 411]";
        } else if (parsedId == 412) {
            targetDisplayName = "Thịt thỏ chín (Cooked Rabbit) [ID: 412]";
        } else if (parsedId == 334) {
            targetDisplayName = "Da thuộc (Leather) [ID: 334]";
        } else if (parsedId == 288) {
            targetDisplayName = "Lông gà (Feather) [ID: 288]";
        } else if (parsedId == 287) {
            targetDisplayName = "Tơ nhện (String) [ID: 287]";
        } else if (parsedId == 352) {
            targetDisplayName = "Xương (Bone) [ID: 352]";
        } else if (parsedId == 375) {
            targetDisplayName = "Mắt nhện (Spider Eye) [ID: 375]";
        } else if (parsedId == 367) {
            targetDisplayName = "Thịt thối (Rotten Flesh) [ID: 367]";
        } else if (parsedId == 344) {
            targetDisplayName = "Trứng gà (Egg) [ID: 344]";
        } else if (parsedId == 341) {
            targetDisplayName = "Bóng Slime (Slimeball) [ID: 341]";
        } else if (parsedId == 368) {
            targetDisplayName = "Ngọc Ender (Ender Pearl) [ID: 368]";
        } else if (parsedId == 369) {
            targetDisplayName = "Que quỷ lửa (Blaze Rod) [ID: 369]";
        } else if (parsedId == 372) {
            targetDisplayName = "Bướu địa ngục (Nether Wart) [ID: 372]";
        } else if (parsedId == 81) {
            targetDisplayName = "Xương rồng (Cactus) [ID: 81]";
        } else if (parsedId == 391) {
            targetDisplayName = "Cà rốt (Carrot) [ID: 391]";
        } else if (parsedId == 392) {
            targetDisplayName = "Khoai tây (Potato) [ID: 392]";
        } else if (parsedId == 296) {
            targetDisplayName = "Lúa mì (Wheat) [ID: 296]";
        } else if (parsedId == 360) {
            targetDisplayName = "Dưa hấu (Melon) [ID: 360]";
        } else if (parsedId == 86) {
            targetDisplayName = "Bí ngô (Pumpkin) [ID: 86]";
        } else if (parsedId == 265) {
            targetDisplayName = "Thỏi sắt (Iron Ingot) [ID: 265]";
        } else if (parsedId == 41) {
            targetDisplayName = "Khối vàng (Gold Block) [ID: 41]";
        } else if (parsedId == 266) {
            targetDisplayName = "Thỏi vàng (Gold Ingot) [ID: 266]";
        } else if (parsedId == 57) {
            targetDisplayName = "Khối kim cương (Diamond Block) [ID: 57]";
        } else if (parsedId == 264) {
            targetDisplayName = "Kim cương (Diamond) [ID: 264]";
        } else if (parsedId == 133) {
            targetDisplayName = "Khối ngọc lục bảo (Emerald Block) [ID: 133]";
        } else if (parsedId == 388) {
            targetDisplayName = "Ngọc lục bảo (Emerald) [ID: 388]";
        } else if (parsedId == 152) {
            targetDisplayName = "Khối đá đỏ (Redstone Block) [ID: 152]";
        } else if (parsedId == 331) {
            targetDisplayName = "Đá đỏ (Redstone) [ID: 331]";
        } else if (parsedId == 173) {
            targetDisplayName = "Khối than (Coal Block) [ID: 173]";
        } else if (parsedId == 263) {
            targetDisplayName = "Than (Coal) [ID: 263]";
        } else if (parsedId == 46) {
            targetDisplayName = "Thuốc nổ (TNT) [ID: 46]";
        } else if (parsedId > 0) {
            targetDisplayName = nameResult + " [ID: " + parsedId + "]";
        } else {
            targetDisplayName = clean.toUpperCase();
        }
    }

    private static void populateCommonAliases(int id) {
        switch (id) {
            case 42:
                targetAliases.add("iron_block");
                targetAliases.add("iron block");
                targetAliases.add("ironblock");
                targetAliases.add("khối sắt");
                targetAliases.add("khoi sat");
                targetAliases.add("sắt khối");
                targetAliases.add("sat khoi");
                targetAliases.add("cục sắt");
                targetAliases.add("cuc sat");
                targetAliases.add("block of iron");
                break;
            case 265:
                targetAliases.add("iron_ingot");
                targetAliases.add("iron ingot");
                targetAliases.add("thỏi sắt");
                targetAliases.add("thoi sat");
                targetAliases.add("sắt thỏi");
                targetAliases.add("sat thoi");
                targetAliases.add("iron");
                break;
            case 41:
                targetAliases.add("gold_block");
                targetAliases.add("gold block");
                targetAliases.add("khối vàng");
                targetAliases.add("khoi vang");
                targetAliases.add("vàng khối");
                targetAliases.add("vang khoi");
                targetAliases.add("block of gold");
                break;
            case 266:
                targetAliases.add("gold_ingot");
                targetAliases.add("gold ingot");
                targetAliases.add("thỏi vàng");
                targetAliases.add("thoi vang");
                targetAliases.add("gold");
                break;
            case 57:
                targetAliases.add("diamond_block");
                targetAliases.add("diamond block");
                targetAliases.add("khối kim cương");
                targetAliases.add("khoi kim cuong");
                targetAliases.add("block of diamond");
                break;
            case 264:
                targetAliases.add("diamond");
                targetAliases.add("kim cương");
                targetAliases.add("kim cuong");
                targetAliases.add("viên kim cương");
                targetAliases.add("vien kim cuong");
                break;
            case 133:
                targetAliases.add("emerald_block");
                targetAliases.add("emerald block");
                targetAliases.add("khối ngọc lục bảo");
                targetAliases.add("khoi ngoc luc bao");
                targetAliases.add("block of emerald");
                break;
            case 388:
                targetAliases.add("emerald");
                targetAliases.add("ngọc lục bảo");
                targetAliases.add("ngoc luc bao");
                break;
            case 152:
                targetAliases.add("redstone_block");
                targetAliases.add("redstone block");
                targetAliases.add("khối đá đỏ");
                targetAliases.add("khoi da do");
                targetAliases.add("block of redstone");
                break;
            case 331:
                targetAliases.add("redstone");
                targetAliases.add("đá đỏ");
                targetAliases.add("da do");
                break;
            case 173:
                targetAliases.add("coal_block");
                targetAliases.add("coal block");
                targetAliases.add("khối than");
                targetAliases.add("khoi than");
                targetAliases.add("block of coal");
                break;
            case 263:
                targetAliases.add("coal");
                targetAliases.add("than");
                targetAliases.add("than đá");
                targetAliases.add("than da");
                break;
            case 289:
                targetAliases.add("gunpowder");
                targetAliases.add("thuốc súng");
                targetAliases.add("thuoc sung");
                targetAliases.add("thuocsung");
                targetAliases.add("powder");
                break;
            case 338:
                targetAliases.add("reeds");
                targetAliases.add("reed");
                targetAliases.add("sugar_cane");
                targetAliases.add("sugar cane");
                targetAliases.add("sugarcane");
                targetAliases.add("mía");
                targetAliases.add("mia");
                break;
            case 46:
                targetAliases.add("tnt");
                targetAliases.add("thuốc nổ");
                targetAliases.add("thuoc no");
                break;
            case 81:
                targetAliases.add("cactus");
                targetAliases.add("xương rồng");
                targetAliases.add("xuong rong");
                break;
            case 86:
                targetAliases.add("pumpkin");
                targetAliases.add("bí ngô");
                targetAliases.add("bi ngo");
                break;
            case 103:
            case 360:
                targetAliases.add("melon");
                targetAliases.add("dưa hấu");
                targetAliases.add("dua hau");
                targetAliases.add("miếng dưa hấu");
                targetAliases.add("mieng dua hau");
                break;
            case 372:
                targetAliases.add("nether_wart");
                targetAliases.add("nether wart");
                targetAliases.add("bướu địa ngục");
                targetAliases.add("buou dia nguc");
                break;
            case 369:
                targetAliases.add("blaze_rod");
                targetAliases.add("blaze rod");
                targetAliases.add("que quỷ lửa");
                targetAliases.add("que quy lua");
                break;
            case 352:
                targetAliases.add("bone");
                targetAliases.add("xương");
                targetAliases.add("xuong");
                break;
            case 368:
                targetAliases.add("ender_pearl");
                targetAliases.add("ender pearl");
                targetAliases.add("ngọc ender");
                targetAliases.add("ngoc ender");
                break;
            case 341:
                targetAliases.add("slimeball");
                targetAliases.add("slime ball");
                targetAliases.add("bóng slime");
                targetAliases.add("bong slime");
                break;
            case 391:
                targetAliases.add("carrot");
                targetAliases.add("cà rốt");
                targetAliases.add("ca rot");
                break;
            case 392:
                targetAliases.add("potato");
                targetAliases.add("khoai tây");
                targetAliases.add("khoai tay");
                break;
            case 296:
                targetAliases.add("wheat");
                targetAliases.add("lúa mì");
                targetAliases.add("lua mi");
                break;
            case 363:
                targetAliases.add("beef");
                targetAliases.add("raw_beef");
                targetAliases.add("raw beef");
                targetAliases.add("thịt bò");
                targetAliases.add("thit bo");
                targetAliases.add("bò sống");
                targetAliases.add("bo song");
                targetAliases.add("bò");
                targetAliases.add("bo");
                break;
            case 364:
                targetAliases.add("steak");
                targetAliases.add("cooked_beef");
                targetAliases.add("cooked beef");
                targetAliases.add("thịt bò chín");
                targetAliases.add("thit bo chin");
                targetAliases.add("bít tết");
                targetAliases.add("bit tet");
                break;
            case 319:
                targetAliases.add("porkchop");
                targetAliases.add("raw_porkchop");
                targetAliases.add("raw porkchop");
                targetAliases.add("thịt heo");
                targetAliases.add("thit heo");
                targetAliases.add("thịt lợn");
                targetAliases.add("thit lon");
                break;
            case 320:
                targetAliases.add("cooked_porkchop");
                targetAliases.add("cooked porkchop");
                targetAliases.add("thịt heo chín");
                targetAliases.add("thit heo chin");
                targetAliases.add("thịt lợn chín");
                targetAliases.add("thit lon chin");
                break;
            case 365:
                targetAliases.add("chicken");
                targetAliases.add("raw_chicken");
                targetAliases.add("raw chicken");
                targetAliases.add("thịt gà");
                targetAliases.add("thit ga");
                targetAliases.add("gà sống");
                targetAliases.add("ga song");
                break;
            case 366:
                targetAliases.add("cooked_chicken");
                targetAliases.add("cooked chicken");
                targetAliases.add("thịt gà chín");
                targetAliases.add("thit ga chin");
                break;
            case 423:
                targetAliases.add("mutton");
                targetAliases.add("raw_mutton");
                targetAliases.add("raw mutton");
                targetAliases.add("thịt cừu");
                targetAliases.add("thit cuu");
                break;
            case 424:
                targetAliases.add("cooked_mutton");
                targetAliases.add("cooked mutton");
                targetAliases.add("thịt cừu chín");
                targetAliases.add("thit cuu chin");
                break;
            case 411:
                targetAliases.add("rabbit");
                targetAliases.add("raw_rabbit");
                targetAliases.add("raw rabbit");
                targetAliases.add("thịt thỏ");
                targetAliases.add("thit tho");
                break;
            case 412:
                targetAliases.add("cooked_rabbit");
                targetAliases.add("cooked rabbit");
                targetAliases.add("thịt thỏ chín");
                targetAliases.add("thit tho chin");
                break;
            case 334:
                targetAliases.add("leather");
                targetAliases.add("da");
                targetAliases.add("da bò");
                targetAliases.add("da bo");
                targetAliases.add("da thuộc");
                targetAliases.add("da thuoc");
                break;
            case 288:
                targetAliases.add("feather");
                targetAliases.add("lông gà");
                targetAliases.add("long ga");
                targetAliases.add("lông vũ");
                targetAliases.add("long vu");
                break;
            case 287:
                targetAliases.add("string");
                targetAliases.add("tơ");
                targetAliases.add("to");
                targetAliases.add("tơ nhện");
                targetAliases.add("to nhen");
                targetAliases.add("chỉ");
                targetAliases.add("chi");
                break;
            case 375:
                targetAliases.add("spider_eye");
                targetAliases.add("spider eye");
                targetAliases.add("mắt nhện");
                targetAliases.add("mat nhen");
                break;
            case 367:
                targetAliases.add("rotten_flesh");
                targetAliases.add("rotten flesh");
                targetAliases.add("thịt thối");
                targetAliases.add("thit thoi");
                break;
            case 344:
                targetAliases.add("egg");
                targetAliases.add("trứng");
                targetAliases.add("trung");
                targetAliases.add("trứng gà");
                targetAliases.add("trung ga");
                break;
        }
    }

    /**
     * Kiểm tra xem bảng có phải là bảng MUA (Buy sign) hay không.
     * Hỗ trợ mọi định dạng: [Buy], Buy, [Mua], Mua, B 100, B:100, Giá mua, v.v.
     */
    public static boolean isBuySign(TileEntitySign sign) {
        if (sign == null) return false;
        for (int i = 0; i < 4; i++) {
            String raw = cleanText(sign.signText[i].getUnformattedText()).toLowerCase();
            if (raw.contains("buy") || raw.contains("[buy]")
                || raw.contains("mua") || raw.contains("[mua]")
                || raw.contains("b ") || raw.startsWith("b ") || raw.endsWith(" b") || raw.equals("b")
                || raw.contains("b:") || raw.contains(":b")
                || raw.contains("giá mua") || raw.contains("mua:") || raw.contains("buy:")) {
                return true;
            }
        }
        return false;
    }

    public static boolean isAnySellSign(TileEntitySign sign) {
        if (sign == null) return false;
        if (isBuySign(sign)) return false;
        boolean hasSell = false;
        for (int i = 0; i < 4; i++) {
            String raw = cleanText(sign.signText[i].getUnformattedText()).toLowerCase();
            if (raw.contains("sell") || raw.contains("[sell]") || raw.contains("bán") || raw.contains("[bán]") || raw.contains("s ") || raw.startsWith("s ")) {
                hasSell = true;
            }
        }
        return hasSell;
    }

    public static boolean isMatchingSellSign(TileEntitySign sign, String itemTarget) {
        if (sign == null) return false;
        if (isBuySign(sign)) return false; // TUYỆT ĐỐI KHÔNG BAO GIỜ COI BẢNG BUY LÀ BẢNG SELL!

        StringBuilder sb = new StringBuilder();
        boolean hasSell = false;

        for (int i = 0; i < 4; i++) {
            String raw = cleanText(sign.signText[i].getUnformattedText()).toLowerCase();
            sb.append(" ").append(raw);
            if (raw.contains("sell") || raw.contains("[sell]") || raw.contains("bán") || raw.contains("[bán]") 
                || raw.startsWith("s ") || raw.contains(" s ") || raw.endsWith(" s") || raw.equals("s")
                || raw.contains("s:") || raw.contains(":s")) {
                hasSell = true;
            }
        }

        if (!hasSell) return false;
        if (itemTarget == null || itemTarget.trim().isEmpty() || itemTarget.equals("any") || itemTarget.equals("all")) {
            return true;
        }

        String cleanedTarget = itemTarget.replaceAll("[<>\\[\\]]", "").trim();
        if (targetAliases.isEmpty() || !lastItemTarget.equalsIgnoreCase(cleanedTarget)) {
            resolveTarget(cleanedTarget);
        }

        String combined = sb.toString();
        for (String alias : targetAliases) {
            if (alias == null || alias.trim().isEmpty()) continue;
            String a = alias.toLowerCase().trim();
            if (a.matches("^\\d+$")) {
                if (combined.matches(".*(^|[^0-9$])" + a + "(:\\d+)?([^0-9]|$).*")) {
                    return true;
                }
            } else {
                if (combined.contains(a)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isSignVisible(EntityPlayerSP player, BlockPos pos) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null) return false;

        Vec3d eyePos = player.getPositionEyes(1.0F);
        IBlockState state = mc.world.getBlockState(pos);
        AxisAlignedBB bbox = state.getBoundingBox(mc.world, pos);
        Vec3d signCenter = new Vec3d(
            pos.getX() + (bbox.minX + bbox.maxX) / 2.0,
            pos.getY() + 0.35 + yOffset,
            pos.getZ() + (bbox.minZ + bbox.maxZ) / 2.0
        );

        RayTraceResult hit = mc.world.rayTraceBlocks(eyePos, signCenter, false, true, false);
        return (hit == null || hit.typeOfHit == RayTraceResult.Type.MISS || hit.getBlockPos().equals(pos));
    }

    /**
     * Tìm bảng phù hợp gần nhất khi khởi động ghim
     */
    public static boolean findTargetSign(String itemTarget) {
        return findTargetSign(itemTarget, false);
    }

    public static boolean findTargetSign(String itemTarget, boolean verbose) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null) return false;

        EntityPlayerSP player = mc.player;
        // Chỉ quét tìm bảng Sell khi người chơi ở quầy Shop (Y xấp xỉ 151)
        if (Math.abs(player.posY - 151.0) > 4.0) {
            return false;
        }

        if (itemTarget == null || itemTarget.trim().isEmpty()) {
            itemTarget = "gunpowder";
        }
        resolveTarget(itemTarget);

        List<TileEntitySign> matchedSigns = new ArrayList<TileEntitySign>();
        double maxDistSq = 256.0; // 16 block

        for (TileEntity te : mc.world.loadedTileEntityList) {
            if (te instanceof TileEntitySign) {
                TileEntitySign sign = (TileEntitySign) te;
                double distSq = player.getDistanceSq(sign.getPos());
                if (distSq > maxDistSq) continue;

                if (isMatchingSellSign(sign, lastItemTarget)) {
                    matchedSigns.add(sign);
                }
            }
        }

        // Nếu không tìm thấy bảng khớp từ khóa và itemTarget là "any" thì mới tìm bất kỳ bảng sell nào
        if (matchedSigns.isEmpty() && (lastItemTarget.equals("any") || lastItemTarget.equals("all"))) {
            for (TileEntity te : mc.world.loadedTileEntityList) {
                if (te instanceof TileEntitySign) {
                    TileEntitySign sign = (TileEntitySign) te;
                    double distSq = player.getDistanceSq(sign.getPos());
                    if (distSq > 144.0) continue; // 12 block

                    if (isAnySellSign(sign)) {
                        matchedSigns.add(sign);
                    }
                }
            }
        }

        if (matchedSigns.isEmpty()) {
            if (verbose) {
                player.sendMessage(new TextComponentString(TextFormatting.RED + 
                    "[AutoAim] Không tìm thấy bảng Sell cho '" + targetDisplayName + "' trong khu vực xung quanh!"));
            }
            return false;
        }

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

    /**
     * Tự động quét kiểm tra bảng:
     * - Khi đang đứng ở quầy và bảng hiện tại vẫn là bảng nông sản cần bán (trong 8 block):
     *   TUYỆT ĐỐI GIỮ NGUYÊN, không bao giờ tự ý nhảy sang bảng khác (như TNT, NetherStar, SpiderEye...)
     * - Chỉ tìm bảng mới khi bảng cũ biến mất hoặc bị dịch chuyển sang phòng khác (> 8 block).
     */
    private static void scanAndSwitchTargetSign(Minecraft mc) {
        if (mc.world == null || mc.player == null) return;
        EntityPlayerSP player = mc.player;

        // Nếu đã có bảng ghim và bảng đó vẫn khớp món đồ cần bán (thuốc súng/mía) trong cự ly 8 block:
        // TUYỆT ĐỐI KHÔNG ĐỔI SANG BẢNG KHÁC DÙ BỊ ĐẨY SANG BÊN NÀO!
        if (targetSignPos != null) {
            TileEntity te = mc.world.getTileEntity(targetSignPos);
            if (te instanceof TileEntitySign) {
                TileEntitySign currentSign = (TileEntitySign) te;
                if (isMatchingSellSign(currentSign, lastItemTarget) && player.getDistanceSq(targetSignPos) <= 64.0) {
                    return; // Đang ngắm đúng bảng món đồ cần bán, giữ chặt 100%!
                }
            }
        }

        // Chỉ khi bảng cũ không còn hoặc bị đẩy ra quá 8 block mới quét tìm bảng thuốc súng khác:
        TileEntitySign closestSign = null;
        double closestDistSq = Double.MAX_VALUE;

        for (TileEntity te : mc.world.loadedTileEntityList) {
            if (te instanceof TileEntitySign) {
                TileEntitySign sign = (TileEntitySign) te;
                // BẮT BUỘC PHẢI KHỚP ĐÚNG MÓN ĐỒ (lastItemTarget), KHÔNG BAO GIỜ LẤY BẢNG MÓN KHÁC!
                if (!isMatchingSellSign(sign, lastItemTarget)) continue;

                double dSq = player.getDistanceSq(sign.getPos());
                if (dSq > 64.0) continue; // trong vòng 8 block

                if (dSq < closestDistSq) {
                    closestDistSq = dSq;
                    closestSign = sign;
                }
            }
        }

        if (closestSign != null) {
            targetSignPos = closestSign.getPos();
        }
    }

    public static void printDebugSigns() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null) return;
        EntityPlayerSP player = mc.player;
        player.sendMessage(new TextComponentString(TextFormatting.GOLD + "[AutoAim Debug] Đang quét các bảng xung quanh..."));
        int count = 0;
        for (TileEntity te : mc.world.loadedTileEntityList) {
            if (te instanceof TileEntitySign) {
                TileEntitySign sign = (TileEntitySign) te;
                double dist = Math.sqrt(player.getDistanceSq(sign.getPos()));
                if (dist > 12.0) continue;
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 4; i++) {
                    String line = cleanText(sign.signText[i].getUnformattedText());
                    if (!line.isEmpty()) sb.append("[").append(line).append("] ");
                }
                player.sendMessage(new TextComponentString(TextFormatting.GRAY + 
                    String.format("%.1fm: %s tai (%d, %d, %d)", dist, sb.toString(), sign.getPos().getX(), sign.getPos().getY(), sign.getPos().getZ())));
                count++;
            }
        }
        player.sendMessage(new TextComponentString(TextFormatting.YELLOW + "[AutoAim Debug] Tìm thấy " + count + " bảng."));
    }

    private static boolean isSyncingTarget = false;

    public static void setupTarget(String input, boolean verbose) {
        if (isSyncingTarget) return;
        try {
            isSyncingTarget = true;
            Minecraft mc = Minecraft.getMinecraft();
            if (input == null || input.trim().isEmpty()) {
                input = "gunpowder";
            }
            String clean = input.replaceAll("[<>\\[\\]]", "").trim();
            if (clean.isEmpty()) {
                clean = "gunpowder";
            }
            hasUserConfiguredTarget = true;
            configuredTarget = clean;
            resolveTarget(clean);

            if (targetItemId > 0) {
                try {
                    if (com.zorotool.macrorecorder.ConfigHelper.getFarmItemId() != targetItemId) {
                        com.zorotool.macrorecorder.ConfigHelper.setFarmItemId(targetItemId);
                    }
                } catch (Exception ignored) {}
            }

            isAimLockActive = true;
            outOfRangeTicks = 0;
            stuckTicks = 0;
            isWarmupActive = false;

            boolean found = findTargetSign(lastItemTarget, verbose);
            if (found) {
                if (mc.player != null) {
                    anchorPos = new Vec3d(mc.player.posX, mc.player.posY, mc.player.posZ);
                }
                if (mc.gameSettings != null) {
                    mc.gameSettings.pauseOnLostFocus = false;
                }
                if (mc.player != null) {
                    mc.player.setSprinting(false);
                    if (verbose) {
                        mc.player.sendMessage(new TextComponentString(TextFormatting.GREEN + 
                            "[AutoAim] ĐÃ BẬT GHIM BẢNG [Sell] " + targetDisplayName));
                        mc.player.sendMessage(new TextComponentString(TextFormatting.AQUA + 
                            "-> Camera ghim vào bảng mượt như người thật. Bạn thoải mái đè chuột phải để bán đồ!"));
                    }
                }
            } else {
                targetSignPos = null;
                if (mc.player != null && verbose) {
                    mc.player.sendMessage(new TextComponentString(TextFormatting.YELLOW + 
                        "[AutoAim] ĐÃ SETUP TRƯỚC: " + targetDisplayName));
                    mc.player.sendMessage(new TextComponentString(TextFormatting.AQUA + 
                        "-> Khi bấm F4 (#goto tới quầy), camera sẽ tự động chỉa và ghim đúng vào bảng " + targetDisplayName + "!"));
                }
            }
        } finally {
            isSyncingTarget = false;
        }
    }

    public static void startAimLock(String itemTarget) {
        setupTarget(itemTarget, false);
    }

    public static void toggleAimLock(String itemTarget) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;

        if (isAimLockActive) {
            stopAimLock(mc);
            mc.player.sendMessage(new TextComponentString(TextFormatting.RED + 
                "[AutoAim] ĐÃ TẮT GHIM CAMERA."));
        } else {
            startAimLock(itemTarget);
        }
    }

    public static void stopAimLock(Minecraft mc) {
        isAimLockActive = false;
        targetSignPos = null;
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

    /**
     * Cập nhật góc nhìn mô phỏng 100% chuyển động chuột người thật (Human-like Aiming):
     * - Khi Alt-Tab ra ngoài làm việc khác: camera vẫn bám bảng ngầm, TUYỆT ĐỐI KHÔNG can thiệp chuột Windows
     * - Khi đang trong game và người chơi rê chuột tay: nhường quyền 100% cho người chơi
     */
    private static void updateCameraSmooth(Minecraft mc, float partialTicks) {
        if (!isAimLockActive || targetSignPos == null || mc.player == null || mc.world == null) return;
        // Chỉ hoạt động khi người chơi ở khu vực Shop (Y xấp xỉ 151)
        if (Math.abs(mc.player.posY - 151.0) > 4.0) return;
        // Nếu đang mở menu màn hình (ESC, chat, hòm đồ) -> không can thiệp góc nhìn
        if (mc.currentScreen != null) return;

        // TUYỆT ĐỐI KHÔNG CHIẾM CHUỘT:
        // Khi game đang có Focus VÀ người chơi đang chủ động rê chuột tay, nhường quyền 100% cho người chơi
        if (mc.inGameHasFocus && mc.mouseHelper != null && (Math.abs(mc.mouseHelper.deltaX) > 1 || Math.abs(mc.mouseHelper.deltaY) > 1)) {
            return;
        }

        // Khi người chơi đã ngắm trúng bảng VÀ không bị ai xô đẩy:
        // TUYỆT ĐỐI KHÔNG can thiệp xoay hay rung lắc camera, để người chơi hoàn toàn tự do lia chuột!
        if (isAimingAtSign(mc) && !isAutoWalking) {
            return;
        }

        EntityPlayerSP player = mc.player;

        double eyeX = player.lastTickPosX + (player.posX - player.lastTickPosX) * (double) partialTicks;
        double eyeY = player.lastTickPosY + (player.posY - player.lastTickPosY) * (double) partialTicks + (double) player.getEyeHeight();
        double eyeZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * (double) partialTicks;

        IBlockState state = mc.world.getBlockState(targetSignPos);
        AxisAlignedBB bbox = state.getBoundingBox(mc.world, targetSignPos);
        Vec3d target = new Vec3d(
            targetSignPos.getX() + (bbox.minX + bbox.maxX) / 2.0,
            targetSignPos.getY() + 0.35 + yOffset,
            targetSignPos.getZ() + (bbox.minZ + bbox.maxZ) / 2.0
        );

        double dx = eyeX - target.x;
        double dy = eyeY - target.y;
        double dz = eyeZ - target.z;

        float targetYaw = (float) (MathHelper.atan2(dx, -dz) * (180.0 / Math.PI));
        double dist = Math.sqrt(dx * dx + dz * dz);
        float targetPitch = (float) (MathHelper.atan2(dy, dist) * (180.0 / Math.PI));

        targetYaw = MathHelper.wrapDegrees(targetYaw);

        float deltaYaw = MathHelper.wrapDegrees(targetYaw - player.rotationYaw);
        float deltaPitch = targetPitch - player.rotationPitch;

        boolean aimingAtSign = isAimingAtSign(mc);

        // Mô phỏng độ trễ và gia tốc chuột người thật (Human-like glide & ease-out):
        // 1. Tính bước nhảy góc theo độ nhạy chuột game (Mouse Sensitivity Quantization / GCD)
        float sens = (mc.gameSettings != null) ? mc.gameSettings.mouseSensitivity : 0.5F;
        float f = sens * 0.6F + 0.2F;
        float gcd = f * f * f * 8.0F * 0.15F;

        // 2. Tính gia tốc lướt chuột (glide into sign):
        float totalDiff = (float) Math.sqrt(deltaYaw * deltaYaw + deltaPitch * deltaPitch);
        float smoothFactor;
        if (aimingAtSign) {
            smoothFactor = 0.14F;
        } else {
            if (totalDiff > 60.0F) {
                smoothFactor = 0.18F;
            } else if (totalDiff > 25.0F) {
                smoothFactor = 0.26F;
            } else if (totalDiff > 8.0F) {
                smoothFactor = 0.32F;
            } else {
                smoothFactor = 0.22F; // giảm tốc êm khi sắp cập bến bảng (glide decel)
            }
        }

        float stepYaw = deltaYaw * smoothFactor;
        float stepPitch = deltaPitch * smoothFactor;

        // Giới hạn vận tốc xoay tối đa mỗi frame tránh snap giật góc
        float maxTurnRate = aimingAtSign ? 3.0F : 9.5F;
        stepYaw = MathHelper.clamp(stepYaw, -maxTurnRate, maxTurnRate);
        stepPitch = MathHelper.clamp(stepPitch, -maxTurnRate, maxTurnRate);

        // Làm tròn theo pixel chuột phần cứng để chuyển động 100% tự nhiên không bị giật
        if (gcd > 0.0001F) {
            stepYaw = Math.round(stepYaw / gcd) * gcd;
            stepPitch = Math.round(stepPitch / gcd) * gcd;
        }

        player.rotationYaw += stepYaw;
        player.rotationPitch += stepPitch;
    }

    public static boolean isAimingAtSign(Minecraft mc) {
        if (mc == null || mc.world == null || mc.objectMouseOver == null || targetSignPos == null) return false;
        if (mc.objectMouseOver.typeOfHit != RayTraceResult.Type.BLOCK) return false;
        BlockPos hitPos = mc.objectMouseOver.getBlockPos();
        if (!targetSignPos.equals(hitPos)) return false;

        TileEntity te = mc.world.getTileEntity(hitPos);
        if (te instanceof TileEntitySign) {
            TileEntitySign sign = (TileEntitySign) te;
            if (isBuySign(sign)) return false; // Tuyệt đối không coi bảng Buy là bảng Sell!
            if (!isMatchingSellSign(sign, lastItemTarget)) return false;
        }
        return true;
    }

    /**
     * Kiểm tra xem tâm ngắm hiện tại có đang chỉ vào bất kỳ bảng Mua (Buy sign) nào không,
     * hoặc bảng nằm ở dưới (Y < targetSignPos.getY()).
     * Dùng để ngắt chuột phải NGAY LẬP TỨC nếu bị đẩy lệch tâm xuống các bảng phía dưới.
     */
    public static boolean isLookingAtAnyBuySign(Minecraft mc) {
        if (mc == null || mc.world == null || mc.objectMouseOver == null) return false;
        if (mc.objectMouseOver.typeOfHit != RayTraceResult.Type.BLOCK) return false;
        BlockPos pos = mc.objectMouseOver.getBlockPos();
        TileEntity te = mc.world.getTileEntity(pos);
        if (te instanceof TileEntitySign) {
            TileEntitySign sign = (TileEntitySign) te;
            if (isBuySign(sign)) return true;
            if (targetSignPos != null && pos.getY() < targetSignPos.getY()) {
                return true;
            }
        }
        return false;
    }

    public static boolean isFacingTargetSign(Minecraft mc) {
        if (mc == null || mc.player == null || targetSignPos == null) return false;
        EntityPlayerSP player = mc.player;
        double distSq = player.getDistanceSq(targetSignPos);
        if (distSq > 25.0) return false; // trong cự ly 5 block

        double eyeX = player.posX;
        double eyeY = player.posY + player.getEyeHeight();
        double eyeZ = player.posZ;
        Vec3d target = new Vec3d(
            targetSignPos.getX() + 0.5,
            targetSignPos.getY() + 0.35 + yOffset,
            targetSignPos.getZ() + 0.5
        );
        double dx = eyeX - target.x;
        double dy = eyeY - target.y;
        double dz = eyeZ - target.z;
        float targetYaw = (float) (MathHelper.atan2(dx, -dz) * (180.0 / Math.PI));
        double dist = Math.sqrt(dx * dx + dz * dz);
        float targetPitch = (float) (MathHelper.atan2(dy, dist) * (180.0 / Math.PI));
        float deltaYaw = Math.abs(MathHelper.wrapDegrees(targetYaw - player.rotationYaw));
        float deltaPitch = Math.abs(targetPitch - player.rotationPitch);
        return deltaYaw <= 25.0F && deltaPitch <= 25.0F;
    }

    /**
     * Bán đồ tự động bằng tương tác gói tin nội bộ của game (Packet / ProcessRightClick)
     * TUYỆT ĐỐI KHÔNG SỬ DỤNG CHUỘT HỆ ĐIỀU HÀNH (OS MOUSE)
     */
    public static boolean clickTargetSign() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null || mc.playerController == null || targetSignPos == null) return false;

        // Cho phép click nếu tâm ngắm đang trúng bảng HOẶC góc mặt đang chỉa thẳng vào bảng (dù có người đứng che)
        if (!isAimingAtSign(mc) && !isFacingTargetSign(mc)) {
            return false;
        }

        EnumFacing facing = EnumFacing.UP;
        Vec3d hitVec = new Vec3d(targetSignPos.getX() + 0.5, targetSignPos.getY() + 0.5, targetSignPos.getZ() + 0.5);
        if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == RayTraceResult.Type.BLOCK && targetSignPos.equals(mc.objectMouseOver.getBlockPos())) {
            facing = mc.objectMouseOver.sideHit;
            hitVec = mc.objectMouseOver.hitVec;
        }

        mc.playerController.processRightClickBlock(mc.player, mc.world, targetSignPos, facing, hitVec, EnumHand.MAIN_HAND);

        // Vung tay visual trên màn hình (Client-side ONLY) - KHÔNG gửi CPacketAnimation lên server
        // để AntiCheat (GrimAC, Vulcan) không phát hiện BadPackets / AutoClicker
        if (mc.player != null) {
            if (!mc.player.isSwingInProgress || mc.player.swingProgressInt >= 3) {
                mc.player.isSwingInProgress = true;
                mc.player.swingProgressInt = -1;
                mc.player.swingingHand = EnumHand.MAIN_HAND;
            }
        }
        return true;
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getMinecraft();
        updateCameraSmooth(mc, event.renderTickTime);
    }

    /**
     * Kiểm tra xem có người chơi khác đang đứng chắn ngay phía trước mặt/hướng về quầy không
     * -> Nếu chỗ đó có người đứng rồi thì thôi, không đứng chỗ đó nữa (người thật không chen lấn)
     */
    private static boolean isAnotherPlayerBlocking(Minecraft mc, EntityPlayerSP player) {
        if (mc.world == null || player == null || targetSignPos == null) return false;
        double toSignX = (targetSignPos.getX() + 0.5) - player.posX;
        double toSignZ = (targetSignPos.getZ() + 0.5) - player.posZ;
        for (net.minecraft.entity.player.EntityPlayer other : mc.world.playerEntities) {
            if (other == null || other == player) continue;
            double d = player.getDistance(other);
            if (d < 1.3) {
                double dx = other.posX - player.posX;
                double dz = other.posZ - player.posZ;
                double dot = dx * toSignX + dz * toSignZ;
                if (dot > 0) {
                    return true; // Có người chơi đang đứng chắn phía trước
                }
            }
        }
        return false;
    }

    /**
     * Can thiệp trực tiếp vào Input movement của Minecraft để tự động di chuyển mượt mà
     * TUYỆT ĐỐI KHÔNG SPRINT -> KHÔNG TIÊU HAO ĐỘ ĐÓI (FEED)
     */
    @SubscribeEvent
    public void onInputUpdate(InputUpdateEvent event) {
        if (!isAimLockActive || targetSignPos == null) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.currentScreen != null) return; // Không can thiệp nếu đang mở GUI/Chat
        if (Math.abs(mc.player.posY - 151.0) > 4.0) return; // Chỉ can thiệp khi ở quầy Shop (Y xấp xỉ 151)

        if (isAutoWalking && !isWarmupActive && targetSignPos != null && mc.player != null) {
            // Nếu đã ngắm trúng hoặc đang chỉa mặt vào bảng bán: tuyệt đối đứng yên
            if (isAimingAtSign(mc) || isFacingTargetSign(mc)) {
                if (isAutoWalking) stopAutoWalk(mc);
                return;
            }

            EntityPlayerSP player = mc.player;
            double dx = player.posX - (targetSignPos.getX() + 0.5);
            double dz = player.posZ - (targetSignPos.getZ() + 0.5);
            double distXZ = Math.sqrt(dx * dx + dz * dz);

            if (distXZ > 2.2) {
                // Nếu bị đẩy lùi khỏi quầy: bước xích lại gần quầy
                double toSignX = (targetSignPos.getX() + 0.5) - player.posX;
                double toSignZ = (targetSignPos.getZ() + 0.5) - player.posZ;
                float moveAngle = (float) (MathHelper.atan2(toSignZ, toSignX) * (180.0D / Math.PI)) - 90.0F;
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
            }

            mc.player.setSprinting(false);
            if (mc.gameSettings != null) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
            }
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!isAimLockActive) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null || mc.gameSettings == null) return;
        if (mc.currentScreen != null) return; // Tạm dừng nếu đang mở chat/menu

        EntityPlayerSP player = mc.player;
        // Chỉ hoạt động khi người chơi ở quầy Shop (Y xấp xỉ 151)
        if (Math.abs(player.posY - 151.0) > 4.0) {
            if (isAutoWalking) stopAutoWalk(mc);
            return;
        }

        // Quét tự động chuyển bảng mỗi 5 tick (0.25s) khi người chơi bước sang bảng khác
        if (player.ticksExisted % 5 == 0) {
            scanAndSwitchTargetSign(mc);
        }

        if (targetSignPos == null) {
            if (isAutoWalking) stopAutoWalk(mc);
            if (player.ticksExisted % 5 == 0) {
                if (findTargetSign(getEffectiveTarget())) {
                    if (mc.player != null) {
                        anchorPos = new Vec3d(mc.player.posX, mc.player.posY, mc.player.posZ);
                        mc.player.sendMessage(new TextComponentString(TextFormatting.GREEN + 
                            "[AutoAim] Đã tới quầy! Tự động chỉa và ghim vào bảng: " + targetDisplayName));
                    }
                }
            }
            return;
        }

        // ĐIỀU KIỆN 100% NGƯỜI THẬT:
        // "miễn sao vẫn trong tầm ngắm thì ko di chuyển gì hết"
        // "nếu ra khỏi tầm nhắm bán ko lên tiền thì mới bắt đầu di chuyển"
        if (isAimingAtSign(mc) || isFacingTargetSign(mc)) {
            // ĐANG TRONG TẦM NGẮM BÁN ĐƯỢC TIỀN: ĐỨNG YÊN HOÀN TOÀN!
            if (isAutoWalking) {
                stopAutoWalk(mc);
            }
            outOfRangeTicks = 0;
            stuckTicks = 0;
        } else {
            // RA KHỎI TẦM NGẮM (bị đẩy ra quá xa hoặc bị người khác che chắn không bán được):
            if (autoReturnEnabled && !isWarmupActive) {
                outOfRangeTicks++;

                // Phản xạ tự nhiên: sau 8 tick (0.4s) không ngắm được bảng mới bắt đầu di chuyển tìm lại góc ngắm
                if (!isAutoWalking && outOfRangeTicks >= 8) {
                    isAutoWalking = true;
                    stuckTicks = 0;
                    lastCheckX = player.posX;
                    lastCheckZ = player.posZ;
                }

                if (isAutoWalking) {
                    player.setSprinting(false);

                    // Khi vừa vào lại tầm ngắm trúng bảng: DỪNG LẠI NGAY LẬP TỨC!
                    if (isAimingAtSign(mc)) {
                        stopAutoWalk(mc);
                        outOfRangeTicks = 0;
                        stuckTicks = 0;
                    } else if (player.collidedHorizontally) {
                        double moved = Math.sqrt(Math.pow(player.posX - lastCheckX, 2) + Math.pow(player.posZ - lastCheckZ, 2));
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

    @SubscribeEvent
    public void onChatReceived(net.minecraftforge.client.event.ClientChatReceivedEvent event) {
        if (event.getMessage() == null) return;
        String text = cleanText(event.getMessage().getUnformattedText()).toLowerCase();
        if (text.contains("dịch chuyển sẽ diễn ra") || text.contains("đừng chạy nhảy")) {
            isWarmupActive = true;
            stopAutoWalk(Minecraft.getMinecraft());
        } else if (text.contains("yêu cầu dịch chuyển đã bị hủy") || text.contains("đã dịch chuyển") || text.contains("mở kho")) {
            isWarmupActive = false;
        }
    }
}
