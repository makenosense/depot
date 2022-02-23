package depot.model.repository.path;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RepositoryPathNode {
    private static final String SEARCH_PATH_PREFIX = "/@@";
    private static final Pattern SEARCH_PATH_PATTERN = Pattern.compile("^" + SEARCH_PATH_PREFIX + "(.+)$");

    private final Path path;

    public RepositoryPathNode() {
        this(Paths.get("/"));
    }

    public RepositoryPathNode(String pathString) {
        this(Paths.get("/").resolve(pathString));
    }

    public RepositoryPathNode(Path path) {
        this.path = path.normalize();
    }

    public Path getPath() {
        return path;
    }

    public static String toUniString(Path path) {
        return path.normalize().toString().replaceAll("\\\\", "/");
    }

    @Override
    public String toString() {
        return toUniString(path);
    }

    public boolean isSearch() {
        return SEARCH_PATH_PATTERN.matcher(toString()).matches();
    }

    public String getSearchPattern() {
        Matcher searchPathMatcher = SEARCH_PATH_PATTERN.matcher(toString());
        if (searchPathMatcher.matches()) {
            try {
                return URLDecoder.decode(searchPathMatcher.group(1), StandardCharsets.UTF_8.toString());
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public static RepositoryPathNode getSearchPathNode(String searchPattern) {
        try {
            return new RepositoryPathNode(Paths.get(SEARCH_PATH_PREFIX
                    + URLEncoder.encode(searchPattern, StandardCharsets.UTF_8.toString())));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return null;
    }

    public String getName() {
        if (isSearch()) {
            return "搜索“" + getSearchPattern() + "”";
        }
        return path.getNameCount() > 0 ? path.getFileName().toString() : "我的仓库";
    }

    public RepositoryPathNode getParent() {
        Path parentPath = path.getParent();
        return parentPath != null ?
                new RepositoryPathNode(parentPath) : null;
    }

    public RepositoryPathNode resolve(String pathString) {
        return new RepositoryPathNode(path.resolve(pathString));
    }

    public Path relativize(Path other) {
        return path.relativize(other);
    }

    public boolean startsWith(RepositoryPathNode other) {
        return path.startsWith(other.path);
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof RepositoryPathNode
                && obj.toString().equals(toString());
    }
}
