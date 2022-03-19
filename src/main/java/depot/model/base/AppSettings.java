package depot.model.base;

import lombok.Getter;
import lombok.Setter;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.File;
import java.nio.file.Paths;
import java.util.logging.Logger;

@Getter
@Setter
@XmlRootElement(name = "AppSettings")
public class AppSettings extends BaseModel {
    private static final String XML_PATH = Paths.get(APP_HOME, "AppSettings.xml").toString();
    private static final Logger LOGGER = Logger.getLogger("AppSettings");

    private File downloadParent;

    public AppSettings() {
        downloadParent = new File(APP_HOME, "download");
    }

    public static AppSettings load() {
        try {
            File appSettingsFile = new File(XML_PATH);
            if (appSettingsFile.isFile()) {
                return (AppSettings) JAXBContext.newInstance(AppSettings.class)
                        .createUnmarshaller()
                        .unmarshal(appSettingsFile);
            }
        } catch (Exception e) {
            LOGGER.warning("设置文件读取失败：" + e);
        }
        return new AppSettings();
    }

    public void save() throws Exception {
        Marshaller marshaller = JAXBContext.newInstance(AppSettings.class)
                .createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        marshaller.marshal(this, new File(XML_PATH));
    }
}
