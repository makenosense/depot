package depot.model.base;

import depot.MainApp;

import java.io.File;
import java.nio.file.Paths;

public abstract class BaseModel {
    protected static final String USER_HOME = System.getProperty("user.home");
    protected static final String APP_HOME = Paths.get(USER_HOME, "." + MainApp.APP_NAME.toLowerCase()).toString();

    static {
        File appHome = new File(APP_HOME);
        if (!appHome.isDirectory()) {
            appHome.mkdirs();
        }
    }
}
