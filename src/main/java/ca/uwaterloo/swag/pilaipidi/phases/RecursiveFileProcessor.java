package ca.uwaterloo.swag.pilaipidi.phases;

import ai.serenade.treesitter.Languages;
import ai.serenade.treesitter.Node;
import ai.serenade.treesitter.Parser;
import ai.serenade.treesitter.Tree;
import ca.uwaterloo.swag.pilaipidi.util.ArugumentOptions;
import ca.uwaterloo.swag.pilaipidi.util.OsUtils;

import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RecursiveFileProcessor {

    private final ArugumentOptions arugumentOptions;

    public RecursiveFileProcessor(ArugumentOptions arugumentOptions) {
        this.arugumentOptions = arugumentOptions;
    }

    public static void processFiles(File directory, FileProcessor javaProcessor, FileProcessor cProcessor) {
        File[] files = directory.listFiles();

        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    processFiles(file, javaProcessor, cProcessor);
                } else if (file.getName().endsWith(".java")) {
                    try {
                        String content = readContentFromFile(file);
                        javaProcessor.processFileContent(file.getName(), content);
                    } catch (IOException e) {
                        System.err.println("Error reading file " + file.getAbsolutePath() + ": " + e.getMessage());
                    }
                } else if (file.getName().endsWith(".c") || file.getName().endsWith(".cpp") ) {
                    try {
                        String content = readContentFromFile(file);
                        cProcessor.processFileContent(file.getName(), content);
                    } catch (IOException e) {
                        System.err.println("Error reading file " + file.getAbsolutePath() + ": " + e.getMessage());
                    }
                }

            }
        }
    }

    private static String readContentFromFile(File file) throws IOException {
        StringBuilder content = new StringBuilder();
        BufferedReader reader = new BufferedReader(new FileReader(file));
        String line;

        while ((line = reader.readLine()) != null) {
            content.append(line);
            content.append(System.lineSeparator());
        }

        reader.close();
        return content.toString();
    }

    public Map<String, Tree> generateTSG() {
        File projectLocation = null;
        String TS = null;
        List<String> argsList = arugumentOptions.argsList;
        Map<String, String> optsList = arugumentOptions.optsList;
        Map<String, Tree> file_trees = new HashMap<>();

        if (argsList.size() > 0) {
            projectLocation = new File(arugumentOptions.projectLocation);
            if (optsList.containsKey("-ts")) {
                TS = optsList.get("-ts");
            } else {
                if (OsUtils.isWindows()) {
                    System.exit(1);
                } else if (OsUtils.isLinux()) {
                    System.exit(1);
                } else if (OsUtils.isMac()) {
                    TS = "mac/libjava-tree-sitter.dylib";
                } else {
                    System.err.println("Please specify location of compiled Tree Sitter library for current OS");
                    System.exit(1);
                }
            }
            System.load(TS);
        } else {
            System.err.println("Please specify location of project to be analysed");
            System.exit(1);
        }

        FileProcessor javaProcessor = (name, content) -> {
            try (Parser parser = new Parser()) {
                parser.setLanguage(Languages.java());
                try (Tree tree = parser.parseString(content)) {
                    file_trees.put(name, tree);
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }
            }

        };
        FileProcessor cProcessor = (name, content) -> {
            try (Parser parser = new Parser()) {
                parser.setLanguage(Languages.cpp());
                try (Tree tree = parser.parseString(content)) {
                    file_trees.put(name, tree);
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        processFiles(projectLocation, javaProcessor, cProcessor);
        return file_trees;
    }
    public interface FileProcessor {
        void processFileContent(String name, String content);
    }
}
