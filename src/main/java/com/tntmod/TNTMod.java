package com.tntmod;

import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = TNTMod.MODID, name = TNTMod.NAME, version = TNTMod.VERSION, clientSideOnly = true)
public class TNTMod {
    public static final String MODID = "tntmod";
    public static final String NAME = "TNT Auto Logistics";
    public static final String VERSION = "1.0";

    @Mod.Instance
    public static TNTMod instance;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ModConfig.init();
        BlockStateManager.init();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // Register Keybindings
        KeyHandler.register();

        // Register Event Handlers
        MinecraftForge.EVENT_BUS.register(new KeyHandler());
        MinecraftForge.EVENT_BUS.register(new InventoryHandler());
        MinecraftForge.EVENT_BUS.register(new ServerSyncHandler());

        // Register Client Command /t
        ClientCommandHandler.instance.registerCommand(new CommandTNT());

        // Start background Team Sync Client
        TeamSyncClient.start();

        System.out.println("[TNTMod] TNT Auto-Logistics Mod 1.12.2 initialized successfully!");
    }
}
