package cz.vity.freerapid.utilities;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Help System utility to open files
 *
 * @author Vity
 */
public class OSDesktop {
    private final static Logger logger = Logger.getLogger(OSDesktop.class.getName());

    /**
     * Opens given file/directory on local operating system
     *
     * @param file file to open with local application
     * @see Desktop#open(java.io.File)
     */
    public static void openFile(File file) {
        if (!file.exists())
            return;
        if (Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().open(file);
            } catch (IOException e) {
                LogUtils.processException(logger, e);
            }
        }
    }

    /**
     * This function is similar to <code>openFile</code> function, but it tries to also select given file in the folder<br/>
     * File has to exist on the file system.
     *
     * @param file file with folder to open with local application
     */
    public static void openDirectoryForFile(File file) {
        if (!file.exists())
            return;

        if (Utils.isWindows()) {
            try {
                Runtime.getRuntime().exec("explorer.exe /select,\"" + file.getAbsolutePath() + '\"');
            } catch (IOException e) {
                LogUtils.processException(logger, e);
            }
        } else {
            openFile(file.getParentFile());
        }
    }
}
