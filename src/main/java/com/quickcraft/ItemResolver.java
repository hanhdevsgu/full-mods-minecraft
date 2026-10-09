package com.quickcraft;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import java.util.Locale;

/**
 * Doc chuoi nguoi dung nhap:
 *   "42"                    -> item/block id 42 (iron block)
 *   "35:14"                 -> id 35, meta 14 (len do)
 *   "minecraft:iron_block"  -> ten registry
 *   "iron_block"            -> tu them minecraft:
 *   "minecraft:wool:14"     -> ten + meta
 */
public class ItemResolver {
    public static class Target {
        public final Item item;
        public final int meta; // -1 = bat ky meta

        Target(Item item, int meta) {
            this.item = item;
            this.meta = meta;
        }

        public ItemStack toStack() {
            return new ItemStack(item, 1, meta < 0 ? 0 : meta);
        }
    }

    public static Target parse(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return null;

        int meta = -1;
        int idx = s.lastIndexOf(':');
        if (idx > 0) {
            String tail = s.substring(idx + 1);
            if (isInt(tail)) {
                meta = Integer.parseInt(tail);
                s = s.substring(0, idx);
            }
        }

        Item item;
        if (isInt(s)) {
            item = Item.getItemById(Integer.parseInt(s));
        } else {
            if (!s.contains(":")) s = "minecraft:" + s;
            item = Item.REGISTRY.getObject(new ResourceLocation(s));
        }
        if (item == null || item == Items.AIR) return null;
        return new Target(item, meta);
    }

    private static boolean isInt(String s) {
        if (s.isEmpty() || s.length() > 9) return false;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
    }
}
