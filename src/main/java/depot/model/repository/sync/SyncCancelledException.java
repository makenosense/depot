package depot.model.repository.sync;

public class SyncCancelledException extends Exception {
    public SyncCancelledException() {
        super("同步已取消");
    }
}
