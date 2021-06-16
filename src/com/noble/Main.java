package com.noble;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;

public class Main {

    static {
        URL res = Main.class.getClassLoader().getResource("libsrcml.so");
        try {
            File file = Paths.get(res.toURI()).toFile();
            System.load(file.getAbsolutePath());
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }
    public static native String srcml();

    public static void main(String[] args) {
        System.out.println(srcml());
    }

}
