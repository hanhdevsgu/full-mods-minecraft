package com.quickcraft;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Mo ruong -> shift-click (quick_move) dung loai + dung ty le nguyen lieu cua cong thuc ra item `itemId`,
 * lap day cho trong cua tui, roi dong GUI. Vi du TNT (5 thuoc sung + 4 cat): 36 o trong ->
 * 20 o thuoc sung + 16 o cat. Con du 1-2 o thi lay tiep nguyen lieu dang thieu nhat; lan sau tinh lai
 * dua tren do dang co trong tui nen tu bu cho can bang.
 */
public class ChestTaker {
    /** So o trong de lai khong lap day (0 = lap day het). */
    public static int reserveSlots = 0;

    private static class Need {
        final ItemStack proto;
        int perCraft = 1;
        int have = 0;      // dang co trong tui
        int space = 0;     // cho con trong cac stack do dang dang do trong tui
        int taken = 0;     // se lay them (that)
        int simSpace = 0;  // mo phong ke hoach
        int simTaken = 0;
        final List<int[]> stacks = new ArrayList<int[]>(); // {slotNumber, count} trong ruong
        Need(ItemStack proto) { this.proto = proto; }
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

        // chon cong thuc dau tien co du loai nguyen lieu (trong tui hoac ruong)
        List<Need> needs = null;
        for (IRecipe r : ForgeRegistries.RECIPES.getValuesCollection()) {
            ItemStack out = r.getRecipeOutput();
            if (out.isEmpty() || out.getItem() != target.item) continue;
            if (target.meta >= 0 && out.getMetadata() != target.meta) continue;
            needs = buildNeeds(r, mine, chest);
            if (needs != null) break;
        }
        if (needs == null) {
            ClientHandler.setStatus("§eRuong khong co nguyen lieu de craft " + name);
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
        // luon them 1 stack cho loai dang "thap" nhat so voi ty le cong thuc -> vd 36 o = 20 thuoc sung + 16 cat.
        List<Need> order = new ArrayList<Need>();
        int simE = e[0];
        for (Need n : needs) { n.simSpace = n.space; n.simTaken = 0; }
        int guard = 0;
        while (guard++ < 1000) {
            Need best = null;
            double bestVal = Double.MAX_VALUE;
            for (Need n : needs) {
                if (n.simSpace <= 0 && simE <= 0) continue;
                double v = (double) (n.have + n.simTaken) / n.perCraft;
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

        // BUOC 2: ruong nay co gi thuoc ke hoach thi shift-click lay (cac loai ruong khac se lay o lan mo sau,
        // vd ruong A chi co thuoc sung -> lay 20 st; ruong B chi co cat -> lay 16 st).
        List<Integer> clicks = new ArrayList<Integer>();
        for (Need n : order) {
            if (n.stacks.isEmpty()) continue;
            int[] entry = n.stacks.remove(0);
            clicks.add(entry[0]);
            n.taken += entry[1];
        }

        if (clicks.isEmpty()) {
            // khong lay duoc gi: ruong khong co nguyen lieu can, hoac tui da day -> dong GUI ngay
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

        StringBuilder sb = new StringBuilder("§aDa lay cho " + name + ": ");
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

        if (ClientHandler.autoCloseChest) p.closeScreen(); // dong GUI ngay, goi sau cac click nen server xu ly dung thu tu
        return true;
    }

    /** Nhom nguyen lieu cong thuc theo loai item cu the; null neu co loai khong tim thay o tui/ruong. */
    private static List<Need> buildNeeds(IRecipe r, List<Slot> mine, List<Slot> chest) {
        Ingredient[] grid = ClientCrafter.layoutFor(r, 3);
        if (grid == null) return null;
        List<Need> needs = new ArrayList<Need>();
        for (Ingredient ing : grid) {
            if (ing == null) continue;
            ItemStack found = find(ing, mine);
            if (found == null) found = find(ing, chest);
            if (found == null) {
                // chua co o tui/ruong nay (nam o ruong khac) -> dung loai dau tien cong thuc chap nhan
                ItemStack[] ms = ing.getMatchingStacks();
                if (ms.length == 0) return null;
                found = ms[0].copy();
                if (found.getMetadata() == 32767) found = new ItemStack(found.getItem(), 1, 0);
            }
            Need n = null;
            for (Need x : needs) {
                if (same(x.proto, found)) { n = x; break; }
            }
            if (n == null) { n = new Need(found.copy()); needs.add(n); }
            else n.perCraft++;
        }
        return needs.isEmpty() ? null : needs;
    }

    private static ItemStack find(Ingredient ing, List<Slot> slots) {
        for (Slot s : slots) {
            ItemStack st = s.getStack();
            if (!st.isEmpty() && ing.apply(st)) return st;
        }
        return null;
    }

    private static boolean same(ItemStack a, ItemStack b) {
        return ItemStack.areItemsEqual(a, b) && ItemStack.areItemStackTagsEqual(a, b);
    }
}
