package cn.mythicland.customnpc.storage;

import cn.mythicland.customnpc.model.NpcLocation;
import cn.mythicland.customnpc.model.NpcRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class NpcRepositoryTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void automaticShopkeeperNameRoundTripsAsAuto() throws Exception {
        UUID id = UUID.randomUUID();
        NpcRepository repository = new NpcRepository(temporaryDirectory.resolve("npcs.yml"));
        repository.save(Map.of(id, npc(id, "auto"))).join();

        assertNotNull(Files.readString(temporaryDirectory.resolve("npcs.yml")));
        NpcRecord loaded = repository.load().join().get(id);

        assertNotNull(loaded);
        assertNull(loaded.shopkeeperName());
        assertEquals("Village Merchant", loaded.resolvedShopkeeperName());
    }

    @Test
    void customShopkeeperNameRoundTrips() {
        UUID id = UUID.randomUUID();
        NpcRepository repository = new NpcRepository(temporaryDirectory.resolve("npcs.yml"));
        repository.save(Map.of(id, npc(id, "Special Deals"))).join();

        NpcRecord loaded = repository.load().join().get(id);

        assertNotNull(loaded);
        assertEquals("Special Deals", loaded.shopkeeperName());
        assertEquals("Special Deals", loaded.resolvedShopkeeperName());
    }

    @Test
    void reloadReadsAllNameLinesFromAnExternallyEditedFile() throws Exception {
        UUID id = UUID.randomUUID();
        Path file = temporaryDirectory.resolve("npcs.yml");
        Files.writeString(
                file,
                """
                npcs:
                  %s:
                    world: world
                    x: 11.5
                    y: 7.0
                    z: -23.5
                    yaw: -90.0
                    pitch: 0.0
                    name:
                      - '123'
                      - '234'
                """.formatted(id)
        );

        NpcRecord loaded = new NpcRepository(file).load().join().get(id);

        assertNotNull(loaded);
        assertEquals(List.of("123", "234"), loaded.nameLines());
    }

    @Test
    void customNameLineSpacingsRoundTrip() {
        UUID id = UUID.randomUUID();
        NpcRecord record = new NpcRecord(
                id,
                new NpcLocation("world", 0.5D, 64.0D, 0.5D, 0.0F, 0.0F),
                List.of("Top", "Middle", "Bottom"),
                List.of(0.35D, 0.6D, 0.0D),
                null,
                List.of(),
                null,
                null
        );
        NpcRepository repository = new NpcRepository(temporaryDirectory.resolve("npcs.yml"));

        repository.save(Map.of(id, record)).join();

        assertEquals(List.of(0.35D, 0.6D, 0.0D), repository.load().join().get(id).nameLineSpacings());
    }

    @Test
    void legacyRuntimeSpacingOrderIsIgnoredOnLoad() throws Exception {
        UUID id = UUID.randomUUID();
        Path file = temporaryDirectory.resolve("npcs.yml");
        Files.writeString(
                file,
                """
                npcs:
                  %s:
                    world: world
                    x: 0.5
                    y: 64.0
                    z: 0.5
                    name:
                      - 'Top'
                      - 'Bottom'
                    name-line-spacing:
                      - 0.0
                      - 0.35
                """.formatted(id)
        );

        NpcRecord loaded = new NpcRepository(file).load().join().get(id);

        assertNull(loaded);
    }

    private static NpcRecord npc(UUID id, String shopkeeperName) {
        return new NpcRecord(
                id,
                new NpcLocation("world", 0.5D, 64.0D, 0.5D, 0.0F, 0.0F),
                List.of("Village", "Merchant"),
                null,
                List.of(),
                null,
                shopkeeperName
        );
    }
}
