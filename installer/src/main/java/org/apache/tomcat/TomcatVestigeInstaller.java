package org.apache.tomcat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.IOUtils;

/**
 * @author gaellalire
 */
public class TomcatVestigeInstaller {

    private File base;

    public TomcatVestigeInstaller(final File base) {
        this.base = base;
    }

    public void install() throws Exception {
        ZipInputStream zipFile = new ZipInputStream(TomcatVestigeInstaller.class.getResourceAsStream("/home.zip"));
        ZipEntry entry = zipFile.getNextEntry();
        while (entry != null) {
            File entryDestination = new File(base, entry.getName());
            if (entry.isDirectory()) {
                entryDestination.mkdirs();
            } else {
                entryDestination.getParentFile().mkdirs();
                OutputStream out = new FileOutputStream(entryDestination);
                IOUtils.copy(zipFile, out);
                IOUtils.closeQuietly(out);
            }
            zipFile.closeEntry();
            entry = zipFile.getNextEntry();
        }
        File entryDestination = new File(base, "webapps/mywar.vwar");
        OutputStream out = new FileOutputStream(entryDestination);
        IOUtils.copy(TomcatVestigeInstaller.class.getResourceAsStream("/mywar.vwar"), out);
        IOUtils.closeQuietly(out);
    }

}
