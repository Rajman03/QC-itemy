package pl.rajman.striderspawner.items.boskidirt;

import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.HashMap;

/** Handles temporary client-side block changes and nearby entity outlines. */
final class BoskiDirtXrayAbility {
    private static final class XraySession {
        private final World world;
        private final List<Location> changedBlocks;
        private final List<Entity> glowingEntities;
        private BukkitTask restoreTask;

        private XraySession(World world, List<Location> changedBlocks, List<Entity> glowingEntities) {
            this.world=world;
            this.changedBlocks=changedBlocks;
            this.glowingEntities=glowingEntities;
        }
    }

    private final JavaPlugin plugin;
    private final FileConfiguration config;
    private final Map<UUID, XraySession> sessions = new HashMap<>();

    BoskiDirtXrayAbility(JavaPlugin plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
    }

    void shutdown() {
        for (Map.Entry<UUID, XraySession> entry : new ArrayList<>(sessions.entrySet())) {
            restore(entry.getKey(), entry.getValue());
        }
    }

    void activate(Player player) {
        UUID playerId = player.getUniqueId();

        int radius = Math.max(2, Math.min(8, config.getInt("xray.radius", 8)));
        restore(playerId, sessions.get(playerId));
        World world = player.getWorld();
        Location origin = player.getLocation();
        int centerX = origin.getBlockX(), centerY = origin.getBlockY(), centerZ = origin.getBlockZ();
        List<Location> changed = new ArrayList<>();
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int y = Math.max(world.getMinHeight(), centerY - radius);
                 y <= Math.min(world.getMaxHeight() - 1, centerY + radius); y++) {
                for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                    int dx = x - centerX, dy = y - centerY, dz = z - centerZ;
                    if (dx * dx + dy * dy + dz * dz > radius * radius
                            || !world.isChunkLoaded(x >> 4, z >> 4)) continue;
                    Material material = world.getBlockAt(x, y, z).getType();
                    if (material.isAir() || material == Material.WATER || material == Material.LAVA
                            || isValuableOre(material)) continue;
                    Location location = new Location(world, x, y, z);
                    player.sendBlockChange(location, Material.GLASS.createBlockData());
                    changed.add(location);
                }
            }
        }

        List<Entity> glowing = world.getNearbyEntities(origin, radius, radius, radius).stream()
                .filter(entity -> entity instanceof Player || entity instanceof Mob)
                .filter(entity -> entity != player && !entity.isGlowing()).toList();
        glowing.forEach(entity -> entity.setGlowing(true));
        XraySession session = new XraySession(world, changed, glowing);
        sessions.put(playerId, session);
        player.sendMessage(color("&bX-Ray aktywny przez 5 sekund."));
        world.playSound(origin, Sound.BLOCK_GLASS_PLACE, 0.7f, 1.6f);

        session.restoreTask = Bukkit.getScheduler().runTaskLater(plugin,
                () -> restore(playerId, session), 100L);
    }

    private void restore(UUID playerId, XraySession session) {
        if (session == null || !sessions.remove(playerId, session)) return;
        if (session.restoreTask != null) session.restoreTask.cancel();
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline() && player.getWorld().equals(session.world)) {
            session.changedBlocks.stream()
                    .filter(location -> session.world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4))
                    .forEach(location -> player.sendBlockChange(location, location.getBlock().getBlockData()));
        }
        session.glowingEntities.stream().filter(Entity::isValid).forEach(entity -> entity.setGlowing(false));
    }

    private boolean isValuableOre(Material material) {
        String name = material.name();
        return name.endsWith("_ORE") || material == Material.ANCIENT_DEBRIS
                || material == Material.RAW_IRON_BLOCK || material == Material.RAW_GOLD_BLOCK
                || material == Material.RAW_COPPER_BLOCK;
    }

    private static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
