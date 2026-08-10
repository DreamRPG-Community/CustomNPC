package cn.mythicland.customnpc.api;

import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

/**
 * Public read-only CustomNPC service contract.
 */
public interface CustomNPCApi {

    /**
     * Returns the NPC selected by a player.
     *
     * @param player player
     * @return selected id, or empty when no NPC is selected
     */
    Optional<UUID> selectedNpc(Player player);

    /**
     * Checks whether an NPC exists.
     *
     * @param id stable NPC id
     * @return whether the NPC exists
     */
    boolean exists(UUID id);
}
