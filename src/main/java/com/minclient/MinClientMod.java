package com.minclient;

import com.minclient.command.CommandManager;
import com.minclient.event.ClientEventHandler;
import com.minclient.module.ModuleManager;
import com.minclient.pathfinding.MotorController;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = MinClientMod.MODID, name = MinClientMod.NAME, version = MinClientMod.VERSION, clientSideOnly = true)
public class MinClientMod {
    public static final String MODID = "minclient";
    public static final String NAME = "MinClient";
    public static final String VERSION = "1.0.0";

    public static final Logger LOGGER = LogManager.getLogger(NAME);

    @Mod.Instance
    public static MinClientMod INSTANCE;

    public static MinClientMod getInstance() {
        return INSTANCE;
    }

    private ModuleManager moduleManager;
    private CommandManager commandManager;
    private MotorController motorController;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        INSTANCE = this;
        LOGGER.info("[MinClient] Đang khởi tạo Minimal Client (NoPush, FastInteract, .look, Baritone A*)...");
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        this.motorController = new MotorController();
        this.moduleManager = new ModuleManager();
        this.commandManager = new CommandManager(this.moduleManager, this.motorController);

        ClientEventHandler eventHandler = new ClientEventHandler(this.moduleManager, this.commandManager, this.motorController);
        MinecraftForge.EVENT_BUS.register(eventHandler);

        LOGGER.info("[MinClient] Khởi tạo thành công! Gõ .help trong chat để xem danh sách lệnh.");
    }

    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    public CommandManager getCommandManager() {
        return commandManager;
    }

    public MotorController getMotorController() {
        return motorController;
    }
}
