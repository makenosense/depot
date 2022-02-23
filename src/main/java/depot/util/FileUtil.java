package depot.util;

import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

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

    public interface ChecksumProgressHandler {
        void handle(long processedSize, long totalSize);
    }

    public static String getChecksum(File file, ChecksumProgressHandler handler) {
        long processedSize = 0, totalSize = file.length();
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[64 * 1024];
            while (true) {
                int readLength = fileInputStream.read(buffer);
                if (readLength <= 0) {
                    break;
                }
                digest.update(buffer, 0, readLength);
                processedSize += readLength;
                if (handler != null) {
                    handler.handle(processedSize, totalSize);
                }
            }
            return SVNFileUtil.toHexDigest(digest);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
