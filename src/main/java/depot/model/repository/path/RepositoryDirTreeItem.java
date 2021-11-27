package depot.model.repository.path;

import depot.model.base.BaseDirTreeItem;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.io.SVNRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RepositoryDirTreeItem extends BaseDirTreeItem {

    private final SVNRepository repository;

    public RepositoryDirTreeItem(SVNRepository repository) {
        this(repository, new RepositoryPathNode());
    }

    public RepositoryDirTreeItem(SVNRepository repository, RepositoryPathNode pathNode) {
        super(pathNode);
        this.repository = repository;
    }

    @Override
    protected String getName() {
        return pathNode.getName();
    }

    @Override
    protected List<Object> getChildrenDir() throws Exception {
        if (childrenDir == null) {
            childrenDir = new ArrayList<>();
            if (repository.checkPath(pathNode.toString(), -1) == SVNNodeKind.DIR) {
                repository.getDir(pathNode.toString(), -1, null, childrenDir);
            }
            childrenDir = childrenDir.stream()
                    .filter(dirEntry -> ((SVNDirEntry) dirEntry).getKind() == SVNNodeKind.DIR)
                    .collect(Collectors.toList());
        }
        return childrenDir;
    }

    @Override
    protected BaseDirTreeItem convertChildDir(Object childDir) {
        return new RepositoryDirTreeItem(repository, pathNode.resolve(((SVNDirEntry) childDir).getName()));
    }
}
