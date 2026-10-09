package com.quickcraft;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod(modid = QuickCraftMod.MODID, name = QuickCraftMod.NAME, version = QuickCraftMod.VERSION,
     clientSideOnly = true, acceptableRemoteVersions = "*")
public class QuickCraftMod {
    public static final String MODID = "quickcraft";
    public static final String NAME = "Quick Craft";
    public static final String VERSION = "1.0";

    public static java.io.File configFile;
    public static org.apache.logging.log4j.Logger logger;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        configFile = event.getSuggestedConfigurationFile();
        logger = event.getModLog();
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        if (event.getSide() == Side.CLIENT) {
            ClientHandler.init();
        }
    }
}
