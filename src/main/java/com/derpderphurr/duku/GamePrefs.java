package com.derpderphurr.duku;

import java.util.prefs.Preferences;

//Cross-platform key/value persistence for window bounds and where the player left off,
//backed by java.util.prefs (no extra dependency, no file management).
public class GamePrefs {
    private static final Preferences prefs = Preferences.userNodeForPackage(GamePrefs.class);

    private static final String KEY_WINDOW_X = "windowX";
    private static final String KEY_WINDOW_Y = "windowY";
    private static final String KEY_WINDOW_WIDTH = "windowWidth";
    private static final String KEY_WINDOW_HEIGHT = "windowHeight";
    private static final String KEY_LAST_SEED = "lastSeed";

    public static void saveWindowBounds(double x, double y, double width, double height) {
        prefs.putDouble(KEY_WINDOW_X, x);
        prefs.putDouble(KEY_WINDOW_Y, y);
        prefs.putDouble(KEY_WINDOW_WIDTH, width);
        prefs.putDouble(KEY_WINDOW_HEIGHT, height);
    }

    public static double getWindowX(double defaultValue) { return prefs.getDouble(KEY_WINDOW_X, defaultValue); }
    public static double getWindowY(double defaultValue) { return prefs.getDouble(KEY_WINDOW_Y, defaultValue); }
    public static double getWindowWidth(double defaultValue) { return prefs.getDouble(KEY_WINDOW_WIDTH, defaultValue); }
    public static double getWindowHeight(double defaultValue) { return prefs.getDouble(KEY_WINDOW_HEIGHT, defaultValue); }

    public static void saveLastSeed(long seed) {
        prefs.putLong(KEY_LAST_SEED, seed);
    }

    public static long getLastSeed(long defaultValue) { return prefs.getLong(KEY_LAST_SEED, defaultValue); }

    public static void resetLevel() {
        prefs.remove(KEY_LAST_SEED);
    }
}