package cn.mythicland.customnpc;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomNPCServiceInteractionTest {

    @Test
    void movementToolAcceptsConfiguredStickCaseInsensitively() {
        assertTrue(CustomNPCService.isStickMaterial(Material.STICK, "stick"));
        assertTrue(CustomNPCService.isStickMaterial(Material.STICK, " STICK "));
    }

    @Test
    void movementToolRejectsOtherMaterialsAndMissingConfiguration() {
        assertFalse(CustomNPCService.isStickMaterial(Material.BLAZE_ROD, "STICK"));
        assertFalse(CustomNPCService.isStickMaterial(Material.STICK, null));
        assertFalse(CustomNPCService.isStickMaterial(null, "STICK"));
    }
}
