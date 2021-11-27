package depot.model.transfer.download;

import depot.model.repository.path.RepositoryPathNode;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.SVNRepository;

import java.io.File;

public class DownloadTask {

    private final RepositoryPathNode pathNode;
    private final SVNDirEntry dirEntry;
    private final ISVNReporterBaton reporter;
    private final DownloadEditor editor;

    public DownloadTask(RepositoryPathNode pathNode, SVNDirEntry dirEntry, RepositoryPathNode parentPathNode) {
        this(pathNode, dirEntry, parentPathNode, null);
    }

    public DownloadTask(RepositoryPathNode pathNode, SVNDirEntry dirEntry, RepositoryPathNode parentPathNode, File downloadParent) {
        this.pathNode = pathNode;
        this.dirEntry = dirEntry;
        this.reporter = reporter -> {
            try {
                reporter.setPath("", null, dirEntry.getRevision(), SVNDepth.INFINITY, true);
                reporter.deletePath("");
                reporter.finishReport();
            } catch (Exception e) {
                reporter.abortReport();
                throw e;
            }
        };
        this.editor = new DownloadEditor(parentPathNode, downloadParent);
    }

    public void execute(SVNRepository repository) throws Exception {
        try {
            repository.update(-1, pathNode.toString(), true, reporter, editor);
        } catch (Exception e) {
            try {
                editor.textDeltaEnd(pathNode.toString());
            } catch (Exception ignored) {
            }
            try {
                editor.abortEdit();
            } catch (Exception ignored) {
            }
            throw e;
        }
    }

    public String getName() {
        return pathNode.getName();
    }

    public long getSize() {
        return dirEntry.getSize();
    }

    public DownloadEditor getEditor() {
        return editor;
    }
}
