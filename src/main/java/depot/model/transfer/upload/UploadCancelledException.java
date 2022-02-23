package depot.model.transfer.upload;

public class UploadCancelledException extends Exception {

    public UploadCancelledException() {
        super("上传已取消");
    }

    public UploadCancelledException(String message) {
        super(message);
    }
}
