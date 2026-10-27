package pl.rajman.striderspawner;

import org.bukkit.entity.EntityType;

import java.util.Collection;

/** Pure rules for combining spawners and calculating their configured income. */
final class SpawnerProgression {
    private SpawnerProgression() { }

    static int combineLevels(Collection<Integer> levels, int maxLevel) {
        if (levels == null || (levels.size() != 2 && levels.size() != 3)) throw new IllegalArgumentException("Two or three spawners are required");
        if (maxLevel < 1) throw new IllegalArgumentException("Maximum level must be positive");
        Integer first = null;
        for (Integer level : levels) {
            if (level == null || level < 1) throw new IllegalArgumentException("Spawner levels must be positive");
            if (first == null) first = level;
            else if (!first.equals(level)) throw new IllegalArgumentException("Spawner levels must match");
        }
        long total = (long) first * levels.size();
        if (total > maxLevel) throw new IllegalArgumentException("Combined level exceeds the configured maximum");
        return (int) total;
    }

    static double incomePerSecond(double incomePerLevel, int level) {
        if (!Double.isFinite(incomePerLevel) || incomePerLevel < 0 || level < 1)
            throw new IllegalArgumentException("Income and level must be valid non-negative values");
        return incomePerLevel * level;
    }

    static boolean isAtMaximum(int level, int maxLevel) {
        return maxLevel > 0 && level >= maxLevel;
    }

    static boolean mobDropMatches(EntityType spawnerType, EntityType killedMob) {
        return spawnerType != null && spawnerType == killedMob;
    }
}
