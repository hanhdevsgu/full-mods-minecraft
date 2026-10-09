package com.minclient.module.impl;

import com.minclient.module.Module;
import com.minclient.setting.BooleanSetting;

public class NoPushModule extends Module {
    private final BooleanSetting entities = new BooleanSetting("Entities", true);
    private final BooleanSetting blocks = new BooleanSetting("Blocks", true);
    private final BooleanSetting fluids = new BooleanSetting("Fluids", true);

    public NoPushModule() {
        super("NoPush", "Chống bị xô đẩy bởi thực thể, dòng nước và khối kẹt", Category.PLAYER, true);
        addSetting(entities);
        addSetting(blocks);
        addSetting(fluids);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        if (entities.getValue()) {
            mc.player.entityCollisionReduction = 1.0F;
        } else {
            mc.player.entityCollisionReduction = 0.0F;
        }
    }

    public boolean isNoPushEntities() {
        return isEnabled() && entities.getValue();
    }

    public boolean isNoPushBlocks() {
        return isEnabled() && blocks.getValue();
    }

    public boolean isNoPushFluids() {
        return isEnabled() && fluids.getValue();
    }
}
