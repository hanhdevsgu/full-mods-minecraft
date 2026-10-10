package com.quickcraft;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemShears;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Mo ruong -> shift-click (quick_move) dung loai + dung ty le nguyen lieu cua cong thuc ra item `itemId`,
 * lap day cho trong cua tui, roi dong GUI. Vi du TNT (5 thuoc sung + 4 cat): 36 o trong ->
 * 20 o thuoc sung + 16 o cat.
 * Doi voi cong thuc da tang (vi du: Sach can Giay + Da bo, Giay lam tu Mia):
 * Tu dong phan giai ra Mia + Da bo theo ty le 3 Mia : 1 Da bo de tui do luon can bang tuyet doi!
 * Khoi vang: Hat vang -> Phoi vang -> Khoi vang: lay day hat vang vao tui de craft.
 */
public class ChestTaker {
    /** So o trong de lai khong lap day (0 = lap day het). */
    public static int reserveSlots = 0;

    private static class Need {
        final ItemStack proto;
        double perCraftDouble = 1.0;
        int perCraft = 1;
        int have = 0;      // dang co trong tui
        int space = 0;     // cho con trong cac stack do dang dang do trong tui
        int taken = 0;     // se lay them (that)
        int simSpace = 0;  // mo phong ke hoach
        int simTaken = 0;
        final List<int[]> stacks = new ArrayList<int[]>(); // {slotNumber, count} trong ruong
        Need(ItemStack proto) { this.proto = proto; }
    }

    private static class RawReq {
        final ItemStack proto;
        final double count; // so luong can cho 1 lan craft thanh pham
        RawReq(ItemStack proto, double count) {
            this.proto = proto;
            this.count = count;
        }
    }

    /** Chi tu dong cho ruong binh thuong (khong dung menu shop/plugin dat ten rieng). */
    public static boolean isPlainChest(Container c) {
        if (c instanceof ContainerChest) {
            try {
                String name = ((ContainerChest) c).getLowerChestInventory().getDisplayName().getUnformattedText();
                return name.equals(I18n.format("container.chest"))
                        || name.equals(I18n.format("container.chestDouble"))
                        || name.equals(I18n.format("container.enderchest"));
            } catch (Throwable t) {
                return false;
            }
        }
        // shulker box vanilla (ContainerShulkerBox) -> luon la container local
        return c.getClass().getSimpleName().contains("ContainerShulkerBox");
    }

    /** Phan ruong da co du lieu chua (it nhat 1 o co do). */
    public static boolean hasChestData(EntityPlayerSP p, Container c) {
        for (Slot s : c.inventorySlots) {
            if (s.inventory != p.inventory && s.getHasStack()) return true;
        }
        return false;
    }

    /** @return true neu da lay it nhat 1 stack (va da dong GUI). */
    public static boolean run(Minecraft mc, Container c) {
        if (!ClientHandler.enabled) return false;
        EntityPlayerSP p = mc.player;
        ItemResolver.Target target = ItemResolver.parse(ClientHandler.itemId);
        if (target == null) {
            ClientHandler.setStatus("§cID item khong hop le: " + ClientHandler.itemId);
            return false;
        }
        String name = target.toStack().getDisplayName();

        List<Slot> chest = new ArrayList<Slot>();
        List<Slot> mine = new ArrayList<Slot>();
        for (Slot s : c.inventorySlots) {
            if (s.inventory != p.inventory) chest.add(s);
            else if (s.getSlotIndex() < 36) mine.add(s);
        }

        // Kiem tra neu trong tui dang co san thanh pham (vi du sach, khoi vang...)
        // Nguoi choi co the dang mo ruong de CAT THANH PHAM (Store) -> Khong tu dong dong GUI ruong!
        int targetInMine = 0;
        for (Slot s : mine) {
            ItemStack st = s.getStack();
            if (!st.isEmpty() && st.getItem() == target.item && (target.meta < 0 || st.getMetadata() == target.meta)) {
                targetInMine += st.getCount();
            }
        }
        if (targetInMine > 0) {
            ClientHandler.setStatus("§eBấm 'Store' để cất " + targetInMine + "x " + name + " vào rương");
            return true;
        }

        // Chon cong thuc dau tien co the phan giai nguyen lieu (trong tui hoac ruong)
        List<Need> needs = null;
        for (IRecipe r : ForgeRegistries.RECIPES.getValuesCollection()) {
            ItemStack out = r.getRecipeOutput();
            if (out.isEmpty() || out.getItem() != target.item) continue;
            if (target.meta >= 0 && out.getMetadata() != target.meta) continue;
            needs = buildNeeds(r, mine, chest);
            if (needs != null) break;
        }
        if (needs == null) {
            ClientHandler.setStatus("§eRương không có nguyên liệu để craft " + name);
            return false;
        }

        // thong ke tui + ruong
        int empty = 0;
        for (Slot s : mine) if (!s.getHasStack()) empty++;
        int[] e = {Math.max(0, empty - reserveSlots)};
        for (Need n : needs) {
            for (Slot s : mine) {
                ItemStack st = s.getStack();
                if (st.isEmpty() || !same(st, n.proto)) continue;
                n.have += st.getCount();
                if (st.getCount() < st.getMaxStackSize()) n.space += st.getMaxStackSize() - st.getCount();
            }
            for (Slot s : chest) {
                ItemStack st = s.getStack();
                if (!st.isEmpty() && same(st, n.proto)) n.stacks.add(new int[]{s.slotNumber, st.getCount()});
            }
            Collections.sort(n.stacks, new Comparator<int[]>() {
                public int compare(int[] a, int[] b) { return b[1] - a[1]; }
            });
        }

        // BUOC 1: ke hoach ly tuong cho TOAN BO cho trong cua tui (gia su ruong nao cung du do):
        // luon them 1 stack cho loai dang "thap" nhat so voi ty le cong thuc -> vd 36 o = 27 mia + 9 da bo.
        List<Need> order = new ArrayList<Need>();
        int simE = e[0];
        for (Need n : needs) { n.simSpace = n.space; n.simTaken = 0; }
        int guard = 0;
        while (guard++ < 1000) {
            Need best = null;
            double bestVal = Double.MAX_VALUE;
            for (Need n : needs) {
                if (n.simSpace <= 0 && simE <= 0) continue;
                double v = (double) (n.have + n.simTaken) / Math.max(1, n.perCraft);
                if (v < bestVal) { bestVal = v; best = n; }
            }
            if (best == null) break;
            int max = Math.max(1, best.proto.getMaxStackSize());
            int m = Math.min(max, best.simSpace + simE * max);
            if (m <= 0) break;
            int fromPartial = Math.min(m, best.simSpace);
            int rest = m - fromPartial;
            int newSlots = (rest + max - 1) / max;
            simE -= newSlots;
            best.simSpace = best.simSpace - fromPartial + (newSlots * max - rest);
            best.simTaken += m;
            order.add(best);
        }

        // BUOC 2: ruong nay co gi thuoc ke hoach thi shift-click lay
        List<Integer> clicks = new ArrayList<Integer>();
        for (Need n : order) {
            if (n.stacks.isEmpty()) continue;
            int[] entry = n.stacks.remove(0);
            clicks.add(entry[0]);
            n.taken += entry[1];
        }

        if (clicks.isEmpty()) {
            boolean chestHasIt = false;
            for (Need n : needs) if (!n.stacks.isEmpty()) chestHasIt = true;
            QuickCraftMod.logger.info("[QuickCraft] {} cho {} -> dong GUI",
                    chestHasIt ? "tui het cho" : "ruong khong co nguyen lieu", name);
            if (ClientHandler.autoCloseChest) p.closeScreen();
            return true;
        }

        for (int slot : clicks) {
            mc.playerController.windowClick(c.windowId, slot, 0, ClickType.QUICK_MOVE, p);
        }

        StringBuilder sb = new StringBuilder("§aĐã lấy cho " + name + ": ");
        boolean first = true;
        for (Need n : needs) {
            if (n.taken <= 0) continue;
            if (!first) sb.append(", ");
            first = false;
            sb.append(n.taken).append(" x ").append(n.proto.getDisplayName());
        }
        String msg = sb.toString();
        ClientHandler.setStatus(msg);
        QuickCraftMod.logger.info("[QuickCraft] lay tu ruong: {} ({} click, con {} o trong)", msg, clicks.size(), e[0]);
        try {
            mc.ingameGUI.setOverlayMessage(msg, false);
        } catch (Throwable ignored) {
        }

        if (ClientHandler.autoCloseChest) p.closeScreen();
        return true;
    }

    /**
     * Day toan bo thanh pham / item tu tui do vao ruong.
     * Uu tien thanh pham dang craft (target item), hoac tat ca vat pham khong phai vu khi/dung cu.
     */
    public static void storeItems(Minecraft mc) {
        if (mc.player == null || mc.playerController == null) return;
        Container c = mc.player.openContainer;
        if (c == null) return;

        List<Slot> chest = new ArrayList<Slot>();
        List<Slot> mine = new ArrayList<Slot>();
        for (Slot s : c.inventorySlots) {
            if (s.inventory != mc.player.inventory) chest.add(s);
            else if (s.getSlotIndex() < 36) mine.add(s);
        }
        if (chest.isEmpty() || mine.isEmpty()) return;

        ItemResolver.Target target = ItemResolver.parse(ClientHandler.itemId);
        List<Slot> toStore = new ArrayList<Slot>();

        // 1. Uu tien tim toan bo stack thanh pham muc tieu (sach, khoi vang...)
        if (target != null) {
            for (Slot s : mine) {
                ItemStack st = s.getStack();
                if (!st.isEmpty() && st.getItem() == target.item && (target.meta < 0 || st.getMetadata() == target.meta)) {
                    toStore.add(s);
                }
            }
        }

        // 2. Neu khong co thanh pham muc tieu, cat cac stack khong phai cong cu/giap
        if (toStore.isEmpty()) {
            boolean shiftHeld = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
            for (Slot s : mine) {
                ItemStack st = s.getStack();
                if (st.isEmpty()) continue;
                if (!shiftHeld && isValuableOrTool(st)) continue;

                // Chi cat neu ruong da chua vat pham nay hoac giu Shift
                boolean chestHas = false;
                for (Slot cs : chest) {
                    if (cs.getHasStack() && same(cs.getStack(), st)) {
                        chestHas = true;
                        break;
                    }
                }
                if (chestHas || shiftHeld) {
                    toStore.add(s);
                }
            }
        }

        if (toStore.isEmpty()) {
            ClientHandler.setStatus("§eKhông có item phù hợp để cất vào rương.");
            return;
        }

        int movedStacks = 0;
        int movedCount = 0;
        for (Slot s : toStore) {
            if (!s.getHasStack() || s.getStack().isEmpty()) continue;
            int countBefore = s.getStack().getCount();
            mc.playerController.windowClick(c.windowId, s.slotNumber, 0, ClickType.QUICK_MOVE, mc.player);
            movedStacks++;
            movedCount += countBefore;
        }

        String msg = "§aĐã cất " + movedCount + " item (" + movedStacks + " ô) vào rương!";
        ClientHandler.setStatus(msg);
        try {
            mc.ingameGUI.setOverlayMessage(msg, false);
        } catch (Throwable ignored) {}

        if (ClientHandler.autoCloseChest) {
            mc.player.closeScreen();
        }
    }

    public static boolean isValuableOrTool(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item instanceof ItemTool
                || item instanceof ItemSword
                || item instanceof ItemBow
                || item instanceof ItemArmor
                || item instanceof ItemShears
                || stack.isItemStackDamageable();
    }

    /**
     * Nhom nguyen lieu cong thuc theo loai item cu the;
     * Ho tro de quy phan giai nguyen lieu trung gian thanh nguyen lieu tho co trong ruong
     * (vd: Giay -> Mia ty le 3:3 = 1:1, Phoi vang -> Hat vang ty le 1:9).
     */
    private static List<Need> buildNeeds(IRecipe r, List<Slot> mine, List<Slot> chest) {
        Ingredient[] grid = ClientCrafter.layoutFor(r, 3);
        if (grid == null) return null;

        List<RawReq> allRaw = new ArrayList<RawReq>();
        for (Ingredient ing : grid) {
            if (ing == null) continue;
            List<RawReq> resolved = resolveIngredientToRaw(ing, 1.0, mine, chest, 0);
            if (resolved == null || resolved.isEmpty()) return null;
            allRaw.addAll(resolved);
        }
        if (allRaw.isEmpty()) return null;

        // Gom nhom theo loai vat pham
        List<Need> needs = new ArrayList<Need>();
        for (RawReq raw : allRaw) {
            Need matched = null;
            for (Need x : needs) {
                if (same(x.proto, raw.proto)) {
                    matched = x;
                    break;
                }
            }
            if (matched == null) {
                Need n = new Need(raw.proto.copy());
                n.perCraftDouble = raw.count;
                needs.add(n);
            } else {
                matched.perCraftDouble += raw.count;
            }
        }

        for (Need n : needs) {
            n.perCraft = Math.max(1, (int) Math.round(n.perCraftDouble));
        }

        // Kiem tra it nhat 1 loai co trong ruong hoac tui
        boolean anyFound = false;
        for (Need n : needs) {
            for (Slot s : mine) {
                if (s.getHasStack() && same(s.getStack(), n.proto)) { anyFound = true; break; }
            }
            if (anyFound) break;
            for (Slot s : chest) {
                if (s.getHasStack() && same(s.getStack(), n.proto)) { anyFound = true; break; }
            }
            if (anyFound) break;
        }
        if (!anyFound) return null;

        return needs;
    }

    /**
     * Phan giai de quy 1 Ingredient thanh danh sach cac nguyen lieu tho co trong ruong/tui
     * kem he so so luong can thiet cho moi don vi thanh pham.
     */
    private static List<RawReq> resolveIngredientToRaw(Ingredient ing, double multiplier, List<Slot> mine, List<Slot> chest, int depth) {
        if (ing == null) return Collections.emptyList();

        // 1. Kiem tra co truc tiep trong tui hoac ruong khong
        ItemStack direct = find(ing, mine);
        if (direct == null) direct = find(ing, chest);
        if (direct != null) {
            return Collections.singletonList(new RawReq(direct.copy(), multiplier));
        }

        // 2. Neu chua co truc tiep, tim xem co cong thuc nao che tao ra vat pham nay tu do trong ruong khong
        if (depth < 4) {
            for (IRecipe subR : ForgeRegistries.RECIPES.getValuesCollection()) {
                ItemStack subOut = subR.getRecipeOutput();
                if (subOut.isEmpty() || !ing.apply(subOut)) continue;
                Ingredient[] subGrid = ClientCrafter.layoutFor(subR, 3);
                if (subGrid == null) continue;
                int outCount = Math.max(1, subOut.getCount());

                boolean valid = true;
                List<RawReq> subRaw = new ArrayList<RawReq>();
                for (Ingredient si : subGrid) {
                    if (si == null) continue;
                    List<RawReq> r = resolveIngredientToRaw(si, multiplier / (double) outCount, mine, chest, depth + 1);
                    if (r == null || r.isEmpty()) {
                        valid = false;
                        break;
                    }
                    subRaw.addAll(r);
                }
                if (valid && !subRaw.isEmpty()) {
                    return subRaw;
                }
            }
        }

        // 3. Fallback: lay stack mau mac dinh
        ItemStack[] ms = ing.getMatchingStacks();
        if (ms != null && ms.length > 0) {
            ItemStack fallback = ms[0].copy();
            if (fallback.getMetadata() == 32767) fallback = new ItemStack(fallback.getItem(), 1, 0);
            return Collections.singletonList(new RawReq(fallback, multiplier));
        }
        return null;
    }

    private static ItemStack find(Ingredient ing, List<Slot> slots) {
        for (Slot s : slots) {
            ItemStack st = s.getStack();
            if (!st.isEmpty() && ing.apply(st)) return st;
        }
        return null;
    }

    public static boolean same(ItemStack a, ItemStack b) {
        return ItemStack.areItemsEqual(a, b) && ItemStack.areItemStackTagsEqual(a, b);
    }
}
