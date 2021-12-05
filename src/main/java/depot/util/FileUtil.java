package depot.util;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileUtil {

    public static String getSizeString(double size) {
        return getSizeString(size, 1);
    }

    public static String getSizeString(double size, int precision) {
        int unitSize = 1 << 10;
        String unit = "B";
        if (size > unitSize) {
            size /= unitSize;
            unit = "KB";
            if (size > unitSize) {
                size /= unitSize;
                unit = "MB";
                if (size > unitSize) {
                    size /= unitSize;
                    unit = "GB";
                    if (size > unitSize) {
                        size /= unitSize;
                        unit = "TB";
                    }
                }
            }
        }
        return String.format(String.format("%%.%df %%s", precision), size, unit);
    }

    public static long getUsedSize(File file) {
        try {
            return Files.walk(file.toPath())
                    .map(Path::toFile)
                    .filter(File::isFile)
                    .mapToLong(File::length)
                    .sum();
        } catch (Exception ignored) {
        }
        return 0L;
    }
}
