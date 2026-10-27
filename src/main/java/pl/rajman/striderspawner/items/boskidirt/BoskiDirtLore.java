package pl.rajman.striderspawner.items.boskidirt;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** Keeps the item's display text separate from its mechanics. */
final class BoskiDirtLore {
    private BoskiDirtLore() { }

    static void apply(ItemMeta meta) {
        // Muted gold and dark red match the reference tooltip more closely.
        meta.setDisplayName(color("{#AA7700}&l✦ BOSKI DIRT ✦"));
        meta.setLore(List.of(
                color("{#886622}&m────────────────────────────"),
                color("{#FFCC33}✚ &7F » &fpotężny buff"),
                color("{#FF55FF}✿ &7PPM » &ftęczowa owca"),
                color("{#AAAAAA}◉ &7Shift+PPM » &fobrót celu"),
                color("{#AA7733}◉ &7Shift+LPM » &fzakopanie"),
                color("{#FFAA00}⛏ &7Shift+F » &fX-Ray 5s"),
                color("{#FFAA00}⛏ &7Kopanie » &fszansa na skarb"),
                color("{#FFCC33}✚ &7Śmierć » &fdirtowy totem (&75x, potem się rozsypie&f)"),
                color("&r"),
                color("{#AA0000}&l⊗ DIRTOWA FURIA ⊗"),
                color("&fKażdy hit ładuje pasek {#CC6622}▮{#DD8822}▮{#EEAA22}▮{#DDAA33}▮ &f30"),
                color("&fPełny » cios {#CC3333}-30 &4♥"),
                color("&7&oTotem ratuje - bez dobicia"),
                color("{#886622}&m────────────────────────────"),
                color("&7Działa w prawej i lewej ręce")
        ));
    }

    private static String color(String text) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length();) {
            if (i + 9 <= text.length() && text.charAt(i) == '{' && text.charAt(i + 1) == '#'
                    && text.charAt(i + 8) == '}') {
                result.append(ChatColor.of(text.substring(i + 1, i + 8)));
                i += 9;
            } else if (i + 1 < text.length() && text.charAt(i) == '&') {
                char code = Character.toLowerCase(text.charAt(i + 1));
                if ("0123456789abcdefklmnor".indexOf(code) >= 0) {
                    result.append(ChatColor.getByChar(code));
                    i += 2;
                } else {
                    result.append(text.charAt(i++));
                }
            } else {
                result.append(text.charAt(i++));
            }
        }
        return result.toString();
    }
}
