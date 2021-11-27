package depot.model.transfer.download;

import depot.model.transfer.base.BaseTransferData;
import javafx.concurrent.Task;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public class DownloadTransactionData extends BaseTransferData {

    private final LinkedList<DownloadTask> downloadTasks;
    private final Map<DownloadTask, Long> prevSizeMap = new HashMap<>();

    public DownloadTransactionData(LinkedList<DownloadTask> downloadTasks,
                                   Task<Void> task) throws Exception {
        this.downloadTasks = downloadTasks;
        totalSize = 0;
        for (DownloadTask downloadTask : downloadTasks) {
            if (task.isCancelled()) {
                throw new DownloadCancelledException();
            }
            prevSizeMap.put(downloadTask, totalSize);
            totalSize += downloadTask.getSize();
        }
    }

    public LinkedList<DownloadTask> downloadTasks() {
        return downloadTasks;
    }

    public int lengthOfTasks() {
        return downloadTasks.size();
    }

    public int indexOfTask(DownloadTask downloadTask) {
        return downloadTasks.indexOf(downloadTask);
    }

    public long getPrevSize(DownloadTask downloadTask) {
        return prevSizeMap.getOrDefault(downloadTask, 0L);
    }
}
