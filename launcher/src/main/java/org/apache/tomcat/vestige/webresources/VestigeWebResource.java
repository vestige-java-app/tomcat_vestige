package org.apache.tomcat.vestige.webresources;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.cert.Certificate;
import java.util.jar.Manifest;

import org.apache.catalina.webresources.AbstractResource;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

import fr.gaellalire.vestige.spi.resolver.VestigeJar;
import fr.gaellalire.vestige.spi.resolver.VestigeJarEntry;

public class VestigeWebResource extends AbstractResource {

    // AbstractArchiveResourceSet archiveResourceSet, String webAppPath, String
    // baseUrl, JarEntry jarEntry
    protected VestigeWebResource(VestigeJarResourceSet jarResourceSet, String webAppPath, String baseUrl, VestigeJar vestigeJar, VestigeJarEntry vestigeJarEntry) {
        super(jarResourceSet.getRootProtected(), webAppPath);

        this.baseUrl = baseUrl;
        this.vestigeJarEntry = vestigeJarEntry;
        this.vestigeJar = vestigeJar;

        String resourceName = vestigeJarEntry.getName();
        if (resourceName.charAt(resourceName.length() - 1) == '/') {
            resourceName = resourceName.substring(0, resourceName.length() - 1);
        }
        String internalPath = jarResourceSet.getInternalPathProtected();
        if (internalPath.length() > 0 && resourceName.equals(internalPath.subSequence(1, internalPath.length()))) {
            name = "";
        } else {
            int index = resourceName.lastIndexOf('/');
            if (index == -1) {
                name = resourceName;
            } else {
                name = resourceName.substring(index + 1);
            }
        }

    }

    private static final Log log = LogFactory.getLog(VestigeWebResource.class);

    private VestigeJar vestigeJar;

    private VestigeJarEntry vestigeJarEntry;

    private final String baseUrl;

    private String name;

    private Certificate[] certificates;

    private boolean readCerts = false;

    public VestigeJarEntry getVestigeJarEntry() {
        return vestigeJarEntry;
    }

    @Override
    public long getLastModified() {
        return vestigeJar.getLastModified();
    }

    @Override
    public boolean exists() {
        return true;
    }

    @Override
    public boolean isVirtual() {
        return false;
    }

    @Override
    public boolean isDirectory() {
        return vestigeJarEntry.isDirectory();
    }

    @Override
    public boolean isFile() {
        return !vestigeJarEntry.isDirectory();
    }

    @Override
    public boolean delete() {
        return false;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public long getContentLength() {
        return vestigeJarEntry.getSize();
    }

    @Override
    public String getCanonicalPath() {
        return null;
    }

    @Override
    public boolean canRead() {
        return true;
    }


    @Override
    public byte[] getContent() {
        long len = getContentLength();

        if (len > Integer.MAX_VALUE) {
            // Can't create an array that big
            throw new ArrayIndexOutOfBoundsException(sm.getString("abstractResource.getContentTooLarge", getWebappPath(), Long.valueOf(len)));
        }

        int size = (int) len;
        byte[] result = new byte[size];

        int pos = 0;
        try (InputStream jisw = vestigeJarEntry.open()) {
            if (jisw == null) {
                // An error occurred, don't return corrupted content
                return null;
            }
            while (pos < size) {
                int n = jisw.read(result, pos, size - pos);
                if (n < 0) {
                    break;
                }
                pos += n;
            }
            // Once the stream has been read, read the certs
            certificates = vestigeJarEntry.getCertificates();
            readCerts = true;
        } catch (IOException ioe) {
            if (getLog().isDebugEnabled()) {
                getLog().debug(sm.getString("abstractResource.getContentFail", getWebappPath()), ioe);
            }
            // Don't return corrupted content
            return null;
        }

        return result;
    }

    @Override
    public long getCreation() {
        return vestigeJarEntry.getModificationTime();
    }

    @Override
    public URL getURL() {
        // this URL will be used by servlet context
        String url = "jar:" + baseUrl + "!/" + vestigeJarEntry.getName();
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            if (getLog().isDebugEnabled()) {
                getLog().debug(sm.getString("fileResource.getUrlFail", url), e);
            }
            return null;
        }
    }

    @Override
    public URL getCodeBase() {
        return vestigeJar.getCodeBase();
    }

    @Override
    public Certificate[] getCertificates() {
        if (!readCerts) {
            // TODO - get content first
            throw new IllegalStateException();
        }
        return certificates;
    }

    @Override
    public Manifest getManifest() {
        try {
            return vestigeJar.getManifest();
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    protected InputStream doGetInputStream() {
        try {
            return vestigeJarEntry.open();
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    protected Log getLog() {
        return log;
    }

}
