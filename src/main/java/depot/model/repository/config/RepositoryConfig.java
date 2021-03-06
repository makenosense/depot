package depot.model.repository.config;

import com.google.common.base.Preconditions;
import depot.model.base.BaseModel;
import depot.model.repository.path.RepositoryDirEntry;
import depot.model.repository.path.RepositoryPathNode;
import depot.model.repository.sync.RepositoryCompareEntry;
import depot.model.repository.sync.RepositoryCompareResult;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import netscape.javascript.JSObject;
import org.apache.commons.lang3.StringUtils;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.File;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;

public class RepositoryConfig extends BaseModel implements ComparableRepositoryConfig {
    private static final Logger LOGGER = Logger.getLogger("RepositoryConfig");
    public static final Set<String> SUPPORTED_PROTOCOLS = new HashSet<>();
    public static final String PROTOCOL_SVN_SSH = "svn+ssh";
    public static final String PROTOCOL_SVN = "svn";
    public static final String PROTOCOL_FILE = "file";
    public static final Set<String> SUPPORTED_AUTH_TYPES = new HashSet<>();
    public static final String AUTH_TYPE_PASSWORD = "password";
    public static final String AUTH_TYPE_PRIVATE_KEY = "privateKey";
    public static final String DEFAULT_TITLE = "ROOT";

    @Getter
    @Setter
    private String repositoryUUID;

    @Getter
    @Setter
    private String protocol;

    @Getter
    @Setter
    private String host;

    private transient String portString;

    @Getter
    @Setter
    private int port;

    @Getter
    @Setter
    private String path;

    @Getter
    @Setter
    private String authType;

    @Getter
    @Setter
    private String userName;

    @Getter
    @Setter
    private String password;

    @Getter
    @Setter
    private String privateKey;

    @Getter
    @Setter
    private String passphrase;

    private transient SVNRepository repository;

    static {
        SUPPORTED_PROTOCOLS.add(PROTOCOL_SVN_SSH);
        SUPPORTED_PROTOCOLS.add(PROTOCOL_SVN);
        SUPPORTED_PROTOCOLS.add(PROTOCOL_FILE);
        SUPPORTED_AUTH_TYPES.add(AUTH_TYPE_PASSWORD);
        SUPPORTED_AUTH_TYPES.add(AUTH_TYPE_PRIVATE_KEY);
    }

    private RepositoryConfig() {
    }

    public RepositoryConfig(JSObject params) throws Exception {
        protocol = (String) params.getMember("protocol");
        host = (String) params.getMember("host");
        portString = (String) params.getMember("port");
        path = (String) params.getMember("path");
        authType = (String) params.getMember("authType");
        userName = (String) params.getMember("userName");
        password = (String) params.getMember("password");
        privateKey = (String) params.getMember("privateKey");
        passphrase = (String) params.getMember("passphrase");
        buildParams();
    }

    private void buildParams() throws Exception {
        protocol = protocol.toLowerCase();
        if (!SUPPORTED_PROTOCOLS.contains(protocol)) {
            throw new Exception("???????????????????????????" + protocol + "???");
        }
        String defaultHost = !PROTOCOL_FILE.equals(protocol) ? "localhost" : null;
        host = StringUtils.isBlank(host) || PROTOCOL_FILE.equals(protocol) ? defaultHost : host;
        port = -1;
        if (StringUtils.isNotBlank(portString) && !PROTOCOL_FILE.equals(protocol)) {
            try {
                port = Integer.parseInt(portString);
            } catch (Exception e) {
                throw new Exception("?????????" + portString + "????????????");
            }
        }
        path = StringUtils.defaultIfBlank(path, null);
        if (!SUPPORTED_AUTH_TYPES.contains(authType)) {
            throw new Exception("???????????????????????????" + authType + "???");
        }
        userName = StringUtils.defaultIfBlank(userName, null);
        password = StringUtils.isBlank(password) || !AUTH_TYPE_PASSWORD.equals(authType) ? null : password;
        String defaultPrivateKey = AUTH_TYPE_PRIVATE_KEY.equals(authType) ? Paths.get(USER_HOME, ".ssh/id_rsa").toString() : null;
        privateKey = StringUtils.isBlank(privateKey) || !AUTH_TYPE_PRIVATE_KEY.equals(authType) ? defaultPrivateKey : privateKey;
        passphrase = StringUtils.isBlank(passphrase) || !AUTH_TYPE_PRIVATE_KEY.equals(authType) ? null : passphrase;
    }

    public static RepositoryConfig newFileRepositoryConfig(String path, String userName, String password) throws Exception {
        RepositoryConfig config = new RepositoryConfig();
        config.setProtocol(PROTOCOL_FILE);
        config.setPath(path);
        config.setAuthType(AUTH_TYPE_PASSWORD);
        config.setUserName(userName);
        config.setPassword(password);
        config.buildParams();
        return config;
    }

    public SVNRepository getRepository() throws Exception {
        if (repository == null) {
            char[] passwordCharArray = password != null ? password.toCharArray() : null;
            File privateKeyFile = privateKey != null ? new File(privateKey) : null;
            char[] passphraseCharArray = passphrase != null ? passphrase.toCharArray() : null;
            repository = SVNRepositoryFactory.create(getSvnUrl());
            ISVNAuthenticationManager authManager = SVNWCUtil.createDefaultAuthenticationManager(
                    null, userName, passwordCharArray, privateKeyFile, passphraseCharArray, false);
            repository.setAuthenticationManager(authManager);
            String uuid = repository.getRepositoryUUID(true);
            if (repositoryUUID != null && !repositoryUUID.equals(uuid)) {
                throw new Exception("UUID??????????????????????????????????????????????????????????????????????????????");
            }
            repositoryUUID = uuid;
        }
        return repository;
    }

    public SVNURL getSvnUrl() {
        try {
            String userInfo = host != null ? userName : null;
            return SVNURL.create(protocol, userInfo, host, port, path, true);
        } catch (Exception e) {
            LOGGER.warning(e.toString());
            return null;
        }
    }

    private boolean isPathSeparator(char ch) {
        return ch == '/' || ch == '\\';
    }

    @Override
    public String getTitle() {
        String path = this.path.trim();
        int ep = path.length() - 1;
        while ((ep >= 0) && isPathSeparator(path.charAt(ep))) {
            ep--;
        }
        if (ep < 0) {
            return DEFAULT_TITLE;
        }
        String realPath = path.substring(0, ep + 1);
        int sp = Math.max(realPath.lastIndexOf('/'), realPath.lastIndexOf('\\'));
        String title = (sp >= 0 ? realPath.substring(sp + 1) : realPath).trim();
        return title.length() > 0 ? title : DEFAULT_TITLE;
    }

    @Override
    public String getUrl() {
        return getSvnUrl().toDecodedString();
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @XmlRootElement(name = "RepositoryConfigList")
    private static class RepositoryConfigList {
        private static final String XML_PATH = Paths.get(APP_HOME, "RepositoryConfigList.xml").toString();

        @Getter
        private LinkedList<RepositoryConfig> repositoryConfigs = new LinkedList<>();

        @XmlElement(name = "RepositoryConfig")
        public void setRepositoryConfigs(LinkedList<RepositoryConfig> repositoryConfigs) {
            this.repositoryConfigs = repositoryConfigs;
        }
    }

    public static LinkedList<RepositoryConfig> loadAll() {
        try {
            File xmlFile = new File(RepositoryConfigList.XML_PATH);
            if (xmlFile.isFile()) {
                return ((RepositoryConfigList) JAXBContext.newInstance(RepositoryConfigList.class)
                        .createUnmarshaller()
                        .unmarshal(xmlFile))
                        .getRepositoryConfigs();
            }
        } catch (Exception e) {
            LOGGER.warning(e.toString());
        }
        return new LinkedList<>();
    }

    public static void saveAll(LinkedList<RepositoryConfig> repositoryConfigs) throws Exception {
        Marshaller marshaller = JAXBContext.newInstance(RepositoryConfigList.class)
                .createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        marshaller.marshal(new RepositoryConfigList(repositoryConfigs), new File(RepositoryConfigList.XML_PATH));
    }

    public static RepositoryConfig load(String uuid) throws Exception {
        return loadAll().stream()
                .filter(repositoryConfig -> repositoryConfig.repositoryUUID.equals(uuid))
                .findFirst()
                .orElseThrow(() -> new Exception("?????????UUID??????" + uuid + "??????????????????"));
    }

    public static RepositoryConfig loadAndMoveFirst(String uuid) throws Exception {
        LinkedList<RepositoryConfig> repositoryConfigs = loadAll();
        RepositoryConfig config = repositoryConfigs.stream()
                .filter(repositoryConfig -> repositoryConfig.repositoryUUID.equals(uuid))
                .findFirst()
                .orElseThrow(() -> new Exception("?????????UUID??????" + uuid + "??????????????????"));
        if (!Objects.equals(repositoryConfigs.peekFirst(), config)) {
            repositoryConfigs.remove(config);
            repositoryConfigs.addFirst(config);
            saveAll(repositoryConfigs);
        }
        return config;
    }

    public static RepositoryConfig remove(String uuid) throws Exception {
        LinkedList<RepositoryConfig> repositoryConfigs = loadAll();
        RepositoryConfig config = repositoryConfigs.stream()
                .filter(repositoryConfig -> repositoryConfig.repositoryUUID.equals(uuid))
                .findFirst()
                .orElse(null);
        if (config != null) {
            repositoryConfigs.remove(config);
            saveAll(repositoryConfigs);
        }
        return config;
    }

    public void save() throws Exception {
        if (repositoryUUID == null) {
            throw new Exception("????????????UUID?????????????????????");
        }
        LinkedList<RepositoryConfig> repositoryConfigs = loadAll();
        if (!repositoryConfigs.contains(this)) {
            repositoryConfigs.addFirst(this);
            saveAll(repositoryConfigs);
        }
    }

    @Override
    public HashMap<RepositoryPathNode, RepositoryCompareEntry.EntryInfo> collectEntryInfo(
            RepositoryPathNode pathNode, RepositoryCompareResult.CollectListener collectListener) throws Exception {
        HashMap<RepositoryPathNode, RepositoryCompareEntry.EntryInfo> entryInfoMap = new HashMap<>();
        SVNRepository repository = getRepository();
        Map<RepositoryPathNode, String> checksumMap = RepositoryDirEntry.getChecksumMap(repository);
        RepositoryDirEntry.traverse(repository, pathNode, (currentPathNode, entry) -> {
            if (collectListener != null) {
                collectListener.handle(currentPathNode);
            }
            if (entry.getKind() == SVNNodeKind.FILE) {
                Preconditions.checkArgument(checksumMap.containsKey(currentPathNode));
                entryInfoMap.put(currentPathNode, RepositoryCompareEntry.EntryInfo.newFileEntryInfo(
                        entry.getDate(), entry.getSize(), checksumMap.get(currentPathNode)));
            } else if (entry.getKind() == SVNNodeKind.DIR) {
                if (currentPathNode.getParent() != null) {
                    entryInfoMap.put(currentPathNode, RepositoryCompareEntry.EntryInfo.newDirEntryInfo(entry.getDate()));
                }
            }
        });
        return entryInfoMap;
    }

    @Override
    public int hashCode() {
        return Objects.hash(repositoryUUID, protocol, host, port, path,
                authType, userName, password, privateKey, passphrase);
    }

    @Override
    public boolean equals(Object obj) {
        return (obj instanceof RepositoryConfig)
                && Objects.equals(((RepositoryConfig) obj).repositoryUUID, repositoryUUID)
                && Objects.equals(((RepositoryConfig) obj).protocol, protocol)
                && Objects.equals(((RepositoryConfig) obj).host, host)
                && Objects.equals(((RepositoryConfig) obj).port, port)
                && Objects.equals(((RepositoryConfig) obj).path, path)
                && Objects.equals(((RepositoryConfig) obj).authType, authType)
                && Objects.equals(((RepositoryConfig) obj).userName, userName)
                && Objects.equals(((RepositoryConfig) obj).password, password)
                && Objects.equals(((RepositoryConfig) obj).privateKey, privateKey)
                && Objects.equals(((RepositoryConfig) obj).passphrase, passphrase);
    }
}
