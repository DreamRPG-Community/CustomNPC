package cn.mythicland.customnpc;

import cn.mythicland.lib.config.ConfigSupport;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Objects;

/**
 * Validated runtime configuration for CustomNPC.
 */
public record CustomNPCSettings(
        double lineSpacing,
        double nameOffset,
        double viewDistance,
        double selectionRange,
        double selectionRadius,
        String stickMaterial,
        String shopkeeperCreatePermission,
        boolean orientationDebug,
        boolean lifecycleDebug
) {

    public static CustomNPCSettings load(CustomNPCPlugin plugin, FileConfiguration configuration) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(configuration, "configuration");
        double lineSpacing = positive(plugin, configuration, "name.line-spacing", 0.25D);
        double nameOffset = configuration.getDouble("name.offset", 2.15D);
        if (!Double.isFinite(nameOffset)) {
            nameOffset = ConfigSupport.resetToDefault(
                    plugin,
                    configuration,
                    "name.offset",
                    2.15D,
                    "expected a finite number"
            );
        }
        double viewDistance = positive(plugin, configuration, "name.view-distance", 32.0D);
        double selectionRange = positive(plugin, configuration, "selection.range", 6.0D);
        double selectionRadius = positive(plugin, configuration, "selection.radius", 0.75D);
        String stickMaterial = ConfigSupport.getString(plugin, configuration, "selection.stick-material", "STICK")
                .trim();
        String permission = ConfigSupport.getString(
                plugin,
                configuration,
                "shopkeeper.create-permission",
                "customnpc.shopkeeper.create"
        );
        boolean orientationDebug = configuration.getBoolean("debug.orientation", true);
        boolean lifecycleDebug = configuration.getBoolean("debug.lifecycle", true);
        return new CustomNPCSettings(
                lineSpacing,
                nameOffset,
                viewDistance,
                selectionRange,
                selectionRadius,
                stickMaterial,
                permission,
                orientationDebug,
                lifecycleDebug
        );
    }

    private static double positive(
            CustomNPCPlugin plugin,
            FileConfiguration configuration,
            String path,
            double defaultValue
    ) {
        double value = configuration.getDouble(path, defaultValue);
        if (!Double.isFinite(value) || value <= 0.0D) {
            return ConfigSupport.resetToDefault(
                    plugin,
                    configuration,
                    path,
                    defaultValue,
                    "expected a finite positive number"
            );
        }
        return value;
    }

}
