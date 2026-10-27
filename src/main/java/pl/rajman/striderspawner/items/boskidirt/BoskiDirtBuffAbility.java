package pl.rajman.striderspawner.items.boskidirt;

import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** Owns buff selection, duration, and player-facing messages. */
final class BoskiDirtBuffAbility {
    private static final Set<String> POSITIVE_EFFECTS = Set.of(
            "resistance", "fire_resistance", "speed", "strength", "regeneration", "absorption",
            "haste", "jump_boost", "water_breathing", "night_vision", "slow_falling", "health_boost", "luck"
    );

    private final JavaPlugin plugin;
    private final FileConfiguration config;

    BoskiDirtBuffAbility(JavaPlugin plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
    }

    void activate(Player player) {
        List<PotionEffect> available = availableEffects();
        if (available.isEmpty()) {
            player.sendMessage(color("&cNie skonfigurowano pozytywnych efektów Boskiego Dirta."));
            return;
        }
        PotionEffect effect = available.get(ThreadLocalRandom.current().nextInt(available.size()));
        player.addPotionEffect(effect);
        int seconds = Math.max(1, config.getInt("buff.duration-seconds", 60));
        player.sendMessage(color("&6Dirtowa Moc &adała Ci efekt: &e" + displayName(effect.getType())
                + " &ana " + seconds + " sekund."));
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.7f, 1.4f);
    }

    private List<PotionEffect> availableEffects() {
        int durationTicks = Math.max(1, Math.min(Integer.MAX_VALUE / 20,
                config.getInt("buff.duration-seconds", 60))) * 20;
        List<PotionEffect> effects = new ArrayList<>();
        for (String entry : config.getStringList("buff.effects")) {
            String[] parts = entry.split(":", 2);
            String id = parts[0].trim().toLowerCase(Locale.ROOT);
            if (!POSITIVE_EFFECTS.contains(id)) continue;
            PotionEffectType type = PotionEffectType.getByName(id.toUpperCase(Locale.ROOT));
            if (type == null) continue;
            int amplifier = 0;
            if (parts.length == 2) {
                try { amplifier = Math.max(0, Math.min(4, Integer.parseInt(parts[1].trim()))); }
                catch (NumberFormatException ignored) { continue; }
            }
            effects.add(new PotionEffect(type, durationTicks, amplifier, false, true, true));
        }
        return effects;
    }

    private String displayName(PotionEffectType type) {
        return switch (type.getKey().getKey()) {
            case "resistance" -> "Odporność";
            case "fire_resistance" -> "Ognioodporność";
            case "speed" -> "Szybkość";
            case "strength" -> "Siła";
            case "regeneration" -> "Regeneracja";
            case "absorption" -> "Absorpcja";
            case "haste" -> "Szybkie kopanie";
            case "jump_boost" -> "Wysoki skok";
            case "water_breathing" -> "Oddychanie pod wodą";
            case "night_vision" -> "Widzenie w ciemności";
            case "slow_falling" -> "Powolne opadanie";
            case "health_boost" -> "Dodatkowe zdrowie";
            case "luck" -> "Szczęście";
            default -> type.getKey().getKey();
        };
    }

    private static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
