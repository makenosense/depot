package depot.model.repository.log;

import depot.model.base.BaseTreeNode;

public class RepositoryLogTreeNode extends BaseTreeNode {
    public static final String DATE_TEXT_TPL = "<span class='log-date'>%s</span>";
    public static final String REVISION_TEXT_TPL = "<span class='revision-time' title='%tT'>%tR</span> " +
            "(%d) <span class='revision-message' title='%s'>%s</span><span class='revision-item-count'> - %d项</span>";

    public RepositoryLogTreeNode(String id, String parent, String type, String text, String comment) {
        this(id, parent, type, text, comment, false);
    }

    public RepositoryLogTreeNode(String id, String parent, String type, String text, String comment, boolean opened) {
        super(id, parent, type, text, comment, opened);
    }
}
