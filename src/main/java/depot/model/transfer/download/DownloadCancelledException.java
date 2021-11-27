package depot.model.transfer.download;

public class DownloadCancelledException extends Exception {
    public DownloadCancelledException() {
        super("下载已取消");
    }
}
