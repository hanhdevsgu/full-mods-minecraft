-injars 'sell kho_unobf.jar'
-outjars 'sell kho.jar'

-libraryjars 'C:\Program Files\Java\jre-1.8\lib\rt.jar'
-libraryjars 'C:\Program Files\Java\jre-1.8\lib\jce.jar'
-libraryjars 'C:\Users\Lenovo\.gradle\caches\forge_gradle\mcp_repo\net\minecraft\joined\1.12.2\joined-1.12.2-srg.jar'
-libraryjars 'C:\Users\Lenovo\.gradle\caches\minecraft\net\minecraftforge\forge\1.12.2-14.23.5.2847\snapshot\20171003\forgeBin-1.12.2-14.23.5.2847.jar'

# Anti-Reverse Engineering & Obfuscation Settings
-dontshrink
-dontoptimize
-dontwarn **
-ignorewarnings

-repackageclasses 'com.zorotool.macrorecorder'
-allowaccessmodification
-overloadaggressively
-useuniqueclassmembernames
-renamesourcefileattribute ''

# Keep only necessary attributes for Forge runtime, strip debugging line numbers & source names
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep Mod Entry Point
-keep @net.minecraftforge.fml.common.Mod class com.zorotool.macrorecorder.MacroRecorderMod {
    public static final java.lang.String MODID;
    public static final java.lang.String VERSION;
    public <init>();
    @net.minecraftforge.fml.common.Mod$EventHandler *;
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent *;
}

# Keep event handlers on any classes
-keepclassmembers class * {
    @net.minecraftforge.fml.common.Mod$EventHandler *;
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent *;
}

