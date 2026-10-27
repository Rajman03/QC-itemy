package pl.rajman.striderspawner.items.spawners;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;

/** Owns the spawner item format and the spawner-specific configuration. */
public final class SpawnerItemModule {
    private static final DecimalFormat MONEY=new DecimalFormat("#,##0",DecimalFormatSymbols.getInstance(Locale.US));
    private final JavaPlugin plugin;
    private final NamespacedKey itemKey,ownerKey,typeKey,levelKey,pendingKey,remainderKey,tickKey;
    private FileConfiguration config;

    public SpawnerItemModule(JavaPlugin plugin) {
        this.plugin=plugin;
        itemKey=new NamespacedKey(plugin,"strider_spawner"); ownerKey=new NamespacedKey(plugin,"owner");
        typeKey=new NamespacedKey(plugin,"mob_type"); levelKey=new NamespacedKey(plugin,"level");
        pendingKey=new NamespacedKey(plugin,"pending"); remainderKey=new NamespacedKey(plugin,"income_remainder"); tickKey=new NamespacedKey(plugin,"last_tick");
    }

    public void load() {
        File file=new File(plugin.getDataFolder(),"items/spawners/config.yml");
        File oldFile=new File(plugin.getDataFolder(),"items/spawner/config.yml");
        if(!file.exists()&&oldFile.isFile()){
            file.getParentFile().mkdirs();
            try{Files.copy(oldFile.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);plugin.getLogger().info("Przeniesiono konfigurację z items/spawner do items/spawners.");}
            catch(IOException ex){throw new IllegalStateException("Nie można przenieść starej konfiguracji spawnerów.",ex);}
        }
        boolean firstRun=!file.exists();
        if (firstRun) plugin.saveResource("items/spawners/config.yml",false);
        config=YamlConfiguration.loadConfiguration(file);
        if (firstRun) {
            ConfigurationSection legacy=plugin.getConfig().getConfigurationSection("spawners");
            if (legacy!=null) {
                for (String type:legacy.getKeys(false)) {
                    ConfigurationSection section=legacy.getConfigurationSection(type);
                    if (section==null) continue;
                    for (String path:section.getKeys(true))
                        if (section.get(path)!=null && !(section.get(path) instanceof ConfigurationSection)) config.set("spawners."+type+"."+path,section.get(path));
                }
                save(file);
                plugin.getLogger().info("Przeniesiono konfigurację spawnerów do items/spawners/config.yml.");
            }
        }
    }

    public NamespacedKey itemKey(){return itemKey;}
    public NamespacedKey ownerKey(){return ownerKey;}
    public NamespacedKey typeKey(){return typeKey;}
    public NamespacedKey levelKey(){return levelKey;}
    public NamespacedKey pendingKey(){return pendingKey;}
    public NamespacedKey remainderKey(){return remainderKey;}
    public NamespacedKey tickKey(){return tickKey;}
    public FileConfiguration config(){return config;}
    public List<String> types(){ConfigurationSection section=config.getConfigurationSection("spawners");return section==null?List.of():section.getKeys(false).stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();}
    private String path(EntityType type){return "spawners."+type.name()+".";}
    public boolean configured(EntityType type){return config.getBoolean(path(type)+"enabled",false);}
    public boolean enabled(String name){return config.getBoolean("spawners."+name+".enabled",false);}
    public int maxLevel(EntityType type){return Math.min(1000,Math.max(1,config.getInt(path(type)+"max-level",10)));}
    public double income(EntityType type,int level){double base=config.getDouble(path(type)+"income-per-second-per-level",5.0);if(!Double.isFinite(base)||base<0)return 0;double total=base*Math.max(1,level);return Double.isFinite(total)?total:Double.MAX_VALUE;}
    public double baseIncome(EntityType type){return config.getDouble(path(type)+"income-per-second-per-level",5.0);}
    public double dropChance(EntityType type){double chance=config.getDouble(path(type)+"drop.chance",0);return Double.isFinite(chance)?Math.max(0,Math.min(1,chance)):0;}
    public int dropMin(EntityType type){return Math.min(64,Math.max(1,config.getInt(path(type)+"drop.min-amount",1)));}
    public int dropMax(EntityType type){return Math.min(64,Math.max(dropMin(type),config.getInt(path(type)+"drop.max-amount",dropMin(type))));}

    public ItemStack create(EntityType type,int level,UUID owner){return create(type,level,owner,0.0);}
    public ItemStack create(EntityType type,int level,UUID owner,double pending){return create(type,level,owner,pending,0);}
    public ItemStack create(EntityType type,int level,UUID owner,double pending,double remainder){
        level=Math.max(1,Math.min(maxLevel(type),level));
        if(!Double.isFinite(pending)||pending<0)pending=0;
        if(!Double.isFinite(remainder)||remainder<0)remainder=0;
        double total=pending+remainder;
        if(!Double.isFinite(total)||total<0)total=0;
        pending=Math.floor(total);
        remainder=total-pending;
        ItemStack item=new ItemStack(Material.SPAWNER); ItemMeta meta=item.getItemMeta(); String mob=type.name();
        meta.setRarity(ItemRarity.EPIC);
        meta.setDisplayName(color("&8(")+ChatColor.AQUA+mob+color("&8) &e&lSPAWNER &e⭐&e"+level));
        meta.setLore(List.of(color("&8▸ &fTYP: &b"+mob),color("&8▸ &fPOZIOM: &e"+level),color("&r"),
                color("&8▸ &fDOCHÓD: &a"+wholeDollars(income(type,level))+" &a$/s"),
                color("&8▸ &fZGROMADZONE: &e"+whole(pending+remainder)+" &6$"),color("&r"),
                color("&8▸ &fPOŁĄCZ W &a&nCRAFTINGU"),color("&8▸ &fLUB KLIKNIJ SWOJ &b&nSPAWNER"),color("&r"),
                color("&8Kliknięcie jajem przywołania:"),color("&9Ustawia typ moba")));
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        meta.getPersistentDataContainer().set(itemKey,PersistentDataType.BYTE,(byte)1);
        meta.getPersistentDataContainer().set(typeKey,PersistentDataType.STRING,mob);
        meta.getPersistentDataContainer().set(levelKey,PersistentDataType.INTEGER,level);
        meta.getPersistentDataContainer().set(pendingKey,PersistentDataType.DOUBLE,Math.max(0,pending));
        meta.getPersistentDataContainer().set(remainderKey,PersistentDataType.DOUBLE,remainder);
        if(owner!=null)meta.getPersistentDataContainer().set(ownerKey,PersistentDataType.STRING,owner.toString());
        item.setItemMeta(meta);return item;
    }

    public ItemStack menuItem(EntityType type,int level,UUID owner,double pending){
        ItemStack item=create(type,level,owner,pending); ItemMeta meta=item.getItemMeta();
        String ownerName=owner==null?"Brak":Optional.ofNullable(Bukkit.getOfflinePlayer(owner).getName()).orElse("Brak");
        String mob=type.name();
        meta.setDisplayName(color("&d&lSPAWNER &6⭐&f"+level));
        meta.setLore(List.of(color("&8Kliknięcie jajem przywołania:"),color("&9Ustawia typ moba"),color("&r"),
                color("&8▸ &fWLAŚCICIEL: &d"+ownerName),color("&8▸ &fTYP: &b"+mob),
                color("&8▸ &fPOZIOM: &e"+level),color("&r"),
                color("&8▸ &fDOCHÓD: &a"+wholeDollars(income(type,level))+" &a$/s"),color("&r"),
                color("&8▸ &fPOŁĄCZ Z INNYM SPAWNEREM"),color("&8▸ &fTEGO SAMEGO &b&nTYPU&r&f, ABY"),
                color("&8▸ &fZWIĘKSZYĆ JEGO &6&nPOZIOM")));
        item.setItemMeta(meta);return item;
    }

    public boolean isItem(ItemStack item){return item!=null&&item.getType()==Material.SPAWNER&&item.hasItemMeta()&&item.getItemMeta().getPersistentDataContainer().has(itemKey,PersistentDataType.BYTE);}
    public UUID owner(ItemStack item,UUID fallback){if(!isItem(item))return fallback;String value=item.getItemMeta().getPersistentDataContainer().get(ownerKey,PersistentDataType.STRING);try{return value==null?fallback:UUID.fromString(value);}catch(IllegalArgumentException ex){return fallback;}}
    public EntityType type(ItemStack item){try{return EntityType.valueOf(item.getItemMeta().getPersistentDataContainer().getOrDefault(typeKey,PersistentDataType.STRING,"STRIDER"));}catch(Exception ex){return EntityType.STRIDER;}}
    public int level(ItemStack item){return item.getItemMeta().getPersistentDataContainer().getOrDefault(levelKey,PersistentDataType.INTEGER,1);}
    public double pending(ItemStack item){return item.getItemMeta().getPersistentDataContainer().getOrDefault(pendingKey,PersistentDataType.DOUBLE,0d);}
    public double remainder(ItemStack item){return item.getItemMeta().getPersistentDataContainer().getOrDefault(remainderKey,PersistentDataType.DOUBLE,0d);}
    private static String whole(double amount){return MONEY.format(Math.max(0,amount));}
    private static String wholeDollars(double amount){return MONEY.format(Math.floor(Math.max(0,amount)));}
    private static String color(String value){
        if(value.startsWith("&8Kliknięcie jajem przywołania:")||value.startsWith("&9Ustawia typ moba"))
            return ChatColor.translateAlternateColorCodes('&',value);
        return styledColor(value);
    }
    public static String styledColor(String value){return ChatColor.translateAlternateColorCodes('&',smallCapsBeforeColon(value));}
    private static String smallCapsBeforeColon(String value){
        int colon=value.indexOf(':');
        return colon<0?smallCaps(value):smallCaps(value.substring(0,colon))+value.substring(colon);
    }
    private static String smallCaps(String value){
        StringBuilder result=new StringBuilder(value.length());
        for(int i=0;i<value.length();i++){
            char current=value.charAt(i);
            if(current=='&'&&i+1<value.length()){result.append(current).append(value.charAt(++i));continue;}
            char lower=Character.toLowerCase(current);
            result.append(switch(lower){
                case 'a'->"ᴀ";case 'b'->"ʙ";case 'c'->"ᴄ";case 'd'->"ᴅ";case 'e'->"ᴇ";
                case 'f'->"ꜰ";case 'g'->"ɢ";case 'h'->"ʜ";case 'i'->"ɪ";case 'j'->"ᴊ";
                case 'k'->"ᴋ";case 'l'->"ʟ";case 'm'->"ᴍ";case 'n'->"ɴ";case 'o'->"ᴏ";
                case 'p'->"ᴘ";case 'r'->"ʀ";case 's'->"s";case 't'->"ᴛ";case 'u'->"ᴜ";
                case 'v'->"ᴠ";case 'w'->"ᴡ";case 'y'->"ʏ";case 'z'->"ᴢ";
                case 'ą','ć','ę','ń','ó','ś','ź','ż'->String.valueOf(lower);
                default->String.valueOf(current);
            });
        }
        return result.toString();
    }
    private void save(File file){try{config.save(file);}catch(IOException ex){throw new IllegalStateException("Nie można zapisać "+file,ex);}}
}
