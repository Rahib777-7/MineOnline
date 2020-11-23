package gg.codie.mineonline.client;

import gg.codie.minecraft.client.EMinecraftGUIScale;
import gg.codie.minecraft.client.EMinecraftMainHand;
import gg.codie.minecraft.client.Options;
import gg.codie.mineonline.LauncherFiles;
import gg.codie.mineonline.MinecraftVersion;
import gg.codie.mineonline.Settings;
import gg.codie.mineonline.discord.DiscordRPCHandler;
import gg.codie.mineonline.gui.GUIScale;
import gg.codie.mineonline.gui.rendering.DisplayManager;
import gg.codie.mineonline.gui.rendering.FontRenderer;
import gg.codie.mineonline.gui.rendering.Loader;
import gg.codie.mineonline.gui.screens.AbstractGuiScreen;
import gg.codie.mineonline.patches.ClassPatch;
import gg.codie.mineonline.patches.HashMapPatch;
import gg.codie.mineonline.patches.StringPatch;
import gg.codie.mineonline.patches.lwjgl.LWJGLGL11GLEnableAdvice;
import gg.codie.mineonline.patches.lwjgl.LWJGLGL11GLOrthoAdvice;
import gg.codie.mineonline.patches.lwjgl.LWJGLGL11Patch;
import gg.codie.mineonline.patches.lwjgl.LWJGLGLUPerspectiveAdvice;
import gg.codie.mineonline.patches.minecraft.*;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

import java.awt.*;
import java.io.IOException;

public class LegacyGameManager {

    private static LegacyGameManager singleton;
    public static AbstractGuiScreen guiScreen;

    private MinecraftVersion version;
    private IMinecraftAppletWrapper appletWrapper;

    private Options minecraftOptions = null;

    private LegacyGameManager(MinecraftVersion version, IMinecraftAppletWrapper appletWrapper) {
        this.version = version;
        this.appletWrapper = appletWrapper;
        try {
            minecraftOptions = new Options(LauncherFiles.MINEONLINE_OPTIONS_PATH, version.optionsVersion);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static void createGameManager(MinecraftVersion version, IMinecraftAppletWrapper appletWrapper) {
        if (singleton != null) {
            System.err.println("Legacy game manager already exists!");
        } else {
            singleton = new LegacyGameManager(version, appletWrapper);
        }

        preparePatches();
    }

    private static void preparePatches() {
        System.out.println("Preparing Patches!");

        MinecraftVersion version = getVersion();

        // Allow the MineOnline menu to freeze game input.
        InputPatch.init();

        HashMapPatch.init();
        ClassPatch.init();
        LWJGLGL11Patch.init();

        if (version != null) {
            if (version.ingameVersionString != null) {
                StringPatch.hideVersionNames(version.ingameVersionString);
                StringPatch.enable = Settings.singleton.getHideVersionString();
            }

            if (version.useTexturepackPatch) {
                ClassPatch.texturePack = Settings.singleton.getTexturePack();
            }

            // Screenshotting was officially added at the same time as F1 to hide the HUD / viewmodel.
            if (version.enableScreenshotPatch && version.viewModelFunction == null) {
                LWJGLGL11GLEnableAdvice.enableClassicViewmodelPatch = true;
            }

            // Fixes various input issues with classic - infdev versions.
            InputPatch.enableClassicFixes = version.enableCursorPatch;

            if (getVersion().scaledResolutionClass != null) {
                ScaledResolutionConstructorPatch.useGUIScale(getVersion().scaledResolutionClass);
                LWJGLGL11GLOrthoAdvice.enable = true;
            } else if (getVersion().guiClass != null && getVersion().guiScreenClass != null) {
                GuiScreenPatch.useGUIScale(getVersion().guiScreenClass);
                LWJGLGL11GLOrthoAdvice.enable = true;
            }
        }
    }

    private static LegacyGameManager getSingleton() {
        return singleton;
    }

    public static MinecraftVersion getVersion() {
        return getSingleton().version;
    }

    public static IMinecraftAppletWrapper getAppletWrapper() {
        return getSingleton().appletWrapper;
    }

    public static void closeGame() {
        DiscordRPCHandler.stop();
        getAppletWrapper().closeApplet();
    }

    public static void setTexturePack(String texturePack) {
        Settings.singleton.setTexturePack(texturePack);
        Settings.singleton.saveSettings();
        ClassPatch.texturePack = texturePack;
        Loader.reloadMinecraftTextures();
    }

    public static void setFOV(int fov) {
        Settings.singleton.setFOV(fov);
        Settings.singleton.saveSettings();
        LWJGLGLUPerspectiveAdvice.customFOV = fov;
    }

    public static void setHideVersionString(boolean hideVersionString) {
        Settings.singleton.setHideVersionString(hideVersionString);
        Settings.singleton.saveSettings();
        StringPatch.enable = hideVersionString;
    }

    public static void setGUIScale(EMinecraftGUIScale guiScale) {
        Settings.singleton.setGUIScale(guiScale);
        Settings.singleton.saveSettings();
        // TODO: Put these in one place.
        GuiScreenOpenAdvice.guiScale = guiScale.getIntValue();
        LWJGLGL11GLOrthoAdvice.guiScale = guiScale.getIntValue();
        ScaledResolutionConstructorAdvice.guiScale = guiScale.getIntValue();
        getGuiScreen().resize(GUIScale.lastScaledWidth(), GUIScale.lastScaledHeight());
    }

    public static void setMainHand(EMinecraftMainHand mainHand) {
        Settings.singleton.setMainHand(mainHand);
        Settings.singleton.saveSettings();
        FOVViewmodelAdvice.leftHanded = mainHand == EMinecraftMainHand.LEFT;
    }

    public static boolean isInGame() {
        return getSingleton() != null;
    }

    public static int getWidth() {
        return getAppletWrapper().getWidth();
    }

    public static int getHeight() {
        return getAppletWrapper().getHeight();
    }

    public static boolean mineonlineMenuOpen() {
        return getGuiScreen() != null;
    }

    public static void setGUIScreen(AbstractGuiScreen guiScreen) {
        if (!getVersion().useMineOnlineMenu)
            return;

        if (LegacyGameManager.guiScreen == null) {
            InputPatch.isFocused = false;

            Canvas mcCanvas = Display.getParent();
            Mouse.setCursorPosition((mcCanvas.getWidth() / 2) + DisplayManager.getFrame().getInsets().left, mcCanvas.getHeight() / 2);
            Mouse.setGrabbed(false);
        } else {
            LegacyGameManager.getGuiScreen().onGuiClosed();
        }

        if (guiScreen == null) {
            if (Display.isActive()) {
                InputPatch.isFocused = true;
                Mouse.setGrabbed(true);
            }
        }

        LegacyGameManager.guiScreen = guiScreen;
    }

    public static AbstractGuiScreen getGuiScreen() {
        return guiScreen;
    }
}