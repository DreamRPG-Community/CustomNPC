package cn.mythicland.thirdparty.npclib.listeners;

import cn.mythicland.thirdparty.npclib.NPCLib;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.UUID;

@SuppressWarnings("this-escape")
public class PeriodicMoveListener extends HandleMoveBase implements Listener {

    private final NPCLib instance;
    private final long updateInterval;

    private final HashMap<UUID, BukkitTask> tasks = new HashMap<>();

    public PeriodicMoveListener(NPCLib instance, long updateInterval) {
        this.instance = instance;
        this.updateInterval = updateInterval;
        for (Player player : Bukkit.getOnlinePlayers()) startTask(player.getUniqueId());
    }

    private void startTask(UUID uuid) {
        if (tasks.containsKey(uuid)) return;
        // purposefully using UUIDs and not holding player references
        tasks.put(uuid, Bukkit.getScheduler().runTaskTimer(instance.getPlugin(), () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) { // safety check
                handleMove(player);
            }
        }, 1L, updateInterval));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        startTask(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        BukkitTask task = tasks.remove(event.getPlayer().getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    public void shutdown() {
        for (BukkitTask task : tasks.values()) task.cancel();
        tasks.clear();
    }

}
