package depot.model.repository.sync;

import depot.model.base.BaseTreeNode;

import java.util.HashMap;

public class RepositoryCompareTreeNode extends BaseTreeNode {
    public static final String PROP_SIZE = "SIZE";
    public static final String PROP_MTIME = "MTIME";
    public static final String PROP_CHECKSUM = "CHECKSUM";

    private final HashMap<String, String> sourceProperties = new HashMap<>();
    private final HashMap<String, String> targetProperties = new HashMap<>();

    public RepositoryCompareTreeNode(String id, String parent, String type, String text) {
        this(id, parent, type, text, "");
    }

    public RepositoryCompareTreeNode(String id, String parent, String type, String text, String comment) {
        super(id, parent, type, text, comment, true);
    }

    public void setSourceProperty(String propertyName, String propertyValue) {
        sourceProperties.put(propertyName, propertyValue);
    }

    public void setTargetProperty(String propertyName, String propertyValue) {
        targetProperties.put(propertyName, propertyValue);
    }

    public String getSourceProperty(String propertyName) {
        return sourceProperties.get(propertyName);
    }

    public String getTargetProperty(String propertyName) {
        return targetProperties.get(propertyName);
    }
}
