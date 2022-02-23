package depot.model.repository.sync;

import com.google.common.base.Preconditions;
import depot.model.repository.config.ComparableRepositoryConfig;
import depot.model.repository.path.RepositoryPathNode;

import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class RepositoryCompareResult {

    private final HashMap<RepositoryPathNode, RepositoryCompareEntry> compareEntries;
    private LinkedList<RepositoryCompareTreeNode> compareTreeNodes;

    public interface CollectListener {
        void handle(RepositoryPathNode pathNode) throws Exception;
    }

    public RepositoryCompareResult(ComparableRepositoryConfig sourceConfig, ComparableRepositoryConfig targetConfig,
                                   CollectListener collectListener) throws Exception {
        this.compareEntries = new HashMap<>();
        sourceConfig.collectEntryInfo(new RepositoryPathNode(), collectListener).forEach((pathNode, value) -> {
            RepositoryCompareEntry compareEntry = compareEntries.computeIfAbsent(
                    pathNode, k -> new RepositoryCompareEntry(pathNode.getParent(), pathNode.getName()));
            compareEntry.setSourceEntryInfo(value);
        });
        targetConfig.collectEntryInfo(new RepositoryPathNode(), collectListener).forEach((pathNode, value) -> {
            RepositoryCompareEntry compareEntry = compareEntries.computeIfAbsent(
                    pathNode, k -> new RepositoryCompareEntry(pathNode.getParent(), pathNode.getName()));
            compareEntry.setTargetEntryInfo(value);
        });
    }

    public Map<String, RepositoryCompareEntry> getChangedEntries() {
        return compareEntries.entrySet().stream()
                .filter(entry -> !Objects.equals(entry.getValue().getSourceEntryInfo(), entry.getValue().getTargetEntryInfo())
                        && !Objects.equals(entry.getValue().getTargetEntryInfo(), entry.getValue().getSourceEntryInfo()))
                .collect(Collectors.toMap(entry -> entry.getKey().toString(), Map.Entry::getValue));
    }

    public synchronized Object[] getCompareTreeNodeChildrenArray(String parentId) {
        return (compareTreeNodes != null ? compareTreeNodes : buildCompareTreeNodes()).parallelStream()
                .filter(node -> node.parent.equals(parentId))
                .toArray();
    }

    private LinkedList<RepositoryCompareTreeNode> buildCompareTreeNodes() {
        Map<String, RepositoryCompareEntry> changedEntries = getChangedEntries();
        HashMap<String, RepositoryCompareTreeNode> treeNodes = new HashMap<>();
        changedEntries.keySet().stream().sorted().forEach(key -> {
            RepositoryCompareEntry changedEntry = changedEntries.get(key);
            RepositoryPathNode parentPathNode = changedEntry.getParentPathNode();
            String nodeParent = parentPathNode.getParent() != null ? parentPathNode + "/" : "#";
            String nodeName = changedEntry.getName();
            RepositoryCompareEntry.EntryInfo sourceInfo = changedEntry.getSourceEntryInfo();
            RepositoryCompareEntry.EntryInfo targetInfo = changedEntry.getTargetEntryInfo();
            String nodeType;
            String oldNodeType = null;
            if (sourceInfo != null && targetInfo != null) {
                if (sourceInfo.getKind() != targetInfo.getKind()) {
                    nodeType = targetInfo.getKind() + "_d";
                    oldNodeType = sourceInfo.getKind() + "_a";
                } else {
                    nodeType = targetInfo.getKind() + "_m";
                }
            } else if (sourceInfo == null) {
                nodeType = targetInfo.getKind() + "_d";
            } else {
                nodeType = sourceInfo.getKind() + "_a";
            }

            String nodePath = nodeType.startsWith("dir") ? key + "/" : key;
            RepositoryCompareTreeNode node = new RepositoryCompareTreeNode(
                    nodePath, nodeParent, nodeType, nodeName);
            if (nodeType.endsWith("_a") || nodeType.endsWith("_m")) {
                Preconditions.checkNotNull(sourceInfo);
                setSourceProperties(node, sourceInfo);
            }
            if (nodeType.endsWith("_d") || nodeType.endsWith("_m")) {
                Preconditions.checkNotNull(targetInfo);
                setTargetProperties(node, targetInfo);
            }
            treeNodes.put(nodePath, node);
            if (oldNodeType != null) {
                String oldNodePath = oldNodeType.startsWith("dir") ? key + "/" : key;
                RepositoryCompareTreeNode oldNode = new RepositoryCompareTreeNode(
                        oldNodePath, nodeParent, oldNodeType, nodeName);
                setSourceProperties(oldNode, sourceInfo);
                treeNodes.put(oldNodePath, oldNode);
            }
            while (parentPathNode.getParent() != null) {
                RepositoryPathNode grandParentPathNode = parentPathNode.getParent();
                String parentNodeParent = grandParentPathNode.getParent() != null ? grandParentPathNode + "/" : "#";
                String parentNodeName = parentPathNode.getName();
                String parentNodePath = parentPathNode + "/";
                treeNodes.putIfAbsent(parentNodePath, new RepositoryCompareTreeNode(
                        parentNodePath, parentNodeParent, "dir", parentNodeName));
                parentPathNode = grandParentPathNode;
            }
        });
        refineCompareTreeNodes(treeNodes);
        compareTreeNodes = new LinkedList<>(treeNodes.values());
        setCompareTreeNodeChildrenProperty(compareTreeNodes);
        return compareTreeNodes;
    }

    private void setSourceProperties(RepositoryCompareTreeNode node, RepositoryCompareEntry.EntryInfo info) {
        node.setSourceProperty(RepositoryCompareTreeNode.PROP_SIZE, info.getSizeString());
        node.setSourceProperty(RepositoryCompareTreeNode.PROP_MTIME, info.getMtime());
        node.setSourceProperty(RepositoryCompareTreeNode.PROP_CHECKSUM, info.getChecksum());
    }

    private void setTargetProperties(RepositoryCompareTreeNode node, RepositoryCompareEntry.EntryInfo info) {
        node.setTargetProperty(RepositoryCompareTreeNode.PROP_SIZE, info.getSizeString());
        node.setTargetProperty(RepositoryCompareTreeNode.PROP_MTIME, info.getMtime());
        node.setTargetProperty(RepositoryCompareTreeNode.PROP_CHECKSUM, info.getChecksum());
    }

    private void refineCompareTreeNodes(HashMap<String, RepositoryCompareTreeNode> treeNodes) {
        HashMap<String, Integer> childrenCount = new HashMap<>();
        treeNodes.values().forEach(node ->
                childrenCount.compute(node.parent, (k, v) -> (v == null) ? 1 : ++v));
        treeNodes.keySet().stream().sorted().forEach(key -> {
            RepositoryCompareTreeNode node = treeNodes.get(key);
            if (node.type.startsWith("dir")
                    && childrenCount.get(node.parent) == 1
                    && treeNodes.containsKey(node.parent)) {
                childrenCount.computeIfPresent(node.parent, (k, v) -> -1);
                String newParent = treeNodes.get(node.parent).parent;
                node.parent = newParent;
                if ("#".equals(newParent)) {
                    newParent = "/";
                }
                node.text = RepositoryPathNode.toUniString(Paths.get(newParent).relativize(Paths.get(node.id)));
            }
        });
        treeNodes.keySet().removeIf(key -> childrenCount.getOrDefault(key, 0) < 0);

        HashMap<String, Integer> fileCount = new HashMap<>();
        treeNodes.keySet().stream().sorted(Comparator.reverseOrder()).forEach(key -> {
            RepositoryCompareTreeNode node = treeNodes.get(key);
            fileCount.compute(node.parent, (k, v) ->
                    ((v == null) ? 0 : v) + fileCount.getOrDefault(
                            node.id, node.type.startsWith("file") ? 1 : 0));
        });
        treeNodes.forEach((k, node) -> {
            if (node.type.startsWith("dir")) {
                int childrenFileCount = fileCount.getOrDefault(node.id, 0);
                node.comment = String.format("%d file%s", childrenFileCount, childrenFileCount > 1 ? "s" : "");
            }
        });
    }

    private void setCompareTreeNodeChildrenProperty(LinkedList<RepositoryCompareTreeNode> compareTreeNodes) {
        Set<String> parentIdSet = compareTreeNodes.stream()
                .map(node -> node.parent)
                .collect(Collectors.toSet());
        compareTreeNodes.forEach(node -> node.children = parentIdSet.contains(node.id));
    }
}
