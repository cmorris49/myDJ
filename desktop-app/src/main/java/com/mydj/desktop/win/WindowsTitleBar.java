package com.mydj.desktop.win;

import com.sun.jna.Native;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.win32.StdCallLibrary;
import javafx.application.Platform;
import javafx.stage.Stage;

public final class WindowsTitleBar {
    private WindowsTitleBar() {}

    private interface Dwmapi extends StdCallLibrary {
        Dwmapi INSTANCE = Native.load("dwmapi", Dwmapi.class);
        int DwmSetWindowAttribute(HWND hwnd, int attr, IntByReference pv, int cb);
    }

    private static final int DWMWA_USE_IMMERSIVE_DARK_MODE_OLD = 19; 
    private static final int DWMWA_USE_IMMERSIVE_DARK_MODE = 20; 

    public static void tryEnableDarkTitleBar(Stage stage) {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("win")) return;

        Platform.runLater(() -> {
            String title = stage.getTitle();
            if (title == null || title.isBlank()) return;

            HWND hwnd = User32.INSTANCE.FindWindow(null, title);
            if (hwnd == null) return;

            IntByReference dark = new IntByReference(1);
            Dwmapi.INSTANCE.DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, dark, 4);
            Dwmapi.INSTANCE.DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE_OLD, dark, 4);
        });
    }
}
