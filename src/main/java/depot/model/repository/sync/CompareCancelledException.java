package depot.model.repository.sync;

public class CompareCancelledException extends Exception {
    public CompareCancelledException() {
        super("比较已取消");
    }
}
