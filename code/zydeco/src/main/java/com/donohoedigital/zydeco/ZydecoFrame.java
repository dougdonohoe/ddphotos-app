package com.donohoedigital.zydeco;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.File;

public class ZydecoFrame extends JFrame
{

    public ZydecoFrame()
    {
        super("Zydeco");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 650);
        setLocationRelativeTo(null);

        setJMenuBar(buildMenuBar());
        setContentPane(new FileBrowserPanel());
    }

    private JMenuBar buildMenuBar()
    {
        JMenuBar menuBar = new JMenuBar();
        menuBar.add(buildFileMenu());
        menuBar.add(buildEditMenu());
        menuBar.add(buildViewMenu());
        menuBar.add(buildHelpMenu());
        return menuBar;
    }

    private JMenu buildFileMenu()
    {
        JMenu menu = new JMenu("File");
        menu.setMnemonic(KeyEvent.VK_F);

        JMenuItem newWindow = new JMenuItem("New Window");
        newWindow.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        newWindow.addActionListener(_ -> {
            ZydecoFrame frame = new ZydecoFrame();
            frame.setVisible(true);
        });
        menu.add(newWindow);

        JMenuItem newAlbum = new JMenuItem("New Album…");
        newAlbum.addActionListener(_ -> {
            NewAlbumDialog dialog = new NewAlbumDialog(this, null);
            dialog.setVisible(true);
            AlbumSettings settings = dialog.getResult();
            if (settings != null) {
                System.out.println(settings);
            }
        });
        menu.add(newAlbum);

        menu.addSeparator();

        JMenuItem openFolder = new JMenuItem("Open Folder…");
        openFolder.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        openFolder.addActionListener(_ -> openFolder());
        menu.add(openFolder);

        menu.addSeparator();

        JMenuItem close = new JMenuItem("Close Window");
        close.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        close.addActionListener(_ -> dispose());
        menu.add(close);

        menu.addSeparator();

        JMenuItem quit = new JMenuItem("Quit Zydeco");
        quit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        quit.addActionListener(_ -> System.exit(0));
        menu.add(quit);

        return menu;
    }

    private JMenu buildEditMenu()
    {
        JMenu menu = new JMenu("Edit");
        menu.setMnemonic(KeyEvent.VK_E);

        int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

        JMenuItem cut = new JMenuItem("Cut");
        cut.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, mask));
        cut.setEnabled(false);
        menu.add(cut);

        JMenuItem copy = new JMenuItem("Copy");
        copy.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, mask));
        copy.setEnabled(false);
        menu.add(copy);

        JMenuItem paste = new JMenuItem("Paste");
        paste.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, mask));
        paste.setEnabled(false);
        menu.add(paste);

        menu.addSeparator();

        JMenuItem selectAll = new JMenuItem("Select All");
        selectAll.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, mask));
        selectAll.setEnabled(false);
        menu.add(selectAll);

        return menu;
    }

    private JMenu buildViewMenu()
    {
        JMenu menu = new JMenu("View");
        menu.setMnemonic(KeyEvent.VK_V);

        JCheckBoxMenuItem darkModeItem = new JCheckBoxMenuItem("Dark Mode");
        darkModeItem.addActionListener(_ -> toggleDarkMode(darkModeItem.isSelected()));
        menu.add(darkModeItem);

        return menu;
    }

    private JMenu buildHelpMenu()
    {
        JMenu menu = new JMenu("Help");
        menu.setMnemonic(KeyEvent.VK_H);

        JMenuItem about = new JMenuItem("About Zydeco");
        about.addActionListener(_ -> JOptionPane.showMessageDialog(
                this,
                "Zydeco POC",
                "About Zydeco",
                JOptionPane.INFORMATION_MESSAGE));
        menu.add(about);

        return menu;
    }

    private void openFolder()
    {
        if (isMac()) {
            openWithFileDialog();
        } else {
            openWithJFileChooser();
        }
    }

    private void openWithFileDialog()
    {
        System.setProperty("apple.awt.fileDialogForDirectories", "true");
        FileDialog dialog = new FileDialog(this, "Choose a Folder", FileDialog.LOAD);
        dialog.setDirectory(System.getProperty("user.home"));
        dialog.setVisible(true);
        System.setProperty("apple.awt.fileDialogForDirectories", "false");

        String dir  = dialog.getDirectory();
        String file = dialog.getFile();
        if (file != null) {
            showThanksDialog(new File(dir, file));
        }
    }

    private void openWithJFileChooser()
    {
        JFileChooser chooser = new JFileChooser(System.getProperty("user.home"));
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Choose a Folder");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            showThanksDialog(chooser.getSelectedFile());
        }
    }

    private static boolean isMac()
    {
        return System.getProperty("os.name", "").toLowerCase().contains("mac");
    }

    private void showThanksDialog(File dir)
    {
        JOptionPane.showMessageDialog(this,
                "Thanks for choosing " + dir.getAbsolutePath() + "!",
                "Folder Selected",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void toggleDarkMode(boolean dark)
    {
        try {
            if (dark) {
                FlatDarkLaf.setup();
            } else {
                FlatLightLaf.setup();
            }
            SwingUtilities.updateComponentTreeUI(this);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
