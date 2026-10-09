package com.minclient.module;

import com.minclient.module.impl.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ModuleManager {
    private final List<Module> modules = new ArrayList<>();
    private final NoPushModule noPushModule;
    private final FastInteractModule fastInteractModule;
    private final LightModule lightModule;
    private final PathRenderModule pathRenderModule;
    private final ChatSuffixModule chatSuffixModule;
    private final VelocityModule velocityModule;
    private final CriticalsModule criticalsModule;
    private final EspModule espModule;
    private final TracersModule tracersModule;
    private final StorageEspModule storageEspModule;

    public ModuleManager() {
        // Combat
        modules.add(new KillAuraModule());
        modules.add(new AutoClickerModule());
        this.velocityModule = new VelocityModule();
        modules.add(this.velocityModule);
        this.criticalsModule = new CriticalsModule();
        modules.add(this.criticalsModule);

        // Movement
        modules.add(new SprintModule());
        modules.add(new StepModule());
        modules.add(new FlightModule());
        modules.add(new SafeWalkModule());
        modules.add(new AutoWalkModule());
        modules.add(new JesusModule());
        modules.add(new NoSlowModule());

        // Render
        this.lightModule = new LightModule();
        modules.add(this.lightModule);
        this.pathRenderModule = new PathRenderModule();
        modules.add(this.pathRenderModule);
        this.espModule = new EspModule();
        modules.add(this.espModule);
        this.tracersModule = new TracersModule();
        modules.add(this.tracersModule);
        this.storageEspModule = new StorageEspModule();
        modules.add(this.storageEspModule);

        // Player
        this.noPushModule = new NoPushModule();
        modules.add(this.noPushModule);
        this.fastInteractModule = new FastInteractModule();
        modules.add(this.fastInteractModule);
        modules.add(new NoFallModule());
        modules.add(new AutoEatModule());

        // World
        modules.add(new TimerModule());
        modules.add(new FastBreakModule());

        // Misc
        modules.add(new BaritoneModule());
        this.chatSuffixModule = new ChatSuffixModule();
        modules.add(this.chatSuffixModule);
    }

    public List<Module> getModules() {
        return Collections.unmodifiableList(modules);
    }

    public List<Module> getModulesByCategory(Module.Category category) {
        List<Module> list = new ArrayList<>();
        for (Module m : modules) {
            if (m.getCategory() == category) {
                list.add(m);
            }
        }
        return list;
    }

    public Module getModule(String name) {
        for (Module m : modules) {
            if (m.getName().equalsIgnoreCase(name)) {
                return m;
            }
        }
        return null;
    }

    public NoPushModule getNoPushModule() {
        return noPushModule;
    }

    public FastInteractModule getFastInteractModule() {
        return fastInteractModule;
    }

    public LightModule getLightModule() {
        return lightModule;
    }

    public PathRenderModule getPathRenderModule() {
        return pathRenderModule;
    }

    public ChatSuffixModule getChatSuffixModule() {
        return chatSuffixModule;
    }

    public VelocityModule getVelocityModule() {
        return velocityModule;
    }

    public CriticalsModule getCriticalsModule() {
        return criticalsModule;
    }

    public EspModule getEspModule() {
        return espModule;
    }

    public TracersModule getTracersModule() {
        return tracersModule;
    }

    public StorageEspModule getStorageEspModule() {
        return storageEspModule;
    }

    public void onTick() {
        for (Module m : modules) {
            if (m.isEnabled()) {
                m.onTick();
            }
        }
    }
}
