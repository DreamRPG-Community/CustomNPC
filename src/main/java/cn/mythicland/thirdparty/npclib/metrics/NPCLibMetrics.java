package cn.mythicland.thirdparty.npclib.metrics;

import cn.mythicland.thirdparty.bstats.Metrics;
import cn.mythicland.thirdparty.npclib.NPCLib;
import cn.mythicland.thirdparty.npclib.internal.NPCManager;

public class NPCLibMetrics {

    private static final int BSTATS_PLUGIN_ID = 7214;

    public NPCLibMetrics(NPCLib instance) {
        Metrics metrics = new Metrics(instance.getPlugin(), BSTATS_PLUGIN_ID);
        metrics.addCustomChart(new Metrics.SingleLineChart("npcs", () -> NPCManager.getAllNPCs().size()));
    }
}
