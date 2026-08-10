package cn.mythicland.customnpc.shopkeepers;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Optional integration boundary for Shopkeepers.
 */
public interface ExternalShopkeeperIntegration {

    void enable();

    void disable();

    /**
     * Reconciles Shopkeepers bindings after the CustomNPC repository has loaded.
     */
    default void onNpcsLoaded() {
    }

    void openOrCreate(Player player, UUID npcId);

    /**
     * Opens the trading window for an existing Shopkeeper bound to an NPC.
     *
     * @param player player requesting the trading window
     * @param npcId  CustomNPC identifier
     * @return {@code true} when a bound Shopkeeper exists and the interaction
     * was handled, even if Shopkeepers rejected opening the window
     * @implNote Must be invoked on the Bukkit primary server thread.
     */
    boolean openTrading(Player player, UUID npcId);

    /**
     * Synchronizes the title of a bound Shopkeeper with the current NPC record.
     *
     * @param npcId CustomNPC identifier
     */
    default void synchronizeName(UUID npcId) {
    }

    boolean moveForNpc(UUID npcId, Location location);

    void deleteForNpc(UUID npcId);
}
