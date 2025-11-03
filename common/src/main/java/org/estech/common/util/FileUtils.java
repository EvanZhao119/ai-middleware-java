package org.estech.common.util;

import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileUtils {

    public static Path extractToTemp(String classpath, String prefix, String suffix) throws IOException {
        ClassPathResource cpr = new ClassPathResource(classpath);
        Path tmp = Files.createTempFile(prefix, suffix);
        try (InputStream in = cpr.getInputStream()) {
            Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        tmp.toFile().deleteOnExit();
        return tmp;
    }
}
