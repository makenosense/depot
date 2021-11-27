package depot.model.repository.content;

import depot.model.repository.path.RepositoryDirEntry;
import depot.model.repository.path.RepositoryPathNode;

import java.util.ArrayList;
import java.util.List;

public class RepositoryContentData {

    public List<RepositoryPathNode> pathNodeList = new ArrayList<>();
    public List<RepositoryDirEntry> entryList = new ArrayList<>();

    private Object[] pathNodeArrayCache;
    private Object[] entryArrayCache;

    public Object[] getPathNodeArray() {
        pathNodeArrayCache = pathNodeList.toArray();
        return pathNodeArrayCache;
    }

    public Object[] getEntryArray() {
        entryArrayCache = entryList.toArray();
        return entryArrayCache;
    }
}
