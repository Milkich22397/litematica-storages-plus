package dev.asaul.litematicainventories;

import dev.asaul.litematicainventories.mixin.*;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Metadata lives in the same atomic ledger generation as the observations. */
public final class StorageGroups {
    public static final String ALL="*";
    private static final Map<String,LinkedHashMap<String,String>> groups=new LinkedHashMap<>();
    private static final Map<String,String> selections=new LinkedHashMap<>(),bindings=new LinkedHashMap<>();
    private static final Map<MaterialListBase,String> schematicKeys=new WeakHashMap<>();
    private record Choice(String server,long revision,String selection){}
    private static final Map<MaterialListBase,Choice> choicesCache=new WeakHashMap<>();
    private static long revision;
    public static long revision(){TrackedStorageStore.load();return revision;}
    public static String server(){return SessionContext.serverId(Minecraft.getInstance());}
    public static Map<String,String> groups(String server){TrackedStorageStore.load();var map=groups.get(server);return map==null?Map.of():Collections.unmodifiableMap(map);}
    public static String name(String server,String id){return id.equals(ALL)?Component.translatable("storages.groups.all").getString():id.isEmpty()?Component.translatable("storages.groups.none").getString():groups(server).getOrDefault(id,Component.translatable("storages.groups.unknown").getString());}
    private static String clean(String name){String s=name.strip();return s.length()>64?s.substring(0,64):s;}
    public static String create(String server,String name){TrackedStorageStore.load();name=clean(name);if(name.isBlank())return null;var map=groups.computeIfAbsent(server,k->new LinkedHashMap<>());for(var existing:map.entrySet())if(existing.getValue().equalsIgnoreCase(name))return existing.getKey();if(map.size()>=256)return null;String id=UUID.randomUUID().toString();map.put(id,name);changed();return id;}
    public static void rename(String server,String id,String name){TrackedStorageStore.load();name=clean(name);if(name.isBlank()||!groups(server).containsKey(id))return;groups.get(server).put(id,name);changed();}
    public static void delete(String server,String id){TrackedStorageStore.load();if(!groups(server).containsKey(id))return;groups.get(server).remove(id);if(id.equals(selections.get(server)))selections.put(server,ALL);bindings.entrySet().removeIf(e->e.getKey().startsWith(server+"\n")&&e.getValue().equals(id));TrackedStorageStore.ungroup(server,id);changed();}
    private static boolean valid(String server,String selection){var map=groups.get(server);return selection.equals(ALL)||selection.isEmpty()||map!=null&&map.containsKey(selection);}
    public static void select(String server,String selection){TrackedStorageStore.load();if(!valid(server,selection))return;selections.put(server,selection);changed();}
    public static String schematicId(MaterialListBase list){
        if(list==null)return null;if(schematicKeys.containsKey(list))return schematicKeys.get(list);
        LitematicaSchematic schematic=list instanceof MaterialListPlacementAccess p?p.storage$placement().getSchematic():list instanceof MaterialListSchematicAccess s?s.storage$schematic():null;
        if(schematic==null||schematic.getFile()==null)return null;
        String id=schematic.getFile().toAbsolutePath().normalize().toString();schematicKeys.put(list,id);return id;
    }
    public static boolean bound(String server,MaterialListBase list){TrackedStorageStore.load();String id=schematicId(list);return id!=null&&bindings.containsKey(server+"\n"+id);}
    public static void bind(String server,MaterialListBase list,String selection){TrackedStorageStore.load();String id=schematicId(list);if(id==null||!valid(server,selection))return;bindings.put(server+"\n"+id,selection);changed();}
    public static void unbind(String server,MaterialListBase list){TrackedStorageStore.load();String id=schematicId(list);if(id!=null&&bindings.remove(server+"\n"+id)!=null)changed();}
    public static String selection(MaterialListBase list){return selection(server(),list);}
    public static String selection(String server,MaterialListBase list){TrackedStorageStore.load();var cached=choicesCache.get(list);if(cached!=null&&cached.revision()==revision&&cached.server().equals(server))return cached.selection();String id=schematicId(list),chosen=id==null?null:bindings.get(server+"\n"+id);if(chosen==null)chosen=selections.getOrDefault(server,ALL);String result=valid(server,chosen)?chosen:ALL;choicesCache.put(list,new Choice(server,revision,result));return result;}
    public static List<String> choices(String server){var result=new ArrayList<String>();result.add(ALL);result.add("");result.addAll(groups(server).keySet());return result;}
    private static void changed(){revision++;TrackedStorageStore.organizationChanged();ResourceAccounting.tick(Minecraft.getInstance());}
    public static CompoundTag encode(){
        var root=new CompoundTag();var gs=new ListTag();groups.forEach((server,map)->map.forEach((id,name)->{var n=new CompoundTag();n.putString("World",server);n.putString("Id",id);n.putString("Name",name);gs.add(n);}));root.put("Groups",gs);
        var ss=new ListTag();selections.forEach((key,value)->{var n=new CompoundTag();n.putString("Key",key);n.putString("Value",value);ss.add(n);});root.put("Selections",ss);
        var bs=new ListTag();bindings.forEach((key,value)->{var n=new CompoundTag();n.putString("Key",key);n.putString("Value",value);bs.add(n);});root.put("Bindings",bs);return root;
    }
    public static void restore(CompoundTag root){
        var gs=new LinkedHashMap<String,LinkedHashMap<String,String>>();var ss=new LinkedHashMap<String,String>();var bs=new LinkedHashMap<String,String>();
        for(var raw:root.getListOrEmpty("Groups"))if(raw instanceof CompoundTag n){String server=n.getStringOr("World",""),id=n.getStringOr("Id",""),name=clean(n.getStringOr("Name",""));if(server.isBlank()||id.isBlank()||id.equals(ALL)||name.isBlank())throw new IllegalArgumentException("Invalid storage group");var map=gs.computeIfAbsent(server,k->new LinkedHashMap<>());map.put(id,name);if(map.size()>256||gs.size()>10000)throw new IllegalArgumentException("Too many storage groups");}
        for(var raw:root.getListOrEmpty("Selections"))if(raw instanceof CompoundTag n)ss.put(n.getStringOr("Key",""),n.getStringOr("Value",ALL));
        for(var raw:root.getListOrEmpty("Bindings"))if(raw instanceof CompoundTag n)bs.put(n.getStringOr("Key",""),n.getStringOr("Value",ALL));
        if(ss.size()>10000||bs.size()>10000)throw new IllegalArgumentException("Too many group selections");groups.clear();groups.putAll(gs);selections.clear();selections.putAll(ss);bindings.clear();bindings.putAll(bs);schematicKeys.clear();choicesCache.clear();revision++;
    }
    private StorageGroups(){}
}
