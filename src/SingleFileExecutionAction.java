import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Objects;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 */
class SingleFileExecutionAction extends AnAction {
    private static final Logger LOG = Logger.getInstance(SingleFileExecutionAction.class.getSimpleName());

    private static final int EXE_NOT_EXIST = 0;
    private static final int EXE_EXIST_SAME_SOURCE = 1;
    private static final int EXE_EXIST_DIFFERENT_SOURCE = 2;
    private VirtualFile sourceFile;
    private SingleFileExecutionConfig config;
    private Project project;
    private static final String CMAKE_FILE = "/CMakeLists.txt";

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {

        /* Get all the required data from data keys */
        //final Editor editor = e.getRequiredData(CommonDataKeys.EDITOR);
        project = e.getRequiredData(CommonDataKeys.PROJECT);
        config = SingleFileExecutionConfig.getInstance(project);

        // get source file (* currently selected file in editor)
        sourceFile = e.getData(PlatformDataKeys.VIRTUAL_FILE);


        String cmakelistFilePath = Objects.requireNonNull(sourceFile).getParent().getPath() + CMAKE_FILE;
        File cmakeOnCurrentFolder = new File(cmakelistFilePath);
        if(!cmakeOnCurrentFolder.exists()){
            cmakelistFilePath = project.getBasePath() + CMAKE_FILE;
        }

        //Access document, caret, and selection
        //final Document document = editor.getDocument();
        //final SelectionModel selectionModel = editor.getSelectionModel();
        //final int start = selectionModel.getSelectionStart();
        //final int end = selectionModel.getSelectionEnd();

        File file = new File(cmakelistFilePath);
        VirtualFile cmakelistFile = LocalFileSystem.getInstance().findFileByIoFile(file);
        if (cmakelistFile == null) {
            /* CMakeLists.txt not exist */
            Notifications.Bus.notify (
                    new Notification("singlefileexecutionaction", "Single File Execution Plugin", "Fail to access " + cmakelistFilePath, NotificationType.ERROR)
            );
            return;
        }
        Document cmakelistDocument = FileDocumentManager.getInstance().getDocument(cmakelistFile);

        //vFile.getCanonicalPath();   // source file path (absolute path)
        //vFile.getPath();            // source file path (absolute path)
        String fileName = sourceFile != null ? sourceFile.getName() : null;  // source file name (but not include path)

        String exeName = buildExeName(config.getExecutableName());
        String relativeSourcePath = new File(Objects.requireNonNull(sourceFile.getParent().getPath())).toURI().relativize(new File(sourceFile.getPath()).toURI()).getPath();
        if(!cmakeOnCurrentFolder.exists()){
            relativeSourcePath = new File(Objects.requireNonNull(project.getBasePath())).toURI().relativize(new File(sourceFile.getPath()).toURI()).getPath();
        }

        /* parse cmakelistDocument to check existence of exe_name */
        /* See http://mmasashi.hatenablog.com/entry/20091129/1259511129 for lazy, greedy search */
        String regex = "^add_executable\\s*?\\(\\s*?" + exeName + "\\s+(((\\S+)\\s+)*\\S+)\\s*\\)";

        Pattern pattern = Pattern.compile(regex);

        Scanner scanner = new Scanner(Objects.requireNonNull(cmakelistDocument).getText());
        int exeExistFlag = EXE_NOT_EXIST;
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            Matcher m = pattern.matcher(line);
            if (m.find()) {
                //String existingExeName = m.group(1);
                String existingSourceName = m.group(1);
                if (existingSourceName.contains(relativeSourcePath)) {
                    exeExistFlag = EXE_EXIST_SAME_SOURCE;
                } else {
                    exeExistFlag = EXE_EXIST_DIFFERENT_SOURCE;
                }
                break;
            }
        }
        scanner.close();

        //LocalFileSystem.getInstance().findFileByIoFile();
        switch(exeExistFlag) {
            case EXE_NOT_EXIST:
                insertAddExecutable(cmakelistDocument, exeName, relativeSourcePath);
                Notifications.Bus.notify (
                        new Notification("singlefileexecutionaction", "Single File Execution Plugin", "add_executable added for " + fileName + ".", NotificationType.INFORMATION)
                );
                break;
            case EXE_EXIST_SAME_SOURCE:
                // skip setText
                Notifications.Bus.notify (
                        new Notification("singlefileexecutionaction", "Single File Execution Plugin", "add_executable for this source already exists.", NotificationType.INFORMATION)
                );
                break;
            case EXE_EXIST_DIFFERENT_SOURCE:
                int okFlag;
                if (config.notShowOverwriteConfirmDialog) {
                    // Do not show dialog & proceed
                    okFlag = ExeOverwriteConfirmDialog.OK_FLAG_OK;
                } else {
                    okFlag = ExeOverwriteConfirmDialog.show(project);
                }

                if (okFlag == ExeOverwriteConfirmDialog.OK_FLAG_OK) {
                    // Ok
                    updateAddExecutable(cmakelistDocument, exeName, relativeSourcePath);
                    Notifications.Bus.notify(
                            new Notification("singlefileexecutionaction", "Single File Execution Plugin", "add_executable overwritten", NotificationType.INFORMATION)
                    );
                }  // cancel
                // do nothing so far

                break;
        }

    }

    private void insertAddExecutable(final Document cmakelistDocument, final String exeName, final String relativeSourcePath) {
        ApplicationManager.getApplication().runWriteAction(() -> {
            String updatedText = cmakelistDocument.getText();
            /* add_executable statement */
            updatedText += "\n" + constructAddExecutable(exeName, relativeSourcePath);
            /* set_target_properties statement */
            String runtimeDir = config.getRuntimeOutputDirectory();
            if (runtimeDir != null && !runtimeDir.equals("")) {
                String outputDir = quoteString(buildRuntimeOutputDirectory());
                updatedText += "\n" + constructSetTargetProperties(exeName, outputDir);
            }
            cmakelistDocument.setText(updatedText);
        });
    }

    private void updateAddExecutable(final Document cmakelistDocument, final String exeName, final String relativeSourcePath) {
        String runtimeDir = config.getRuntimeOutputDirectory();
        StringBuilder updatedDocument = new StringBuilder();

        /*
         * This regular expression finds
         * "add_executable(XXXX YYYY.cpp ZZZZ.cpp)" where XXXX is executable name, YYYY.cpp and ZZZZ.cpp are the source files.
         */
        String regex = "^add_executable\\s*?\\(\\s*?" + exeName + "\\s+(((\\S+)\\s+)*\\S+)\\s*\\)";
        Pattern pattern = Pattern.compile(regex);

        String regex2 = "^set_target_properties\\s*?\\(\\s*?" + exeName + "\\s+(((\\S+)\\s+)*\\S+)\\s*\\)";
        Pattern pattern2 = Pattern.compile(regex2);

        Scanner scanner = new Scanner(cmakelistDocument.getText());

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();

            Matcher m = pattern.matcher(line);
            Matcher m2 = pattern2.matcher(line);
            if (m2.find()) {
                /* Skip adding line for old "set_target_properties()" statement */
                continue;
            }
            if (m.find()) {
                /* add_executable */
                line = m.replaceFirst(constructAddExecutable(exeName, relativeSourcePath));
                /* set_target_properties */
                if (runtimeDir != null && !runtimeDir.equals("")) {
                    String outputDir = quoteString(buildRuntimeOutputDirectory());
                    line += "\n" + constructSetTargetProperties(exeName, outputDir);
                }
            }
            updatedDocument.append(line).append('\n');
        }
        scanner.close();
        final String updatedText = updatedDocument.toString();
        ApplicationManager.getApplication().runWriteAction(() -> cmakelistDocument.setText(updatedText));
    }

    /** building add_executable(exeName sourceFilePath) statement */
    private String constructAddExecutable(String exeName, String sourceFilePath) {
        return "add_executable("+ exeName + " " + quotingSourcePath(sourceFilePath) +")";
    }

    /** building set_target_properties(exeName PROPERTIES RUNTIME_OUTPUT_DIRECTORY ourputDir) statement */
    private String constructSetTargetProperties(String exeName, String outputDir) {
        return "set_target_properties(" + exeName + " PROPERTIES RUNTIME_OUTPUT_DIRECTORY " + outputDir + ")";
    }

    /** build target exeName according based on the configuration */
    private String buildExeName(String exeName) {
        String newExeName;
        /* %FILENAME% replacement */
        newExeName = exeName.replace(SingleFileExecutionConfig.EXECUTABLE_NAME_FILENAME, sourceFile.getNameWithoutExtension());
        return newExeName;
    }

    private String buildRuntimeOutputDirectory() {
        String newRuntimeOutputDirectory = config.getRuntimeOutputDirectory();
        /* source file's parent directory absolute path */
        //String sourceDir = new File(sourceFile.getPath()).getAbsoluteFile().getParentFile().getName();
        String sourceDirRelativePath = new File(Objects.requireNonNull(project.getBasePath())).toURI().relativize(
                new File(sourceFile.getPath()).getParentFile().toURI()).getPath();

        newRuntimeOutputDirectory = newRuntimeOutputDirectory.replace(SingleFileExecutionConfig.PROJECTDIR, "${PROJECT_SOURCE_DIR}");
        newRuntimeOutputDirectory = newRuntimeOutputDirectory.replace(SingleFileExecutionConfig.FILEDIR, "${CMAKE_CURRENT_SOURCE_DIR}/" + sourceDirRelativePath);
        return newRuntimeOutputDirectory;
    }

    private String quotingSourcePath(String path) {
        String quotedPath = path;
        if (path.contains(" ") || path.contains("(") || path.contains(")")) {
            quotedPath = '"' + quotedPath + '"';
        }
        return quotedPath;
    }

    private String quoteString(String str) {
        return '"' + str + '"';
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        final Project project = e.getData(CommonDataKeys.PROJECT);
        final Editor editor = e.getData(CommonDataKeys.EDITOR);

        e.getPresentation().setVisible((project != null && editor != null));
    }
}
