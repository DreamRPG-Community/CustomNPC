package cn.mythicland.customnpc;

import cn.mythicland.lib.bootstrap.LibPluginLifecycle;
import cn.mythicland.lib.bootstrap.annotation.LifecycleComponent;

import java.util.Objects;

/**
 * Owns CustomNPC startup, reload, and shutdown ordering.
 */
@LifecycleComponent
public final class CustomNPCLifecycle implements LibPluginLifecycle {

    private final CustomNPCService service;

    public CustomNPCLifecycle(CustomNPCService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public void enable() {
        service.enable();
    }

    @Override
    public void reload() {
        service.reload();
    }

    @Override
    public void disable() {
        service.disable();
    }
}
