package org.estech.common.util;


import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

public class ImageUtils {

    public static BufferedImage readImage(Path path) throws IOException {
        return ImageIO.read(path.toFile());
    }

    public static BufferedImage readImage(InputStream in) throws IOException {
        return ImageIO.read(in);
    }
}
