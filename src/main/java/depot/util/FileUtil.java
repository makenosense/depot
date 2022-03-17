package depot.util;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Sets;
import depot.MainApp;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class FileUtil {
    private static final String IGNORE_FILE_PATH = "ignore";

    private static final LoadingCache<String, Set<String>> IGNORE_ITEMS_CACHE = CacheBuilder.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(1))
            .build(new CacheLoader<>() {
                @Override
                public Set<String> load(String s) {
                    try (InputStream inputStream = new URL(Objects.requireNonNull(MainApp.class.getResource(s)).toExternalForm()).openStream()) {
                        return Arrays.stream(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
                                        .split("\\r?\\n"))
                                .map(StringUtils::trim)
                                .filter(StringUtils::isNotBlank)
                                .collect(Collectors.toSet());
                    } catch (Exception ignored) {
                    }
                    return Sets.newHashSet();
                }
            });

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

    @SneakyThrows
    public static boolean shouldIgnore(File file) {
        Set<String> ignoreItems = IGNORE_ITEMS_CACHE.get(IGNORE_FILE_PATH);
        if (file.isFile()) {
            return ignoreItems.stream()
                    .filter(item -> !item.endsWith("/"))
                    .map(item -> StringUtils.strip(item, "/"))
                    .collect(Collectors.toSet())
                    .contains(file.getName());
        } else {
            return ignoreItems.stream()
                    .filter(item -> item.endsWith("/"))
                    .map(item -> StringUtils.strip(item, "/"))
                    .collect(Collectors.toSet())
                    .contains(file.getName());
        }
    }
}
