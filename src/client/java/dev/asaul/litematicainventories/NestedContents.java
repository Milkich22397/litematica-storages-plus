package dev.asaul.litematicainventories;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Read-only, slot-based comparison of item container components. Never opens or moves items. */
public final class NestedContents {
    public record Difference(String slot,ItemStack expected,ItemStack actual){}
    public record Result(List<Difference> differences){
        public Result{differences=List.copyOf(differences);}
        public List<Component> tooltip(boolean expanded){return tooltip(expanded,0);}
        public List<Component> tooltip(boolean expanded,int page){
            if(differences.isEmpty())return List.of();var lines=new ArrayList<Component>();
            lines.add(Component.translatable("storages.nested.summary",differences.size()));
            if(!expanded){lines.add(Component.translatable("storages.nested.shift"));return lines;}
            int start=Math.clamp(page,0,(differences.size()-1)/8)*8,end=Math.min(start+8,differences.size());
            for(int n=start;n<end;n++){var d=differences.get(n);String expected=d.expected().isEmpty()?"\u2014":d.expected().getHoverName().getString()+" \u00d7"+d.expected().getCount();String actual=d.actual().isEmpty()?"\u2014":d.actual().getHoverName().getString()+" \u00d7"+d.actual().getCount();boolean components=!d.expected().isEmpty()&&!d.actual().isEmpty()&&d.expected().getCount()==d.actual().getCount()&&d.expected().getHoverName().getString().equals(d.actual().getHoverName().getString());lines.add(Component.translatable(components?"storages.nested.components":"storages.nested.slot",d.slot(),expected,actual));}
            if(differences.size()>8)lines.add(Component.translatable("storages.nested.page",start/8+1,(differences.size()+7)/8));return lines;
        }
    }
    public static Result compare(ItemStack expected,ItemStack actual){
        var result=new ArrayList<Difference>();if(!actual.isEmpty()&&expected.getItem()!=actual.getItem())return new Result(result);
        walk(expected,actual,"",0,result);return new Result(result);
    }
    private static void walk(ItemStack expected,ItemStack actual,String path,int depth,List<Difference> result){
        var e=expected.getOrDefault(DataComponents.CONTAINER,ItemContainerContents.EMPTY);var a=actual.getOrDefault(DataComponents.CONTAINER,ItemContainerContents.EMPTY);
        var es=e.itemCopies().toList();var as=a.itemCopies().toList();int slots=Math.min(256,Math.max(es.size(),as.size()));
        for(int n=0;n<slots&&result.size()<256;n++){
            ItemStack want=n<es.size()?es.get(n):ItemStack.EMPTY,have=n<as.size()?as.get(n):ItemStack.EMPTY;if(ItemStack.matches(want,have))continue;
            String slot=path+(n+1);
            if(depth<3&&!want.isEmpty()&&!have.isEmpty()&&want.getItem()==have.getItem()&&want.getCount()==have.getCount()&&(!want.getOrDefault(DataComponents.CONTAINER,ItemContainerContents.EMPTY).equals(have.getOrDefault(DataComponents.CONTAINER,ItemContainerContents.EMPTY))))walk(want,have,slot+" \u203a ",depth+1,result);
            else result.add(new Difference(slot,want.copy(),have.copy()));
        }
    }
    private NestedContents(){}
}
