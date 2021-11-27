package depot.model.transfer.base;

public abstract class BaseTransferData {

    protected long totalSize;

    private Long lastRecordTime;
    private long lastTotalTransfer = 0;
    private String lastRemainingTimeString = "inf";

    public long getTotalSize() {
        return totalSize;
    }

    public String getRemainingTimeString(long totalTransfer) {
        long recordTime = System.currentTimeMillis();
        if (lastRecordTime == null) {
            lastRecordTime = recordTime;
            lastTotalTransfer = totalTransfer;
            return lastRemainingTimeString;
        }
        if (recordTime - lastRecordTime < 5000
                && !"inf".equals(lastRemainingTimeString)) {
            return lastRemainingTimeString;
        }
        if (totalTransfer >= totalSize) {
            lastRemainingTimeString = "00:00:00";
            return lastRemainingTimeString;
        }
        if (totalTransfer <= lastTotalTransfer) {
            lastRecordTime = recordTime;
            lastTotalTransfer = totalTransfer;
            lastRemainingTimeString = "inf";
            return lastRemainingTimeString;
        }
        double remainingTime = 1. * (totalSize - totalTransfer) * (recordTime - lastRecordTime) / (totalTransfer - lastTotalTransfer);
        remainingTime /= 1000;
        int hour = (int) (remainingTime / 3600);
        int minute = (int) ((remainingTime % 3600) / 60);
        int second = (int) (remainingTime % 60);
        lastRecordTime = recordTime;
        lastTotalTransfer = totalTransfer;
        lastRemainingTimeString = String.format("%d:%02d:%02d", hour, minute, second);
        return lastRemainingTimeString;
    }
}
