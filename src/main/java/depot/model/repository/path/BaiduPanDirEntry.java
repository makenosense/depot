package depot.model.repository.path;

import depot.util.FileUtil;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.Date;

public class BaiduPanDirEntry {

    private BigInteger fsId;
    private boolean isDir;
    private RepositoryPathNode pathNode;
    private Date date;
    private long size;
    private String checksum;
    private boolean isEmpty;

    public BaiduPanDirEntry(RepositoryPathNode pathNode) {
        this.pathNode = pathNode;
    }

    @Override
    public String toString() {
        return getName();
    }

    public String getName() {
        Path path = pathNode.getPath();
        return path.getNameCount() > 0 ? path.getFileName().toString() : "我的网盘";
    }

    public String getExt() {
        String name = getName();
        int rIdx = name.lastIndexOf('.');
        return rIdx >= 0 ? name.substring(rIdx + 1) : "";
    }

    public BigInteger getFsId() {
        return fsId;
    }

    public void setFsId(BigInteger fsId) {
        this.fsId = fsId;
    }

    public boolean isDir() {
        return isDir;
    }

    public void setDir(boolean dir) {
        isDir = dir;
    }

    public RepositoryPathNode getPathNode() {
        return pathNode;
    }

    public void setPathNode(RepositoryPathNode pathNode) {
        this.pathNode = pathNode;
    }

    public Date getDate() {
        return date;
    }

    public String getMtime() {
        Date date = getDate();
        return String.format("%tF %tT", date, date);
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public long getSize() {
        return size;
    }

    public String getSizeString() {
        return isDir ? "-" : FileUtil.getSizeString(getSize());
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public boolean isEmpty() {
        return isEmpty;
    }

    public void setEmpty(boolean empty) {
        isEmpty = empty;
    }
}
