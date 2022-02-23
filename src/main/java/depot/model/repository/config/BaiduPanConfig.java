package depot.model.repository.config;

import depot.MainApp;
import depot.model.base.BaseDirTreeItem;
import depot.model.base.BaseModel;
import depot.model.repository.path.BaiduPanDirEntry;
import depot.model.repository.path.RepositoryPathNode;
import depot.model.repository.sync.RepositoryCompareEntry;
import depot.model.repository.sync.RepositoryCompareResult;
import depot.util.HttpUtil;
import depot.util.StringUtil;
import javafx.concurrent.Worker;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.net.URLEncodedUtils;
import org.json.JSONObject;
import org.tmatesoft.svn.core.SVNNodeKind;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@XmlRootElement(name = "BaiduPanConfig")
public class BaiduPanConfig extends BaseModel implements ComparableRepositoryConfig {
    private static final Logger LOGGER = Logger.getLogger("BaiduPanConfig");
    public static final String TITLE = "百度网盘";
    private static final String XML_PATH = Paths.get(APP_HOME, "BaiduPanConfig.xml").toString();
    private static final String DEFAULT_AVATAR_URL = Objects.requireNonNull(MainApp.class.getResource("view/html/img/avatar.jpg")).toExternalForm();
    private static final String CLIENT_ID = "vV7S9UGMguhcwCU6TAlNF3voVZqdiXiv";
    private static URI authUri = null;
    private static URI authSuccessUri = null;
    private static URIBuilder userInfoUriBuilder = null;
    private static URIBuilder listDirUriBuilder = null;
    private static URIBuilder listAllUriBuilder = null;

    static {
        try {
            authUri = new URIBuilder("https://openapi.baidu.com/oauth/2.0/authorize")
                    .addParameter("response_type", "token")
                    .addParameter("client_id", CLIENT_ID)
                    .addParameter("redirect_uri", "oob")
                    .addParameter("scope", "basic,netdisk")
                    .addParameter("display", "popup")
                    .build();
            authSuccessUri = new URIBuilder("https://openapi.baidu.com/oauth/2.0/login_success")
                    .build();
            userInfoUriBuilder = new URIBuilder("https://pan.baidu.com/rest/2.0/xpan/nas")
                    .addParameter("method", "uinfo");
            listDirUriBuilder = new URIBuilder("https://pan.baidu.com/rest/2.0/xpan/file")
                    .addParameter("method", "list")
                    .addParameter("web", "web")
                    .addParameter("showempty", "1");
            listAllUriBuilder = new URIBuilder("https://pan.baidu.com/rest/2.0/xpan/multimedia")
                    .addParameter("method", "listall")
                    .addParameter("limit", "10000")
                    .addParameter("recursion", "1");
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    private String accessToken;
    private Date expireTime;
    private String rootPath;
    private String userName;
    private String userAvatarUrl;

    public BaiduPanConfig() {
        this(null);
    }

    public BaiduPanConfig(String accessToken) {
        this(accessToken, null);
    }

    public BaiduPanConfig(String accessToken, Date expireTime) {
        this.accessToken = accessToken;
        this.expireTime = expireTime;
    }

    public static BaiduPanConfig signIn() {
        BaiduPanConfig baiduPanConfig = new BaiduPanConfig();

        /*获取accessToken*/
        Dialog<String> dialog = new Dialog<>();
        dialog.setResizable(true);
        dialog.setTitle("登录百度网盘");
        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getButtonTypes().setAll(ButtonType.CLOSE);
        Node closeButton = dialogPane.lookupButton(ButtonType.CLOSE);
        closeButton.managedProperty().bind(closeButton.visibleProperty());
        closeButton.setVisible(false);

        WebView webView = new WebView();
        webView.setPrefSize(700, 450);
        WebEngine webEngine = webView.getEngine();
        webEngine.load(authUri.toString());
        webEngine.getLoadWorker().stateProperty().addListener(((observable, oldValue, newValue) -> {
            if (newValue == Worker.State.SUCCEEDED) {
                try {
                    URI uri = new URI(webEngine.getLocation());
                    if (authSuccessUri.equals(new URI(uri.getScheme(),
                            uri.getAuthority(), uri.getPath(), null, null))) {
                        URLEncodedUtils.parse(uri.getRawFragment(), StandardCharsets.UTF_8).forEach(nameValuePair -> {
                            String value = nameValuePair.getValue();
                            switch (nameValuePair.getName()) {
                                case "access_token":
                                    baiduPanConfig.setAccessToken(value);
                                    break;
                                case "expires_in":
                                    baiduPanConfig.setExpireTime(new Date(System.currentTimeMillis() + Integer.parseInt(value) * 1000L));
                                    break;
                            }
                        });
                        if (baiduPanConfig.hasAccessToken()) {
                            dialog.close();
                        }
                    }
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                }
            }
        }));
        dialogPane.setContent(webView);
        dialog.showAndWait();

        /*获取用户信息*/
        baiduPanConfig.updateUserInfo();

        /*选择根目录并保存*/
        if (baiduPanConfig.changeRootPath()
                && baiduPanConfig.isComplete()) {
            try {
                baiduPanConfig.save();
                return baiduPanConfig;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return null;
    }

    public static void clearCache() {
        new File(XML_PATH).delete();
    }

    public static boolean isInvalidAccessTokenErrorNo(int errorNo) {
        return errorNo == -6
                || errorNo == 100
                || errorNo == 110
                || errorNo == 111;
    }

    public static BaiduPanConfig load() {
        try {
            File baiduPanConfigFile = new File(XML_PATH);
            if (baiduPanConfigFile.isFile()) {
                BaiduPanConfig baiduPanConfig = (BaiduPanConfig) JAXBContext.newInstance(BaiduPanConfig.class)
                        .createUnmarshaller()
                        .unmarshal(baiduPanConfigFile);
                if (!baiduPanConfig.isComplete()) {
                    clearCache();
                } else if (baiduPanConfig.expireTime != null
                        && new Date().after(baiduPanConfig.expireTime)) {
                    clearCache();
                } else {
                    return baiduPanConfig;
                }
            }
        } catch (Exception e) {
            LOGGER.warning("百度网盘配置文件读取失败：" + e);
        }
        return new BaiduPanConfig();
    }

    public void save() throws Exception {
        if (!isComplete()) {
            throw new Exception("不能保存不完整的百度网盘配置信息");
        }
        Marshaller marshaller = JAXBContext.newInstance(BaiduPanConfig.class)
                .createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        marshaller.marshal(this, new File(XML_PATH));
    }

    public boolean isComplete() {
        return StringUtil.notEmpty(accessToken)
                && StringUtil.notEmpty(rootPath)
                && StringUtil.notEmpty(userName)
                && StringUtil.notEmpty(userAvatarUrl);
    }

    public boolean hasAccessToken() {
        return StringUtil.notEmpty(accessToken);
    }

    public void updateUserInfo() {
        if (hasAccessToken()) {
            try {
                URI userInfoUri = userInfoUriBuilder
                        .setParameter("access_token", accessToken)
                        .build();
                JSONObject userInfoObj = HttpUtil.getAsJsonObject(userInfoUri);
                int errorNo = userInfoObj.getInt("errno");
                if (errorNo == 0) {
                    userName = userInfoObj.getString("baidu_name");
                    userAvatarUrl = userInfoObj.getString("avatar_url");
                } else if (isInvalidAccessTokenErrorNo(errorNo)) {
                    clearCache();
                } else {
                    LOGGER.warning(userInfoObj.getString("errmsg"));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public List<BaiduPanDirEntry> listDir(RepositoryPathNode pathNode, boolean dirOnly) {
        LinkedList<BaiduPanDirEntry> entries = new LinkedList<>();
        if (hasAccessToken()) {
            try {
                URI listDirUri = listDirUriBuilder
                        .setParameter("access_token", accessToken)
                        .setParameter("dir", pathNode.toString())
                        .build();
                JSONObject dirObj = HttpUtil.getAsJsonObject(listDirUri);
                int errorNo = dirObj.getInt("errno");
                if (errorNo == 0) {
                    dirObj.getJSONArray("list").forEach(e -> {
                        JSONObject entryObj = (JSONObject) e;
                        String path = entryObj.getString("path");
                        BaiduPanDirEntry entry = new BaiduPanDirEntry(new RepositoryPathNode(Paths.get(path)));
                        entry.setDir(entryObj.getInt("isdir") > 0);
                        entry.setDate(new Date(entryObj.getLong("server_mtime") * 1000));
                        if (entry.isDir()) {
                            entry.setEmpty(entryObj.getInt("dir_empty") > 0);
                        } else {
                            entry.setFsId(entryObj.getBigInteger("fs_id"));
                            entry.setSize(entryObj.getLong("size"));
                            entry.setChecksum(entryObj.getString("md5"));
                        }
                        if (entry.isDir() || !dirOnly) {
                            entries.add(entry);
                        }
                    });
                } else if (isInvalidAccessTokenErrorNo(errorNo)) {
                    clearCache();
                } else {
                    LOGGER.warning(dirObj.getString("errmsg"));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return entries;
    }

    public List<BaiduPanDirEntry> listAll(RepositoryPathNode pathNode, boolean dirOnly) {
        LinkedList<BaiduPanDirEntry> entries = new LinkedList<>();
        if (hasAccessToken()) {
            try {
                boolean hasMore = true;
                int start = 0;
                int trialCount = 0;
                while (hasMore) {
                    URI listAllUri = listAllUriBuilder
                            .setParameter("access_token", accessToken)
                            .setParameter("path", pathNode.toString())
                            .setParameter("start", String.valueOf(start))
                            .build();
                    JSONObject respObj = HttpUtil.getAsJsonObject(listAllUri);
                    int errorNo = respObj.getInt("errno");
                    if (errorNo == 0) {
                        trialCount = 0;
                        start = respObj.getInt("cursor");
                        hasMore = respObj.getInt("has_more") > 0;
                        respObj.getJSONArray("list").forEach(e -> {
                            JSONObject entryObj = (JSONObject) e;
                            String path = entryObj.getString("path");
                            BaiduPanDirEntry entry = new BaiduPanDirEntry(new RepositoryPathNode(Paths.get(path)));
                            entry.setDir(entryObj.getInt("isdir") > 0);
                            entry.setDate(new Date(entryObj.getLong("server_mtime") * 1000));
                            if (!entry.isDir()) {
                                entry.setFsId(entryObj.getBigInteger("fs_id"));
                                entry.setSize(entryObj.getLong("size"));
                                entry.setChecksum(entryObj.getString("md5"));
                            }
                            if (entry.isDir() || !dirOnly) {
                                entries.add(entry);
                            }
                        });
                    } else if (isInvalidAccessTokenErrorNo(errorNo)) {
                        clearCache();
                        break;
                    } else {
                        LOGGER.warning(respObj.getString("errmsg"));
                        if (++trialCount > 3) {
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return entries;
    }

    public boolean changeRootPath() {
        if (hasAccessToken()) {
            String newRootPath = MainApp.chooseRepositoryDirectory(new BaiduPanRootPathTreeItem(this, false), "设置为根目录");
            boolean changed = !Objects.equals(rootPath, newRootPath);
            rootPath = newRootPath;
            return changed;
        }
        return false;
    }

    public static class BaiduPanRootPathTreeItem extends BaseDirTreeItem {

        private final BaiduPanConfig baiduPanConfig;

        public BaiduPanRootPathTreeItem(BaiduPanConfig baiduPanConfig, boolean empty) {
            this(baiduPanConfig, new RepositoryPathNode(), empty);
        }

        public BaiduPanRootPathTreeItem(BaiduPanConfig baiduPanConfig, RepositoryPathNode pathNode, boolean empty) {
            super(pathNode);
            this.baiduPanConfig = baiduPanConfig;
            isLeaf = empty;
        }

        @Override
        protected String getName() {
            return new BaiduPanDirEntry(pathNode).getName();
        }

        @Override
        protected List<Object> getChildrenDir() {
            if (childrenDir == null) {
                childrenDir = baiduPanConfig.listDir(pathNode, true).stream()
                        .map(entry -> (Object) entry)
                        .collect(Collectors.toList());
            }
            return childrenDir;
        }

        @Override
        protected BaseDirTreeItem convertChildDir(Object childDir) {
            return new BaiduPanRootPathTreeItem(baiduPanConfig,
                    ((BaiduPanDirEntry) childDir).getPathNode(), ((BaiduPanDirEntry) childDir).isEmpty());
        }
    }

    @Override
    public String getTitle() {
        return TITLE;
    }

    @Override
    public String getUrl() {
        return isComplete() ? String.format("%s：%s", userName, rootPath) : "未登录";
    }

    public static class BaiduPanEntryInfo extends RepositoryCompareEntry.EntryInfo {

        private BaiduPanEntryInfo(SVNNodeKind kind, Date date, long size, String checksum) {
            super(kind, date, size, checksum);
        }

        public static BaiduPanEntryInfo newFileEntryInfo(Date date, long size, String checksum) {
            return new BaiduPanEntryInfo(SVNNodeKind.FILE, date, size, checksum);
        }

        public static BaiduPanEntryInfo newDirEntryInfo(Date date) {
            return new BaiduPanEntryInfo(SVNNodeKind.DIR, date, -1, null);
        }

        @Override
        public int hashCode() {
            return Objects.hash(getKind(), getSize());
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof RepositoryCompareEntry.EntryInfo
                    && ((RepositoryCompareEntry.EntryInfo) obj).getKind() == getKind()
                    && ((RepositoryCompareEntry.EntryInfo) obj).getSize() == getSize();
        }
    }

    @Override
    public HashMap<RepositoryPathNode, RepositoryCompareEntry.EntryInfo> collectEntryInfo(
            RepositoryPathNode pathNode, RepositoryCompareResult.CollectListener collectListener) throws Exception {
        HashMap<RepositoryPathNode, RepositoryCompareEntry.EntryInfo> entryInfoMap = new HashMap<>();
        if (StringUtil.notEmpty(rootPath)) {
            RepositoryPathNode rootPathNode = new RepositoryPathNode(Paths.get(rootPath));
            for (BaiduPanDirEntry entry : listAll(rootPathNode, false)) {
                RepositoryPathNode entryRelPathNode = new RepositoryPathNode(
                        rootPathNode.relativize(entry.getPathNode().getPath()).toString());
                if (collectListener != null) {
                    collectListener.handle(entryRelPathNode);
                }
                entryInfoMap.put(entryRelPathNode, entry.isDir() ?
                        BaiduPanEntryInfo.newDirEntryInfo(entry.getDate()) :
                        BaiduPanEntryInfo.newFileEntryInfo(entry.getDate(), entry.getSize(), entry.getChecksum()));
            }
        }
        return entryInfoMap;
    }

    public String getAvatarExternalUrl() {
        return StringUtil.notEmpty(userAvatarUrl) ? userAvatarUrl : DEFAULT_AVATAR_URL;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public Date getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(Date expireTime) {
        this.expireTime = expireTime;
    }

    public String getRootPath() {
        return rootPath;
    }

    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserAvatarUrl() {
        return userAvatarUrl;
    }

    public void setUserAvatarUrl(String userAvatarUrl) {
        this.userAvatarUrl = userAvatarUrl;
    }
}
