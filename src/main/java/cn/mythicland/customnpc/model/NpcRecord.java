package cn.mythicland.customnpc.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable persisted CustomNPC definition.
 *
 * @param id             stable CustomNPC id
 * @param location       persisted location
 * @param nameLines      floating-text lines
 * @param skin           cached skin, or {@code null}
 * @param commands       immutable command bindings
 * @param shopkeeperId   bound Shopkeepers UUID, or {@code null}
 * @param shopkeeperName custom Shopkeepers title, or {@code null} to follow the NPC name
 */
public record NpcRecord(
        UUID id,
        NpcLocation location,
        List<String> nameLines,
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
        Objects.requireNonNull(commands, "commands");
        commands = List.copyOf(commands);
        if (shopkeeperName != null) {
            shopkeeperName = shopkeeperName.trim();
            if (shopkeeperName.isBlank() || shopkeeperName.equalsIgnoreCase(AUTO_SHOPKEEPER_NAME)) {
                shopkeeperName = null;
            }
        }
    }

    public NpcRecord withLocation(NpcLocation value) {
        return new NpcRecord(id, value, nameLines, skin, commands, shopkeeperId, shopkeeperName);
    }

    public NpcRecord withNameLines(List<String> value) {
        return new NpcRecord(id, location, value, skin, commands, shopkeeperId, shopkeeperName);
    }

    public NpcRecord withSkin(SkinData value) {
        return new NpcRecord(id, location, nameLines, value, commands, shopkeeperId, shopkeeperName);
    }

    public NpcRecord withCommands(List<BoundCommand> value) {
        return new NpcRecord(id, location, nameLines, skin, value, shopkeeperId, shopkeeperName);
    }

    public NpcRecord withShopkeeperId(UUID value) {
        return new NpcRecord(id, location, nameLines, skin, commands, value, shopkeeperName);
    }

    public NpcRecord withShopkeeperName(String value) {
        return new NpcRecord(id, location, nameLines, skin, commands, shopkeeperId, value);
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
