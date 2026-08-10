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
