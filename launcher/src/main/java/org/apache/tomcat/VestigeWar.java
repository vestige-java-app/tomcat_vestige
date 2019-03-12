package org.apache.tomcat;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import fr.gaellalire.vestige.spi.job.DummyJobHelper;
import fr.gaellalire.vestige.spi.resolver.ResolvedClassLoaderConfiguration;
import fr.gaellalire.vestige.spi.resolver.ResolverException;
import fr.gaellalire.vestige.spi.resolver.Scope;
import fr.gaellalire.vestige.spi.resolver.VestigeJar;
import fr.gaellalire.vestige.spi.resolver.maven.MavenContext;
import fr.gaellalire.vestige.spi.resolver.maven.MavenContextBuilder;
import fr.gaellalire.vestige.spi.resolver.maven.ResolveMavenArtifactRequest;
import fr.gaellalire.vestige.spi.resolver.maven.ResolveMode;
import fr.gaellalire.vestige.spi.resolver.maven.VestigeMavenResolver;
import fr.gaellalire.vestige.vwar.AdditionalRepository;
import fr.gaellalire.vestige.vwar.Application;
import fr.gaellalire.vestige.vwar.Config;
import fr.gaellalire.vestige.vwar.MavenClassType;
import fr.gaellalire.vestige.vwar.MavenConfig;
import fr.gaellalire.vestige.vwar.ObjectFactory;

public class VestigeWar {

    private static ThreadLocal<VestigeMavenResolver> mavenResolver = new InheritableThreadLocal<>();

    public static void init(VestigeMavenResolver mavenResolver) {
        VestigeWar.mavenResolver.set(mavenResolver);
    }

    private ResolvedClassLoaderConfiguration classLoaderConfiguration;

    public static VestigeWar create(File vestigeWar, String baseName) {
        Unmarshaller unMarshaller = null;
        try {
            JAXBContext jc = JAXBContext.newInstance(ObjectFactory.class.getPackage().getName());
            unMarshaller = jc.createUnmarshaller();

            URL xsdURL = VestigeWar.class.getResource("vwar-1.0.0.xsd");
            SchemaFactory schemaFactory = SchemaFactory.newInstance("http://www.w3.org/2001/XMLSchema");
            Schema schema = schemaFactory.newSchema(xsdURL);
            unMarshaller.setSchema(schema);
        } catch (Exception e) {
            throw new RuntimeException("Unable to initialize settings parser", e);
        }
        try {
            FileInputStream inputStream = new FileInputStream(vestigeWar);
            try {
                @SuppressWarnings("unchecked")
                Application value = ((JAXBElement<Application>) unMarshaller.unmarshal(inputStream)).getValue();

                MavenContextBuilder mavenContextBuilder = mavenResolver.get().createMavenContextBuilder();
                MavenClassType mavenResolver = value.getLauncher().getMavenResolver();
                Config configurations = value.getConfigurations();
                if (configurations != null) {
                    MavenConfig mavenConfig = configurations.getMavenConfig();
                    if (mavenConfig != null) {
                        List<Object> modifyDependencyOrReplaceDependencyOrAdditionalRepository = mavenConfig.getModifyDependencyOrReplaceDependencyOrAdditionalRepository();
                        for (Object object : modifyDependencyOrReplaceDependencyOrAdditionalRepository) {
                            if (object instanceof AdditionalRepository) {
                                AdditionalRepository additionalRepository = (AdditionalRepository) object;
                                mavenContextBuilder.addAdditionalRepository(additionalRepository.getId(), additionalRepository.getLayout(), additionalRepository.getUrl());
                            }
                        }
                    }
                }

                MavenContext build = mavenContextBuilder.build();
                ResolveMavenArtifactRequest request = build.resolve(ResolveMode.FIXED_DEPENDENCIES, Scope.PLATFORM, mavenResolver.getGroupId(), mavenResolver.getArtifactId(),
                        mavenResolver.getVersion(), "war", "webapp-" + baseName);
                ResolvedClassLoaderConfiguration classLoaderConfiguration;
                try {
                    classLoaderConfiguration = request.execute(DummyJobHelper.INSTANCE);
                } catch (ResolverException e) {
                    throw new RuntimeException("Unable to fetch war", e);
                }
                return new VestigeWar(classLoaderConfiguration);
            } finally {
                inputStream.close();
            }
        } catch (JAXBException e) {
            throw new RuntimeException("Unable to read vwar file", e);
        } catch (IOException e) {
            throw new RuntimeException("Unable to read vwar file", e);
        }
    }

    public VestigeWar(ResolvedClassLoaderConfiguration classLoaderConfiguration) {
        this.classLoaderConfiguration = classLoaderConfiguration;
    }

    public VestigeJar getFirstVestigeJar() {
        return classLoaderConfiguration.getFirstVestigeJar();
    }

}
