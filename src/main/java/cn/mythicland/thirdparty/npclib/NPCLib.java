/*
 * Copyright (c) 2018 Jitse Boonstra
 */

package cn.mythicland.thirdparty.npclib;

import cn.mythicland.thirdparty.npclib.NPCLibOptions.MovementHandling;
import cn.mythicland.thirdparty.npclib.api.NPC;
import cn.mythicland.thirdparty.npclib.api.utilities.Logger;
import cn.mythicland.thirdparty.npclib.listeners.*;
import cn.mythicland.thirdparty.npclib.metrics.NPCLibMetrics;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class NPCLib {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final List<Listener> listeners;
    private final List<NPC> ownedNpcs;
    private PacketListener packetListener;

    private boolean lifecycleDebug;

    private Class<?> npcClass = null;

    private double autoHideDistance = 50.0;

    private NPCLib(JavaPlugin plugin, MovementHandling moveHandling) {
        this(plugin, moveHandling, false);
    }

    private NPCLib(JavaPlugin plugin, MovementHandling moveHandling, boolean lifecycleDebug) {
        this.plugin = plugin;
        this.logger = new Logger("NPCLib");
        this.listeners = new ArrayList<>();
        this.ownedNpcs = new ArrayList<>();
        this.lifecycleDebug = lifecycleDebug;

        String versionName = plugin.getServer().getClass().getPackage().getName().split("\\.")[3];

        try {
            this.npcClass = Class.forName("cn.mythicland.thirdparty.npclib.nms." + versionName + ".NPC_" + versionName);
        } catch (ClassNotFoundException exception) {
            // Version not supported, error below.
        }

        if (npcClass == null) {
            logger.severe("Failed to initiate. Your server's version (" + versionName + ") is not supported");
            return;
        }

        PluginManager pluginManager = plugin.getServer().getPluginManager();

        registerListener(pluginManager, new PlayerListener(this));
        registerListener(pluginManager, new ChunkListener(this));

        if (moveHandling.usePme) {
            registerListener(pluginManager, new PlayerMoveEventListener());
        } else {
            registerListener(pluginManager, new PeriodicMoveListener(this, moveHandling.updateInterval));
        }

        // Boot the according packet listener.
        this.packetListener = new PacketListener();
        this.packetListener.start(this);

        // Start the bStats metrics system and disable the silly relocate check.
        System.setProperty("bstats.relocatecheck", "false");
        new NPCLibMetrics(this);

        logger.info("Enabled for Minecraft " + versionName);
    }

    public NPCLib(JavaPlugin plugin) {
        this(plugin, MovementHandling.playerMoveEvent());
    }

    public NPCLib(JavaPlugin plugin, NPCLibOptions options) {
        this(plugin, options.moveHandling, options.lifecycleDebug);
    }

    /**
     * @return The JavaPlugin instance.
     */
    public JavaPlugin getPlugin() {
        return plugin;
    }

    /**
     * @return The auto-hide distance.
     */
    public double getAutoHideDistance() {
        return autoHideDistance;
    }

    /**
     * Set a new value for the auto-hide distance.
     * A recommended value (and default) is 50 blocks.
     *
     * @param autoHideDistance The new value.
     */
    public void setAutoHideDistance(double autoHideDistance) {
        this.autoHideDistance = autoHideDistance;
    }

    /**
     * @return The logger NPCLib uses.
     */
    public Logger getLogger() {
        return logger;
    }

    /**
     * @return whether lifecycle diagnostics are enabled
     */
    public boolean isLifecycleDebug() {
        return lifecycleDebug;
    }

    /**
     * Enables or disables diagnostic lifecycle logging for NPC visibility transitions.
     *
     * @param lifecycleDebug whether lifecycle diagnostics should be logged
     */
    public void setLifecycleDebug(boolean lifecycleDebug) {
        this.lifecycleDebug = lifecycleDebug;
    }

    /**
     * Create a new non-player character (NPC).
     *
     * @param text The text you want to sendShowPackets above the NPC (null = no text).
     * @return The NPC object you may use to sendShowPackets it to players.
     */
    public NPC createNPC(List<String> text) {
        try {
            NPC npc = (NPC) npcClass.getConstructors()[0].newInstance(this, text);
            ownedNpcs.add(npc);
            return npc;
        } catch (Exception exception) {
            logger.warning("Failed to create NPC. Please report the following stacktrace message", exception);
        }

        return null;
    }

    /**
     * Create a new non-player character (NPC).
     *
     * @return The NPC object you may use to sendShowPackets it to players.
     */
    public NPC createNPC() {
        return createNPC(null);
    }

    /**
     * Shuts down listeners, packet interception, and all NPCs owned by this library instance.
     */
    public void shutdown() {
        for (NPC npc : new ArrayList<>(ownedNpcs)) npc.destroy();
        ownedNpcs.clear();
        for (Listener listener : listeners) {
            if (listener instanceof PeriodicMoveListener periodicMoveListener) {
                periodicMoveListener.shutdown();
            }
            HandlerList.unregisterAll(listener);
        }
        listeners.clear();
        if (packetListener != null) packetListener.stop();
    }

    private void registerListener(PluginManager pluginManager, Listener listener) {
        pluginManager.registerEvents(listener, plugin);
        listeners.add(listener);
    }
}
