package dev.asaul.litematicainventories;

import java.lang.invoke.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Minecraft 26.1 stores the screen directly; 26.2+ exposes it through Gui. */
public final class ClientScreenAccess {
    private static final MethodHandle GET = findGetter();
    private static MethodHandle findGetter(){
        var lookup=MethodHandles.publicLookup();
        try{
            try{return lookup.unreflectGetter(Minecraft.class.getField("screen")).asType(MethodType.methodType(Screen.class,Minecraft.class));}
            catch(NoSuchFieldException oldFieldMissing){
                var gui=Minecraft.class.getField("gui");
                return MethodHandles.filterReturnValue(lookup.unreflectGetter(gui),lookup.unreflect(gui.getType().getMethod("screen"))).asType(MethodType.methodType(Screen.class,Minecraft.class));
            }
        }catch(ReflectiveOperationException e){throw new ExceptionInInitializerError(e);}
    }
    public static Screen get(Minecraft client){try{return (Screen)GET.invokeExact(client);}catch(Throwable e){throw new IllegalStateException("Cannot read current Minecraft screen",e);}}
    private ClientScreenAccess(){}
}
