import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.Nullable;

/**
 * PersistentStateComponent keeps project config values.
 * Similar notion of 'preference' in Android
 */
@State(
        name="SingleFileExecutionConfig",
        storages = {
                @Storage("SingleFileExecutionConfig.xml")}
)
public class SingleFileExecutionConfig implements PersistentStateComponent<SingleFileExecutionConfig> {

    /* NOTE: member should be "public" to be saved in xml */
    /* add_executable(): exe name */
    static final String EXECUTABLE_NAME_FILENAME = "%FILENAME%";
    public static final String DEFAULT_EXECUTABLE_NAME = EXECUTABLE_NAME_FILENAME;
    public String executableName = DEFAULT_EXECUTABLE_NAME;  // persistent member should be public
    /* set_target_properties(): runtime output directory */
    static final String PROJECTDIR = "%PROJECTDIR%";
    static final String FILEDIR = "%FILEDIR%";
    public static final String DEFAULT_RUNTIME_OUTPUT_DIRECTORY = "";
    public String runtimeOutputDirectory = "";   // set empty string as default, persistent member should be public
    private static final boolean DEFAULT_NOT_SHOW_OVERWRITE_CONFIRM_DIALOG = false;
    public boolean notShowOverwriteConfirmDialog = DEFAULT_NOT_SHOW_OVERWRITE_CONFIRM_DIALOG;  // persistent member should be public

    SingleFileExecutionConfig() { }

    String getExecutableName() {
        if (executableName == null) {
            // Error, it should not happen
            executableName = "";
        }
        return executableName;
    }

    void setExecutableName(String executableName) {
        this.executableName = executableName;
    }

    public String getRuntimeOutputDirectory() {
        return runtimeOutputDirectory;
    }

    public void setRuntimeOutputDirectory(String runtimeOutputDirectory) {
        this.runtimeOutputDirectory = runtimeOutputDirectory;
    }

    @Nullable
    @Override
    public SingleFileExecutionConfig getState() {
        return this;
    }

    @Override
    public void loadState(SingleFileExecutionConfig singleFileExecutionConfig) {
        XmlSerializerUtil.copyBean(singleFileExecutionConfig, this);
    }

    @Nullable
    public static SingleFileExecutionConfig getInstance(Project project) {
        SingleFileExecutionConfig sfec = ServiceManager.getService(project, SingleFileExecutionConfig.class);
        return sfec;
    }
}
