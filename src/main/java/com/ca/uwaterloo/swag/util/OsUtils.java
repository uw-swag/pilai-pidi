package com.ca.uwaterloo.swag.util;

public final class OsUtils {
    private static String os = null;

    public static String getOsName() {
        if (os == null) {
            os = System.getProperty("os.name").toLowerCase();
        }
        return os;
    }

    public static boolean isWindows() {
        return getOsName().contains("win");
    }

    @SuppressWarnings("unused")
    public static boolean isMac() {
        return getOsName().contains("mac");
    }

    public static boolean isLinux() {
        return getOsName().contains("nix") || getOsName().contains("nux");
    }
}
