package pl.rajman.striderspawner;

import org.junit.jupiter.api.Test;

import java.util.List;
import org.bukkit.entity.EntityType;

import static org.junit.jupiter.api.Assertions.*;

class SpawnerProgressionTest {
    @Test void combinesThreeLevelOneSpawnersToLevelThree() {
        assertEquals(3, SpawnerProgression.combineLevels(List.of(1, 1, 1), 10));
    }

    @Test void sumsThreeEqualHigherLevels() {
        assertEquals(6, SpawnerProgression.combineLevels(List.of(2, 2, 2), 10));
    }

    @Test void rejectsMixedLevelsAndMaximumOverflow() {
        assertThrows(IllegalArgumentException.class, () -> SpawnerProgression.combineLevels(List.of(1, 2, 1), 10));
        assertThrows(IllegalArgumentException.class, () -> SpawnerProgression.combineLevels(List.of(4, 4, 4), 10));
    }

    @Test void multipliesIncomeBySpawnerLevel() {
        assertEquals(3.0, SpawnerProgression.incomePerSecond(1.0, 3));
        assertEquals(15.0, SpawnerProgression.incomePerSecond(5.0, 3));
    }

    @Test void detectsConfiguredMaximumLevel() {
        assertTrue(SpawnerProgression.isAtMaximum(10, 10));
        assertFalse(SpawnerProgression.isAtMaximum(9, 10));
    }

    @Test void eachSpawnerDropsOnlyFromItsMatchingMob() {
        assertTrue(SpawnerProgression.mobDropMatches(EntityType.PIG, EntityType.PIG));
        assertFalse(SpawnerProgression.mobDropMatches(EntityType.PIG, EntityType.COW));
    }
}
