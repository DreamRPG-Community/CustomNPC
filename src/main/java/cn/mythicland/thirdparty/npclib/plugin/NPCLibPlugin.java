/*
 * Copyright (c) 2018 Jitse Boonstra
 */

package cn.mythicland.thirdparty.npclib.plugin;

import org.bukkit.plugin.java.JavaPlugin;

public class NPCLibPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("NPCLib classes loaded");
    }
}
