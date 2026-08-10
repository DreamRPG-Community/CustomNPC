package cn.mythicland.customnpc.storage;

import cn.mythicland.customnpc.model.*;
import cn.mythicland.lib.storage.AtomicYamlStore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Asynchronous YAML repository for CustomNPC definitions.
 */
public final class NpcRepository {

    private final Path target;

    public NpcRepository(Path target) {
        this.target = target.toAbsolutePath().normalize();
    }

    private static Map<UUID, NpcRecord> decode(YamlConfiguration configuration) {
        ConfigurationSection section = configuration.getConfigurationSection("npcs");
        if (section == null) return Map.of();

        Map<UUID, NpcRecord> records = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                ConfigurationSection npc = section.getConfigurationSection(key);
                if (npc == null) continue;
                NpcLocation location = new NpcLocation(
                        requiredWorldName(npc),
                        npc.getDouble("x"),
                        npc.getDouble("y"),
                        npc.getDouble("z"),
                        (float) npc.getDouble("yaw"),
                        (float) npc.getDouble("pitch")
                );
                List<String> lines = npc.getStringList("name");
                if (lines.isEmpty()) lines = List.of(id.toString());
                List<Double> lineSpacings = npc.getDoubleList("name-line-spacing");

                SkinData skin = decodeSkin(npc.getConfigurationSection("skin"));
                List<BoundCommand> commands = decodeCommands(npc.getMapList("commands"));
                UUID shopkeeperId = parseUuid(npc.getString("shopkeeper-id"));
                String shopkeeperName = npc.getString("shopkeeper-name");
                records.put(
                        id,
                        new NpcRecord(
                                id,
                                location,
                                lines,
                                lineSpacings,
                                skin,
                                commands,
                                shopkeeperId,
                                shopkeeperName
                        )
                );
            } catch (RuntimeException exception) {
                // One malformed record must not prevent all other NPCs from loading.
            }
        }
        return Collections.unmodifiableMap(records);
    }

    private static String requiredWorldName(ConfigurationSection section) {
        String value = section.getString("world");
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing world");
        return value.trim();
    }

    private static SkinData decodeSkin(ConfigurationSection section) {
        if (section == null) return null;
        String player = section.getString("player");
        String value = section.getString("value");
        String signature = section.getString("signature");
        if (player == null || value == null || signature == null) return null;
        return new SkinData(player, value, signature);
    }

    private static List<BoundCommand> decodeCommands(List<Map<?, ?>> rawCommands) {
        List<BoundCommand> commands = new ArrayList<>();
        for (Map<?, ?> raw : rawCommands) {
            Object rawMode = raw.get("mode");
            Object rawCommand = raw.get("command");
            if (rawMode == null || rawCommand == null) continue;
            try {
                commands.add(new BoundCommand(
                        CommandExecutionMode.valueOf(rawMode.toString().trim().toUpperCase()),
                        rawCommand.toString()
                ));
            } catch (IllegalArgumentException ignored) {
                // Ignore only the malformed binding; the NPC itself remains usable.
            }
        }
        return List.copyOf(commands);
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static Map<String, Object> encode(Map<UUID, NpcRecord> records) {
        Map<String, Object> npcValues = new LinkedHashMap<>();
        for (NpcRecord record : records.values()) {
            Map<String, Object> values = new LinkedHashMap<>();
            NpcLocation location = record.location();
            values.put("world", location.worldName());
            values.put("x", location.x());
            values.put("y", location.y());
            values.put("z", location.z());
            values.put("yaw", location.yaw());
            values.put("pitch", location.pitch());
            values.put("name", record.nameLines());
            if (!record.nameLineSpacings().isEmpty()) {
                values.put("name-line-spacing", record.nameLineSpacings());
            }
            if (record.skin() != null) {
                SkinData skin = record.skin();
                values.put("skin", Map.of(
                        "player", skin.playerName(),
                        "value", skin.value(),
                        "signature", skin.signature()
                ));
            }
            List<Map<String, String>> commands = new ArrayList<>();
            for (BoundCommand command : record.commands()) {
                commands.add(Map.of(
                        "mode", command.mode().name().toLowerCase(),
                        "command", command.command()
                ));
            }
            values.put("commands", commands);
            if (record.shopkeeperId() != null) values.put("shopkeeper-id", record.shopkeeperId().toString());
            values.put(
                    "shopkeeper-name",
                    record.shopkeeperName() == null ? NpcRecord.AUTO_SHOPKEEPER_NAME : record.shopkeeperName()
            );
            npcValues.put(record.id().toString(), values);
        }
        return Map.of("npcs", npcValues);
    }

    /**
     * Loads a defensive snapshot without touching Bukkit world state.
     *
     * @return future containing valid records
     */
    public CompletableFuture<Map<UUID, NpcRecord>> load() {
        return CompletableFuture.supplyAsync(() -> {
            if (!Files.exists(target)) return Map.of();
            try {
                String content = Files.readString(target);
                YamlConfiguration configuration = new YamlConfiguration();
                configuration.load(new StringReader(content));
                return decode(configuration);
            } catch (IOException | InvalidConfigurationException exception) {
                throw new IllegalStateException("Could not load " + target, exception);
            }
        });
    }

    /**
     * Saves a defensive snapshot atomically.
     *
     * @param records records to save
     * @return completion future
     */
    public CompletableFuture<Void> save(Map<UUID, NpcRecord> records) {
        Map<UUID, NpcRecord> snapshot = Map.copyOf(records);
        return CompletableFuture.runAsync(() -> {
            try {
                AtomicYamlStore.write(target, encode(snapshot));
            } catch (IOException exception) {
                throw new IllegalStateException("Could not save " + target, exception);
            }
        });
    }
}
