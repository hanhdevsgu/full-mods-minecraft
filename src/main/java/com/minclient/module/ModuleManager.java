package com.minclient.module;

import com.minclient.module.impl.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ModuleManager {
    private final List<Module> modules = new ArrayList<>();
    private final NoPushModule noPushModule;
    private final FastInteractModule fastInteractModule;
    private final PathRenderModule pathRenderModule;
    private final LightModule lightModule;
    private final BaritoneModule baritoneModule;

    public ModuleManager() {
        this.noPushModule = new NoPushModule();
        this.fastInteractModule = new FastInteractModule();
        this.pathRenderModule = new PathRenderModule();
        this.lightModule = new LightModule();
        this.baritoneModule = new BaritoneModule();

        modules.add(this.noPushModule);
        modules.add(this.fastInteractModule);
        modules.add(this.pathRenderModule);
        modules.add(this.lightModule);
        modules.add(this.baritoneModule);
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

    public PathRenderModule getPathRenderModule() {
        return pathRenderModule;
    }

    public LightModule getLightModule() {
        return lightModule;
    }

    public BaritoneModule getBaritoneModule() {
        return baritoneModule;
    }

    public void onTick() {
        for (Module m : modules) {
            if (m.isEnabled()) {
                m.onTick();
            }
        }
    }
}
