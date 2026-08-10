package cn.mythicland.customnpc;

import cn.mythicland.lib.bootstrap.annotation.ConfigComponent;
import cn.mythicland.lib.config.ConfigValue;
import cn.mythicland.lib.config.ConfigView;
import cn.mythicland.lib.config.ConfigurableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Annotation-bound runtime configuration for CustomNPC.
 */
@ConfigComponent
public final class CustomNPCSettings implements ConfigurableComponent {

    private volatile Snapshot snapshot;

    static List<Double> defaultNameLineSpacings(
            int lineCount,
            List<Double> configured
    ) {
        if (lineCount < 1) throw new IllegalArgumentException("lineCount must be positive");
        Objects.requireNonNull(configured, "configured");
        List<Double> gaps = adjacentGaps(configured);
        double fallback = gaps.isEmpty() ? 0.0D : gaps.get(gaps.size() - 1);
        List<Double> result = new ArrayList<>(lineCount);
        for (int index = 0; index < lineCount - 1; index++) {
            result.add(index < gaps.size() ? gaps.get(index) : fallback);
        }
        result.add(0.0D);
        return List.copyOf(result);
    }

    private static List<Double> resolveRuntimeLineSpacings(
            int lineCount,
            List<Double> configured
    ) {
        List<Double> gaps = adjacentGaps(configured);
        List<Double> resolved = new ArrayList<>(lineCount);
        resolved.add(0.0D);
        double fallback = gaps.isEmpty() ? 0.0D : gaps.get(gaps.size() - 1);
        for (int index = 1; index < lineCount; index++) {
            resolved.add(index - 1 < gaps.size() ? gaps.get(index - 1) : fallback);
        }
        return List.copyOf(resolved);
    }

    private static List<Double> adjacentGaps(List<Double> configured) {
        if (configured.size() == 1) return configured;
        return configured.subList(0, configured.size() - 1);
    }

    private static void validateLineSpacings(List<Double> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("name.line-spacing must contain at least one entry");
        }
        for (Double value : values) {
            if (value == null || !Double.isFinite(value) || value < 0.0D) {
                throw new IllegalArgumentException("name.line-spacing must be finite and non-negative");
            }
        }
        if (values.size() > 1
                && Double.compare(values.get(values.size() - 1), 0.0D) != 0) {
            throw new IllegalArgumentException("name.line-spacing must end with zero");
        }
    }

    @Override
    public void reload(ConfigView configuration) {
        Snapshot next = configuration.bind(Snapshot.class);
        validateLineSpacings(next.lineSpacings());
        snapshot = next;
    }

    /**
     * Resolves the configured per-line gaps for a display.
     *
     * <p>The persisted format has one entry per name line, from top to bottom. Each entry is the
     * gap to the next line and the final entry is zero. This method converts that format to Lib's
     * runtime format, whose index zero is the required base entry and whose later entries are the
     * gaps from the anchor to each following line.</p>
     *
     * @param lineCount number of name lines
     * @return immutable gaps matching {@code lineCount}
     */
    public List<Double> lineSpacings(int lineCount) {
        return lineSpacings(lineCount, current().lineSpacings());
    }

    /**
     * Resolves a per-NPC override, falling back to the global configuration when it is empty.
     *
     * @param lineCount        number of name lines
     * @param overrideSpacings per-NPC spacing override, or an empty list for global defaults
     * @return immutable gaps matching {@code lineCount}
     */
    public List<Double> lineSpacings(int lineCount, List<Double> overrideSpacings) {
        if (lineCount < 1) throw new IllegalArgumentException("lineCount must be positive");
        Objects.requireNonNull(overrideSpacings, "overrideSpacings");
        List<Double> configured = overrideSpacings.isEmpty()
                ? current().lineSpacings()
                : overrideSpacings;
        return resolveRuntimeLineSpacings(lineCount, configured);
    }

    /**
     * Creates the persisted one-to-one spacing list for a newly created NPC.
     *
     * @param lineCount number of name lines
     * @return one spacing value per line, with the last value fixed at zero
     */
    public List<Double> defaultNameLineSpacings(int lineCount) {
        if (lineCount < 1) throw new IllegalArgumentException("lineCount must be positive");
        return defaultNameLineSpacings(lineCount, current().lineSpacings());
    }

    public double nameOffset() {
        return current().nameOffset();
    }

    public double viewDistance() {
        return current().viewDistance();
    }

    public double selectionRange() {
        return current().selectionRange();
    }

    public double selectionRadius() {
        return current().selectionRadius();
    }

    public String stickMaterial() {
        return current().stickMaterial();
    }

    public String shopkeeperCreatePermission() {
        return current().shopkeeperCreatePermission();
    }

    public boolean orientationDebug() {
        return current().orientationDebug();
    }

    private Snapshot current() {
        return Objects.requireNonNull(snapshot, "CustomNPC settings have not been loaded");
    }

    private record Snapshot(
            @ConfigValue(
                    path = "name.line-spacing",
                    defaultValue = "0.3,0.0",
                    nonNegative = true
            )
            List<Double> lineSpacings,
            @ConfigValue(
                    path = "name.offset",
                    defaultValue = "2.3"
            )
            double nameOffset,
            @ConfigValue(
                    path = "name.view-distance",
                    defaultValue = "32.0",
                    positive = true
            )
            double viewDistance,
            @ConfigValue(
                    path = "selection.range",
                    defaultValue = "6.0",
                    positive = true
            )
            double selectionRange,
            @ConfigValue(
                    path = "selection.radius",
                    defaultValue = "0.75",
                    positive = true
            )
            double selectionRadius,
            @ConfigValue(
                    path = "selection.stick-material",
                    defaultValue = "STICK",
                    nonBlank = true
            )
            String stickMaterial,
            @ConfigValue(
                    path = "shopkeeper.create-permission",
                    defaultValue = "customnpc.shopkeeper.create",
                    nonBlank = true
            )
            String shopkeeperCreatePermission,
            @ConfigValue(
                    path = "debug.orientation",
                    defaultValue = "false"
            )
            boolean orientationDebug
    ) {
    }
}
