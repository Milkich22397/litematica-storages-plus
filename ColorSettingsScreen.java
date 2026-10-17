package dev.asaul.litematicainventories;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import java.util.function.IntConsumer;
public final class ColorSettingsScreen extends Screen {
 private final Screen parent;
 public ColorSettingsScreen(Screen parent){super(Component.translatable("storage.colors"));this.parent=parent;}
 private void color(int y,String label,int value,IntConsumer setter){
  int x=width/2-130;addRenderableWidget(new StringWidget(x,y,150,20,Component.translatable(label),font));
  EditBox box=new EditBox(font,x+160,y,100,20,Component.literal("AARRGGBB"));box.setMaxLength(8);box.setValue(String.format("%08X",value));box.setResponder(v->{try{if(v.length()==8)setter.accept((int)Long.parseLong(v,16));}catch(NumberFormatException ignored){}});addRenderableWidget(box);
 }
 @Override protected void init(){int y=Math.max(35,height/2-90);var c=StorageSettings.INSTANCE;
  color(y,"storage.diff.missing",c.missingColor,v->c.missingColor=v);color(y+24,"storage.diff.extra",c.extraColor,v->c.extraColor=v);color(y+48,"storage.color.count",c.countColor,v->c.countColor=v);color(y+72,"storage.diff.wrong",c.wrongColor,v->c.wrongColor=v);color(y+96,"storage.diff.unknown",c.unknownColor,v->c.unknownColor=v);
  addRenderableWidget(Button.builder(Component.translatable("litematicainventories.done"),b->onClose()).bounds(width/2-60,y+126,120,20).build());
 }
 @Override public void onClose(){StorageSettings.save();minecraft.setScreenAndShow(parent);}
 @Override public boolean isPauseScreen(){return false;}
}