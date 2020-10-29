package org.apache.tomcat.vestige.webresources;

import java.io.IOException;
import java.io.InputStream;
import java.security.cert.Certificate;

import fr.gaellalire.vestige.spi.resolver.VestigeJar;
import fr.gaellalire.vestige.spi.resolver.VestigeJarEntry;

public class VestigeJarEntryFromVestigeJar implements VestigeJarEntry {

    private VestigeJar vestigeJar;

    public VestigeJarEntryFromVestigeJar(VestigeJar vestigeJar) {
        this.vestigeJar = vestigeJar;
    }

    @Override
    public long getSize() {
        return vestigeJar.getSize();
    }

    @Override
    public InputStream open() throws IOException {
        return vestigeJar.open();
    }

    @Override
    public boolean isDirectory() {
        return false;
    }

    @Override
    public long getModificationTime() {
        return vestigeJar.getLastModified();
    }

    @Override
    public String getName() {
        return vestigeJar.getName();
    }

    public VestigeJar getVestigeJar() {
        return vestigeJar;
    }

    @Override
    public Certificate[] getCertificates() {
        return null;
    }

}
