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

import java.io.File;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 */
public class SingleFileExecutionAction extends AnAction {
    private static final Logger LOG = Logger.getInstance(SingleFileExecutionAction.class.getSimpleName());

    public static final int EXE_NOT_EXIST = 0;
    public static final int EXE_EXIST_SAME_SOURCE = 1;
    public static final int EXE_EXIST_DIFFERENT_SOURCE = 2;

    @Override
    public void actionPerformed(AnActionEvent e) {

        //Get all the required data from data keys
        final Editor editor = e.getRequiredData(CommonDataKeys.EDITOR);
        final Project project = e.getRequiredData(CommonDataKeys.PROJECT);
        final SingleFileExecutionConfig config = SingleFileExecutionConfig.getInstance(project);
        String cmakelistFilePath = project.getBasePath() + "/CMakeLists.txt";

        //Access document, caret, and selection
        final Document document = editor.getDocument();
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

        VirtualFile vFile = e.getData(PlatformDataKeys.VIRTUAL_FILE);  // get source file (* currently selected file in editor)
        //vFile.getCanonicalPath();   // source file path (absolute path)
        //vFile.getPath();            // source file path (absolute path)

        String fileName = vFile != null ? vFile.getName() : null;  // source file name (but not include path)

        String exeName = config.getExecutableName().replace("%FILENAME%", vFile.getNameWithoutExtension());
        String sourceName = fileName;
        String relativeSourcePath = new File(project.getBasePath()).toURI().relativize(new File(vFile.getPath()).toURI()).getPath();

        /* parse cmakelistDocument to check existence of exe_name */
        /* See http://mmasashi.hatenablog.com/entry/20091129/1259511129 for lazy, greedy search */
        String regex = "^add_executable\\s*?\\(\\s*?" + exeName + "\\s+(((\\S+)\\s+)*\\S+)\\s*\\)";

        Pattern pattern = Pattern.compile(regex);

        Scanner scanner = new Scanner(cmakelistDocument.getText());
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
                        new Notification("singlefileexecutionaction", "Single File Execution Plugin", "add_executable added for " + sourceName + ".", NotificationType.INFORMATION)
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
                } else {
                    // cancel
                    // do nothing so far
                }
                break;
        }

    }

    private void insertAddExecutable(final Document cmakelistDocument, final String exeName, final String relativeSourcePath) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
                cmakelistDocument.setText(cmakelistDocument.getText() + "\nadd_executable("+ exeName + " " + quotingSourcePath(relativeSourcePath) +")");
            }
        });
    }

    private void updateAddExecutable(final Document cmakelistDocument, final String exeName, final String relativeSourcePath) {
        String updatedDocument = "";

        /*
         * This regular expression finds
         * "add_executable(XXXX YYYY.cpp ZZZZ.cpp)" where XXXX is executable name, YYYY.cpp and ZZZZ.cpp are the source files.
         */
        String regex = "^add_executable\\s*?\\(\\s*?" + exeName + "\\s+(((\\S+)\\s+)*\\S+)\\s*\\)";
        Pattern pattern = Pattern.compile(regex);
        Scanner scanner = new Scanner(cmakelistDocument.getText());

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();

            Matcher m = pattern.matcher(line);
            if (m.find()) {
                line = m.replaceFirst("add_executable("+ exeName + " " + quotingSourcePath(relativeSourcePath) +")");
            }
            updatedDocument += line + '\n';
        }
        scanner.close();
        final String updatedText = updatedDocument;
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
                cmakelistDocument.setText(updatedText);
            }
        });
    }

    private String quotingSourcePath(String path) {
        String quotedPath = path;
        if (path.contains(" ") || path.contains("(") || path.contains(")")) {
            quotedPath = '"' + quotedPath + '"';
        }
        return quotedPath;
    }

    @Override
    public void update(AnActionEvent e) {
        final Project project = e.getData(CommonDataKeys.PROJECT);
        final Editor editor = e.getData(CommonDataKeys.EDITOR);

        e.getPresentation().setVisible((project != null && editor != null));
    }
}
