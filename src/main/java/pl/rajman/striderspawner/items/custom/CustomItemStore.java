package pl.rajman.striderspawner.items.custom;

import org.bukkit.inventory.ItemStack;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

/** Stores every user-added item in its own folder: items/custom/<id>/config.yml. */
public final class CustomItemStore {
    private final JavaPlugin plugin;
    private final File itemFolders;
    private final Map<String,ItemStack> cache=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    public CustomItemStore(JavaPlugin plugin){this.plugin=plugin;itemFolders=new File(plugin.getDataFolder(),"items/custom");}

    public void load(){
        if(!itemFolders.exists()&&!itemFolders.mkdirs())throw new IllegalStateException("Nie można utworzyć folderu "+itemFolders);
        migrateOldCatalog();
        reloadCache();
    }

    public Map<String,ItemStack> all(){
        Map<String,ItemStack> result=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        cache.forEach((id,item)->result.put(id,item.clone()));
        return result;
    }

    public ItemStack get(String id){ItemStack item=cache.get(id);return item==null?null:item.clone();}

    public Set<String> ids(){return Collections.unmodifiableSet(new TreeSet<>(cache.keySet()));}

    private void reloadCache(){
        cache.clear();
        File[] folders=itemFolders.listFiles(File::isDirectory);if(folders==null)return;
        for(File folder:folders){
            File file=new File(folder,"config.yml"),oldFile=new File(folder,"item.yml");
            if(!file.exists()&&oldFile.isFile())try{Files.copy(oldFile.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);}
            catch(IOException ex){plugin.getLogger().warning("Nie udało się przenieść konfiguracji itemu "+folder.getName()+": "+ex.getMessage());}
            if(!file.isFile())continue;
            Object stored=YamlConfiguration.loadConfiguration(file).get("item");ItemStack item=deserialize(stored);
            if(item!=null&&!item.getType().isAir())cache.put(folder.getName(),item);
        }
    }

    public boolean add(String id,ItemStack item){
        if(id==null||!id.matches("[a-z0-9_-]{1,32}")||id.startsWith("default_"))return false;
        File folder=new File(itemFolders,id);if(folder.exists()||!folder.mkdirs())return false;
        YamlConfiguration data=new YamlConfiguration();data.set("item",serialize(item));
        try{data.save(new File(folder,"config.yml"));cache.put(id,item.clone());return true;}catch(IOException ex){plugin.getLogger().severe("Nie można zapisać itemu "+id+": "+ex.getMessage());folder.delete();return false;}
    }

    public boolean set(String id,ItemStack item){
        if(id==null||!id.matches("[a-z0-9_-]{1,32}")||id.startsWith("default_")||item==null||item.getType().isAir())return false;
        File folder=new File(itemFolders,id);
        if(!folder.exists()&&!folder.mkdirs())return false;
        if(!folder.isDirectory())return false;
        YamlConfiguration data=new YamlConfiguration();data.set("item",serialize(item));
        try{data.save(new File(folder,"config.yml"));cache.put(id,item.clone());return true;}
        catch(IOException ex){plugin.getLogger().severe("Nie można zapisać itemu "+id+": "+ex.getMessage());return false;}
    }

    public boolean remove(String id){
        if(id==null||!id.matches("[a-z0-9_-]{1,32}")||id.startsWith("default_"))return false;
        File folder=new File(itemFolders,id);if(!folder.isDirectory())return false;
        File[] files=folder.listFiles();if(files!=null)for(File file:files)if(!file.delete())return false;
        boolean deleted=folder.delete();if(deleted)cache.remove(id);return deleted;
    }

    private void migrateOldCatalog(){
        File oldFile=new File(plugin.getDataFolder(),"items.yml");if(!oldFile.isFile())return;
        ConfigurationSection old=YamlConfiguration.loadConfiguration(oldFile).getConfigurationSection("items");if(old==null)return;
        int migrated=0;
        YamlConfiguration legacy=YamlConfiguration.loadConfiguration(oldFile);
        for(String id:old.getKeys(false)){
            ItemStack item=deserialize(legacy.get("items."+id));
            if(item!=null&&add(id.toLowerCase(Locale.ROOT),item))migrated++;
        }
        if(migrated>0)plugin.getLogger().info("Przeniesiono "+migrated+" własnych itemów do osobnych folderów items/custom/<id>/.");
    }

    private Map<String,Object> serialize(ItemStack item){
        Map<String,Object> serialized=new LinkedHashMap<>(item.serialize());
        serialized.put("==","org.bukkit.inventory.ItemStack");
        return serialized;
    }

    @SuppressWarnings("unchecked")
    private ItemStack deserialize(Object stored){
        if(stored instanceof ItemStack item)return item.clone();
        if(stored instanceof Map<?,?> map){
            Map<String,Object> serialized=new LinkedHashMap<>();
            map.forEach((key,value)->serialized.put(String.valueOf(key),value));
            serialized.put("==","org.bukkit.inventory.ItemStack");
            try{return ItemStack.deserialize(serialized);}catch(IllegalArgumentException ex){plugin.getLogger().warning("Pominięto nieprawidłowy item z katalogu: "+ex.getMessage());}
        }
        return null;
    }
}
