package org.estech.api.jni;


import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class NativeLibraryLoader {
    public static void load(String baseName) {
        String os = System.getProperty("os.name").toLowerCase();
        String libName = "lib" + baseName + (os.contains("mac") ? ".dylib"
                : os.contains("win") ? ".dll"
                : ".so");

        String resourcePath = "/native/" + libName;

        try (InputStream in = NativeLibraryLoader.class.getResourceAsStream(resourcePath)) {
            if (in == null) throw new RuntimeException("Native library not found: " + resourcePath);
            Path temp = Files.createTempFile(baseName + "-", libName.substring(libName.lastIndexOf('.')));
            temp.toFile().deleteOnExit();
            Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
            System.load(temp.toAbsolutePath().toString());
        } catch (IOException e) {
            throw new RuntimeException("Failed to load native library: " + resourcePath, e);
        }
    }
}
