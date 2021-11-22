package ca.uwaterloo.swag.pilaipidi.phases;

import ca.uwaterloo.swag.pilaipidi.util.ArugumentOptions;
import ca.uwaterloo.swag.pilaipidi.Main;
import ca.uwaterloo.swag.pilaipidi.util.OsUtils;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.spi.FileSystemProvider;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.io.IOUtils;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * {@link SrcMLGenerator} generates srcML for the whole project by invoking the relevant OS specfic srcMl executable.
 *
 * @since 0.0.1
 */
public class SrcMLGenerator {

    private static final String JAR = "jar";
    private final ArugumentOptions arugumentOptions;

    public SrcMLGenerator(ArugumentOptions arugumentOptions) {
        this.arugumentOptions = arugumentOptions;
    }

    public Document generateSrcML()
        throws IOException, URISyntaxException, ParserConfigurationException, SAXException {
        String projectLocation = null;
        String srcML = null;
        File file;
        List<String> argsList = arugumentOptions.argsList;
        Map<String, String> optsList = arugumentOptions.optsList;

        URI uri = Objects.requireNonNull(Main.class.getClassLoader().getResource("windows/srcml.exe")).toURI();
        if (JAR.equals(uri.getScheme())) {
            for (FileSystemProvider provider : FileSystemProvider.installedProviders()) {
                if (provider.getScheme().equalsIgnoreCase(JAR)) {
                    try {
                        provider.getFileSystem(uri);
                    } catch (FileSystemNotFoundException e) {
                        // in this case we need to initialize it first:
                        provider.newFileSystem(uri, Collections.emptyMap());
                    }
                }
            }
        }
        if (argsList.size() > 0) {
            projectLocation = arugumentOptions.projectLocation;
            if (optsList.containsKey("-srcml")) {
                srcML = optsList.get("-srcml");
            } else {
                if (OsUtils.isWindows()) {
                    srcML = "windows/srcml.exe";
                } else if (OsUtils.isLinux()) {
                    srcML = "ubuntu/srcml";
                } else if (OsUtils.isMac()) {
                    srcML = "mac/srcml";
                } else {
                    System.err.println("Please specify location of srcML, binary not included for current OS");
                    System.exit(1);
                }
            }
        } else {
            System.err.println("Please specify location of project to be analysed");
            System.exit(1);
        }

        ProcessBuilder processBuilder;
        if (argsList.size() > 1) {
            processBuilder = new ProcessBuilder(srcML, projectLocation, "--position");
        } else {
            InputStream in = SrcMLGenerator.class.getClassLoader().getResourceAsStream(srcML);
            file = File.createTempFile("PREFIX", "SUFFIX");
            boolean execStatus = file.setExecutable(true);
            if (!execStatus) {
                throw new AssertionError();
            }
            file.deleteOnExit();
            try (FileOutputStream out = new FileOutputStream(file)) {
                IOUtils.copy(Objects.requireNonNull(in), out);
            }
            processBuilder = new ProcessBuilder(file.getAbsolutePath(), projectLocation, "--position");
        }
        String result = IOUtils.toString(processBuilder.start().getInputStream(), StandardCharsets.UTF_8);
        System.out.println("Converted to XML, beginning parsing ...");
        DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        return documentBuilder.parse(new InputSource(new StringReader(result)));
    }
}
