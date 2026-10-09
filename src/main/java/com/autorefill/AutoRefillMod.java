package com.autorefill;

import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

@Mod(
    modid = AutoRefillMod.MODID,
    name = AutoRefillMod.NAME,
    version = AutoRefillMod.VERSION,
    clientSideOnly = true,
    acceptedMinecraftVersions = "[1.12,1.12.2]"
)
public class AutoRefillMod {
    public static final String MODID = "autorefill";
    public static final String NAME = "Auto Hotbar Refill";
    public static final String VERSION = "1.0.0";

    public static Logger logger;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        ConfigManager.init(event.getSuggestedConfigurationFile());
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        // Dang ky phim tat
        KeyInputHandler.register();

        // Dang ky event handlers
        MinecraftForge.EVENT_BUS.register(new RefillHandler());
        MinecraftForge.EVENT_BUS.register(new ChatHandler());
        MinecraftForge.EVENT_BUS.register(new KeyInputHandler());

        // Dang ky client command /refill, /rf
        ClientCommandHandler.instance.registerCommand(new CommandRefill());

        logger.info("[AutoRefill] Auto Hotbar Refill mod da khoi tao thanh cong!");
    }
}
