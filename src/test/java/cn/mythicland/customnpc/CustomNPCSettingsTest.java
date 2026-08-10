package cn.mythicland.customnpc;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CustomNPCSettingsTest {

    @Test
    void bundledConfigurationDisablesOrientationDebugByDefault() throws IOException {
        try (InputStream resource = CustomNPCSettingsTest.class.getResourceAsStream("/config.yml")) {
            assertNotNull(resource);
            FileConfiguration configuration = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(resource, StandardCharsets.UTF_8)
            );

            assertFalse(configuration.getBoolean("debug.orientation"));
            assertNull(configuration.get("debug.lifecycle"));
            assertEquals(2.3D, configuration.getDouble("name.offset"));
            assertEquals(List.of(0.3D, 0.0D), configuration.getDoubleList("name.line-spacing"));
        }
    }

}
