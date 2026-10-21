package dev.asaul.litematicainventories;

import net.minecraft.client.MouseHandler;

/** Only the test driver needs GLFW's private cursor callback on early 26.1 clients. */
public final class PortSmokeAccess {
    public static void move(MouseHandler mouse,long window,double x,double y){
        try{var callback=MouseHandler.class.getDeclaredMethod("onMove",long.class,double.class,double.class);callback.setAccessible(true);callback.invoke(mouse,window,x,y);}
        catch(ReflectiveOperationException e){throw new IllegalStateException("Smoke cursor movement failed",e);}
    }
}
