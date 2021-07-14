package com.noble.util;

public final class OsUtils {
    private static String OS = null;

    public static String getOsName() {
        if (OS == null) {
            OS = System.getProperty("os.name").toLowerCase();
        }
        return OS;
    }

    public static boolean isWindows() {
        return getOsName().contains("win");
    }

    public static boolean isMac() {
        return getOsName().contains("mac");
    }

    public static boolean isLinux() {
        return getOsName().contains("nix") || getOsName().contains("nux");
    }
}
