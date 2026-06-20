package com.donohoedigital.installer;

import com.donohoedigital.base.Utils;
import com.donohoedigital.config.DefaultRuntimeDirectory;
import com.donohoedigital.config.Prefs;
import com.install4j.api.actions.UninstallAction;
import com.install4j.api.context.Context;
import com.install4j.api.context.ProgressInterface;
import com.install4j.api.context.UninstallerContext;
import com.install4j.api.context.UserCanceledException;

import java.io.File;

/**
 * Install4j cleanup action to remove local config files, logs and prefs
 */
@SuppressWarnings({"CallToPrintStackTrace", "UseOfSystemOutOrSystemErr", "unused"})
public class FileCleanup implements UninstallAction {

    // app and version need to match PhotosConstants (copied here to avoid pulling in entire tree in Install4j)
    private static final String APP_NAME = "ddphotos";
    private static final String MAJOR_VERSION = "1";
    private static final boolean DEBUG = false;

    public void init(Context context) {
    }

    @SuppressWarnings("RedundantThrows")
    public boolean uninstall(UninstallerContext uninstallerContext) throws UserCanceledException {
        ProgressInterface progressInterface = uninstallerContext.getProgressInterface();
        Prefs.setRootNodeName(APP_NAME + MAJOR_VERSION);

        try {
            if (uninstallerContext.getBooleanVariable("deleteFiles")) {
                progressInterface.setStatusMessage("Deleting user-specific files...");
                deleteDir(new DefaultRuntimeDirectory().getClientHome(APP_NAME), progressInterface);
                Utils.sleepSeconds(1);
            }

            progressInterface.setPercentCompleted(50);

            if (uninstallerContext.getBooleanVariable("deletePrefs")) {
                progressInterface.setStatusMessage("Removing preferences...");
                Prefs.clearAll();
                Utils.sleepSeconds(1);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }

        progressInterface.setPercentCompleted(100);

        return true;
    }

    /**
     * Delete a directory and all of its contents
     */
    private void deleteDir(File dir, ProgressInterface progressInterface) {
        File[] files = dir.listFiles();
        File file;

        progressInterface.setDetailMessage(dir.getAbsolutePath());
        for (int i = 0; files != null && i < files.length; i++) {
            file = files[i];
            if (file.isDirectory()) {
                // skip jre dir - handled by install4j
                if (file.getName().equals("jre") || file.getName().equalsIgnoreCase(".install4j")) continue;

                // recurse through subdirectory
                deleteDir(file, progressInterface);
            } else {
                // delete a file
                if (DEBUG) System.err.println("Delete file " + file.getAbsolutePath());
                progressInterface.setDetailMessage(file.getName());
                if (!file.delete()) {
                    if (DEBUG) System.err.println("Unable to delete file: " + file.getAbsolutePath());
                }
            }
        }

        // delete the directory
        if (DEBUG) System.err.println("Delete directory " + dir.getAbsolutePath());
        if (!dir.delete()) {
            if (DEBUG) System.err.println("Unable to delete directory: " + dir.getAbsolutePath());
        }
    }
}
