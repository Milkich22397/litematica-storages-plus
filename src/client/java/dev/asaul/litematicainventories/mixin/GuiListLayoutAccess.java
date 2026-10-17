package dev.asaul.litematicainventories.mixin;
import fi.dy.masa.malilib.gui.GuiListBase;
import fi.dy.masa.malilib.gui.widgets.WidgetListBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(value=GuiListBase.class,remap=false)
public interface GuiListLayoutAccess {
    @Invoker("setListPosition") void storage$position(int x,int y);
    @Invoker("reCreateListWidget") void storage$recreate();
    @Invoker("getListWidget") WidgetListBase<?,?> storage$list();
}
