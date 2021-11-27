package depot.model.base;

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import depot.model.repository.path.RepositoryPathNode;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TreeItem;

import java.util.List;

public abstract class BaseDirTreeItem extends TreeItem<String> {
    protected static final String ICON_SIZE = "16px";

    protected final RepositoryPathNode pathNode;
    protected Boolean isLeaf = null;
    protected List<Object> childrenDir = null;
    protected boolean childrenFilled = false;

    protected BaseDirTreeItem(RepositoryPathNode pathNode) {
        super();
        this.pathNode = pathNode;
        setValue(getName());
        setGraphic(GlyphsDude.createIcon(FontAwesomeIcon.FOLDER, ICON_SIZE));
        expandedProperty().addListener((observable, oldValue, newValue) ->
                setGraphic(GlyphsDude.createIcon(newValue ?
                        FontAwesomeIcon.FOLDER_OPEN : FontAwesomeIcon.FOLDER, ICON_SIZE)));
    }

    protected abstract String getName();

    protected abstract List<Object> getChildrenDir() throws Exception;

    protected abstract BaseDirTreeItem convertChildDir(Object childDir);

    @Override
    public boolean isLeaf() {
        if (isLeaf == null) {
            try {
                isLeaf = getChildrenDir().size() <= 0;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return isLeaf;
    }

    @Override
    public ObservableList<TreeItem<String>> getChildren() {
        if (!childrenFilled) {
            try {
                ObservableList<TreeItem<String>> children = FXCollections.observableArrayList();
                getChildrenDir().forEach(dirEntry -> children.add(convertChildDir(dirEntry)));
                super.getChildren().setAll(children);
                childrenFilled = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return super.getChildren();
    }

    public RepositoryPathNode getPathNode() {
        return pathNode;
    }
}
