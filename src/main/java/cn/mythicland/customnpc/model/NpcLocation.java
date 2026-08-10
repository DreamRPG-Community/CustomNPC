package cn.mythicland.customnpc.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;

/**
 * Serializable NPC location that does not access Bukkit world state off the main thread.
 */
public record NpcLocation(
        String worldName,
        double x,
        double y,
        double z,
        float yaw,
        float pitch
) {

    public NpcLocation {
        worldName = Objects.requireNonNull(worldName, "worldName").trim();
        if (worldName.isBlank()) throw new IllegalArgumentException("worldName cannot be blank");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("NPC location coordinates must be finite");
        }
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new IllegalArgumentException("NPC location angles must be finite");
        }
    }

    /**
     * Creates a serializable location from a Bukkit location.
     *
     * @param location Bukkit location
     * @return immutable location
     */
    public static NpcLocation from(Location location) {
        Objects.requireNonNull(location, "location");
        World world = Objects.requireNonNull(location.getWorld(), "location.world");
        return new NpcLocation(world.getName(), location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch());
    }

    /**
     * Resolves this location on the Bukkit primary thread.
     *
     * @return location, or {@code null} when its world is not loaded
     */
    public Location resolve() {
        World world = Bukkit.getWorld(worldName);
        return world == null ? null : new Location(world, x, y, z, yaw, pitch);
    }
}
