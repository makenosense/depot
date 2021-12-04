package depot.model.base;

public class BaseTreeNode {

    public String id;
    public String parent;
    public String type;
    public String text;
    public String comment;
    public State state = new State();
    public boolean children = true;

    public static class State {

        public boolean opened = false;
    }

    public BaseTreeNode(String id, String parent, String type, String text, String comment, boolean opened) {
        this.id = id;
        this.parent = parent;
        this.type = type;
        this.text = text;
        this.comment = comment;
        this.state.opened = opened;
    }
}
