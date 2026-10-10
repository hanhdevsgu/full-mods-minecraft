package com.minclient.module;

import com.minclient.module.impl.AimbotModule;
import com.minclient.module.impl.FastInteractModule;
import com.minclient.module.impl.LightModule;
import com.minclient.module.impl.NoPushModule;
import com.minclient.module.impl.PathRenderModule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ModuleManager {
    private final List<Module> modules = new ArrayList<>();
    private final NoPushModule noPushModule;
    private final FastInteractModule fastInteractModule;
    private final LightModule lightModule;
    private final PathRenderModule pathRenderModule;
    private final AimbotModule aimbotModule;

    public ModuleManager() {
        this.noPushModule = new NoPushModule();
        this.fastInteractModule = new FastInteractModule();
        this.lightModule = new LightModule();
        this.pathRenderModule = new PathRenderModule();
        this.aimbotModule = new AimbotModule();

        modules.add(this.noPushModule);
        modules.add(this.fastInteractModule);
        modules.add(this.lightModule);
        modules.add(this.aimbotModule);
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

    public AimbotModule getAimbotModule() {
        return aimbotModule;
    }

    public void onTick() {
        for (Module m : modules) {
            if (m.isEnabled()) {
                m.onTick();
            }
        }
    }
}
