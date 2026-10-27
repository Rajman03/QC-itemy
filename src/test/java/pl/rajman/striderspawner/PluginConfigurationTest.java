package pl.rajman.striderspawner;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class PluginConfigurationTest {
    @Test void defaultMobDropAndProgressionSettingsAreValidYaml() {
        var resource=getClass().getClassLoader().getResourceAsStream("items/spawners/config.yml");
        assertNotNull(resource);
        YamlConfiguration config=YamlConfiguration.loadConfiguration(new InputStreamReader(resource, StandardCharsets.UTF_8));
        assertNotNull(config.getConfigurationSection("spawners"));
        for (String type : new String[]{"STRIDER", "PIG", "COW"}) {
            String path="spawners."+type+".";
            assertTrue(config.getBoolean(path+"enabled"));
            assertTrue(config.getInt(path+"max-level") >= 3);
            assertTrue(config.getDouble(path+"income-per-second-per-level") >= 0);
            assertTrue(config.getDouble(path+"drop.chance") >= 0 && config.getDouble(path+"drop.chance") <= 1);
        }
    }

    @Test void pluginDeclaresVaultAsRequiredDependency() {
        var resource=getClass().getClassLoader().getResourceAsStream("plugin.yml");
        assertNotNull(resource);
        YamlConfiguration descriptor=YamlConfiguration.loadConfiguration(new InputStreamReader(resource, StandardCharsets.UTF_8));
        assertEquals("pl.rajman.striderspawner.StriderSpawnerPlugin",descriptor.getString("main"));
        assertTrue(descriptor.getStringList("depend").contains("Vault"));
    }

    @Test void pickaxeModuleIncludesN1AndConfigurableLevelsThreeThroughSix() {
        var resource=getClass().getClassLoader().getResourceAsStream("items/kilofy/config.yml");
        assertNotNull(resource);
        YamlConfiguration config=YamlConfiguration.loadConfiguration(new InputStreamReader(resource, StandardCharsets.UTF_8));
        assertEquals(java.util.Set.of("1", "3", "4", "5", "6"), config.getConfigurationSection("levels").getKeys(false));
        assertTrue(config.getBoolean("levels.1.enabled"));
        assertEquals("Kilof N1", config.getString("levels.1.name"));
        assertEquals(1, config.getInt("levels.1.area-size"));
        for (int level=3; level<=6; level++) {
            assertTrue(config.getBoolean("levels."+level+".enabled"));
            assertEquals(level, config.getInt("levels."+level+".area-size"));
        }
    }

    @Test void eachItemCategoryHasItsOwnConfiguration() {
        for (String category : new String[]{"kilofy", "spawners", "n1mc"})
            assertNotNull(getClass().getClassLoader().getResource("items/"+category+"/config.yml"), category);
    }

    @Test void onlySingleBlockPickaxesCanDropStoneRewards() {
        assertTrue(pl.rajman.striderspawner.items.kilofy.MiningPickaxeItem.canDropStoneRewards(1,1));
        assertFalse(pl.rajman.striderspawner.items.kilofy.MiningPickaxeItem.canDropStoneRewards(1,3));
        assertFalse(pl.rajman.striderspawner.items.kilofy.MiningPickaxeItem.canDropStoneRewards(3,3));
        assertFalse(pl.rajman.striderspawner.items.kilofy.MiningPickaxeItem.canDropStoneRewards(3,1));
        assertFalse(pl.rajman.striderspawner.items.kilofy.MiningPickaxeItem.canDropStoneRewards(6,6));
    }
}
