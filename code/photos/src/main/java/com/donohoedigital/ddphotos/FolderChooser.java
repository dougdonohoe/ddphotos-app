package com.donohoedigital.ddphotos;

import com.donohoedigital.base.Utils;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class FolderChooser {

    public static String pickFolder(Frame frame, String startDir) {
        if (startDir == null || startDir.isBlank()) startDir = System.getProperty("user.home");
        File resolved = existingAncestor(startDir);

        if (Utils.ISMAC) {
            System.setProperty("apple.awt.fileDialogForDirectories", "true");
            FileDialog fd = new FileDialog(frame, "Choose a Folder", FileDialog.LOAD);
            fd.setDirectory(resolved.getAbsolutePath());
            fd.setVisible(true);
            System.setProperty("apple.awt.fileDialogForDirectories", "false");
            String dir  = fd.getDirectory();
            String file = fd.getFile();
            return file != null ? new File(dir, file).getAbsolutePath() : null;
        } else {
            JFileChooser fc = new JFileChooser(resolved);
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fc.setDialogTitle("Choose a Folder");
            return fc.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION
                    ? fc.getSelectedFile().getAbsolutePath() : null;
        }
    }

    private static File existingAncestor(String path) {
        File dir = new File(path);
        while (dir != null && !dir.exists()) {
            dir = dir.getParentFile();
        }
        return dir != null ? dir : new File(System.getProperty("user.home"));
    }
}
