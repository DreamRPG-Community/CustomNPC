package cn.mythicland.customnpc.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable persisted CustomNPC definition.
 *
 * @param id               stable CustomNPC id
 * @param location         persisted location
 * @param nameLines        floating-text lines
 * @param nameLineSpacings one-to-one gaps in name order; the last entry is zero; an empty list uses global defaults
 * @param skin             cached skin, or {@code null}
 * @param commands         immutable command bindings
 * @param shopkeeperId     bound Shopkeepers UUID, or {@code null}
 * @param shopkeeperName   custom Shopkeepers title, or {@code null} to follow the NPC name
 */
public record NpcRecord(
        UUID id,
        NpcLocation location,
        List<String> nameLines,
        List<Double> nameLineSpacings,
        SkinData skin,
        List<BoundCommand> commands,
        UUID shopkeeperId,
        String shopkeeperName
) {

    /**
     * Persisted value that selects automatic Shopkeepers title detection.
     */
    public static final String AUTO_SHOPKEEPER_NAME = "auto";

    public NpcRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(nameLines, "nameLines");
        if (nameLines.isEmpty() || nameLines.size() > 16) {
            throw new IllegalArgumentException("NPC names must contain between one and sixteen lines");
        }
        nameLines = nameLines.stream()
                .map(line -> Objects.requireNonNull(line, "name line"))
                .toList();
        nameLineSpacings = copyNameLineSpacings(nameLines.size(), nameLineSpacings);
        Objects.requireNonNull(commands, "commands");
        commands = List.copyOf(commands);
        if (shopkeeperName != null) {
            shopkeeperName = shopkeeperName.trim();
            if (shopkeeperName.isBlank() || shopkeeperName.equalsIgnoreCase(AUTO_SHOPKEEPER_NAME)) {
                shopkeeperName = null;
            }
        }
    }

    public NpcRecord(
            UUID id,
            NpcLocation location,
            List<String> nameLines,
            SkinData skin,
            List<BoundCommand> commands,
            UUID shopkeeperId,
            String shopkeeperName
    ) {
        this(id, location, nameLines, List.of(), skin, commands, shopkeeperId, shopkeeperName);
    }

    private static List<Double> copyNameLineSpacings(
            int lineCount,
            List<Double> values
    ) {
        Objects.requireNonNull(values, "nameLineSpacings");
        if (values.isEmpty()) return List.of();
        if (values.size() != lineCount) {
            throw new IllegalArgumentException("NPC name spacing must match the number of name lines");
        }
        List<Double> copied = values.stream()
                .map(value -> Objects.requireNonNull(value, "name line spacing"))
                .toList();
        for (Double spacing : copied) {
            if (!Double.isFinite(spacing) || spacing < 0.0D) {
                throw new IllegalArgumentException("NPC name spacing must be finite and non-negative");
            }
        }

        if (Double.compare(copied.get(copied.size() - 1), 0.0D) != 0) {
            throw new IllegalArgumentException("NPC name line spacing last entry must be zero");
        }
        return List.copyOf(copied);
    }

    public NpcRecord withLocation(NpcLocation value) {
        return new NpcRecord(id, value, nameLines, nameLineSpacings, skin, commands, shopkeeperId, shopkeeperName);
    }

    public NpcRecord withNameLines(List<String> value) {
        return new NpcRecord(id, location, value, nameLineSpacings, skin, commands, shopkeeperId, shopkeeperName);
    }

    public NpcRecord withNameLineSpacings(List<Double> value) {
        return new NpcRecord(id, location, nameLines, value, skin, commands, shopkeeperId, shopkeeperName);
    }

    public NpcRecord withSkin(SkinData value) {
        return new NpcRecord(id, location, nameLines, nameLineSpacings, value, commands, shopkeeperId, shopkeeperName);
    }

    public NpcRecord withCommands(List<BoundCommand> value) {
        return new NpcRecord(id, location, nameLines, nameLineSpacings, skin, value, shopkeeperId, shopkeeperName);
    }

    public NpcRecord withShopkeeperId(UUID value) {
        return new NpcRecord(id, location, nameLines, nameLineSpacings, skin, commands, value, shopkeeperName);
    }

    public NpcRecord withShopkeeperName(String value) {
        return new NpcRecord(id, location, nameLines, nameLineSpacings, skin, commands, shopkeeperId, value);
    }

    /**
     * Returns the title used by the Shopkeepers trading window.
     *
     * @return configured title, or the current NPC name joined into one line
     */
    public String resolvedShopkeeperName() {
        if (shopkeeperName != null) return shopkeeperName;
        return String.join(" ", nameLines).trim();
    }
}
