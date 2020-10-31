package org.apache.tomcat.vestige;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

import org.apache.catalina.ClassloaderController;
import org.apache.catalina.Host;
import org.apache.catalina.TomcatController;
import org.apache.catalina.WebResource;
import org.apache.catalina.WebResourceRoot.ResourceSetType;
import org.apache.catalina.WebResourceSet;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.HostConfig;
import org.apache.catalina.startup.HostConfig.DeployWar;
import org.apache.catalina.startup.HostConfig.DeployedApplication;
import org.apache.catalina.util.ContextName;
import org.apache.catalina.webresources.StandardRoot;
import org.apache.tomcat.vestige.webresources.VestigeJarEntryFromVestigeJar;
import org.apache.tomcat.vestige.webresources.VestigeJarResourceSet;
import org.apache.tomcat.vestige.webresources.VestigeWebResource;

import fr.gaellalire.vestige.spi.resolver.VestigeJar;
import fr.gaellalire.vestige.spi.resolver.VestigeJarEntry;

public class VestigeTomcatController implements TomcatController {

	private static Pattern VWAR_PATTERN = Pattern.compile("(?i:(.*)\\.(vwar))");
	
	@Override
	public boolean specificFixDocBase(String originalDocBase) {
		return originalDocBase.toLowerCase(Locale.ENGLISH).endsWith(".vwar");
	}
		
	@Override
	public WebResourceSet createMainResourceSet(StandardRoot caller, File f, String docBase, String baseName) {
		if (f.isFile() && docBase.endsWith(".vwar")) {
		    VestigeWar vestigeWar = VestigeWar.create(f, baseName);
		    return new VestigeJarResourceSet(caller, "/", vestigeWar.getVestigeWar(), vestigeWar.getVestigeWarDependencies(), "/");
		}
		return null;
	}
	
	@Override
	public void deploySpecificApp(HostConfig caller, Host host, Set<String> invalidWars, Map<String,DeployedApplication> deployed, File appBase, String[] files) {
        if (files == null)
            return;
        
        ExecutorService es = host.getStartStopExecutor();
        List<Future<?>> results = new ArrayList<>();

        for (int i = 0; i < files.length; i++) {

            if (files[i].equalsIgnoreCase("META-INF"))
                continue;
            if (files[i].equalsIgnoreCase("WEB-INF"))
                continue;
            File war = new File(appBase, files[i]);
            if (files[i].toLowerCase(Locale.ENGLISH).endsWith(".vwar") && war.isFile() && !invalidWars.contains(files[i])) {

                ContextName cn = new ContextName(files[i], true);

                if (caller.isServiced(cn.getName())) {
                    continue;
                }
                if (caller.deploymentExists(cn.getName())) {
                    DeployedApplication app = deployed.get(cn.getName());
                    
                    boolean unpackWAR = caller.isUnpackWARs();
                    if (unpackWAR && host.findChild(cn.getName()) instanceof StandardContext) {
                        unpackWAR = ((StandardContext) host.findChild(cn.getName())).getUnpackWAR();
                    }
                    if (!unpackWAR && app != null) {
                        // Need to check for a directory that should not be
                        // there
                        File dir = new File(appBase, cn.getBaseName());
                        if (dir.exists()) {
                            if (!app.loggedDirWarning) {
                                // log.warn(sm.getString("hostConfig.deployWar.hiddenDir", dir.getAbsoluteFile(), war.getAbsoluteFile()));
                                app.loggedDirWarning = true;
                            }
                        } else {
                            app.loggedDirWarning = false;
                        }
                    }
                    continue;
                }

                // Check for WARs with /../ /./ or similar sequences in the name
                if (!caller.validateContextPath(appBase, cn.getBaseName())) {
                    // log.error(sm.getString("hostConfig.illegalWarName", files[i]));
                    invalidWars.add(files[i]);
                    continue;
                }
                cn.setExtension(".vwar");

                results.add(es.submit(new DeployWar(caller, cn, war)));
            }
        }

        for (Future<?> result : results) {
            try {
                result.get();
            } catch (Exception e) {
                // log.error(sm.getString("hostConfig.deployWar.threaded.error"), e);
            }
        }
		
	}

	@Override
	public ClassloaderController processWebInfLib(StandardRoot caller, WebResource possibleJar, List<WebResourceSet> classResources) {
        if (possibleJar instanceof VestigeWebResource) {
            VestigeJarEntry vestigeJarEntry = ((VestigeWebResource) possibleJar).getVestigeJarEntry();
            if (vestigeJarEntry instanceof VestigeJarEntryFromVestigeJar) {
                VestigeJarEntryFromVestigeJar vestigeJarEntryFromVestigeJar = (VestigeJarEntryFromVestigeJar) vestigeJarEntry;
                classResources.add(new VestigeJarResourceSet(caller, "/WEB-INF/classes", vestigeJarEntryFromVestigeJar.getVestigeJar(), Collections.<VestigeJar>emptyList(),  "/"));
            } else {
            	caller.createWebResourceSet(ResourceSetType.CLASSES_JAR, "/WEB-INF/classes", possibleJar.getURL(), "/");
            }
        } else {
        	caller.createWebResourceSet(ResourceSetType.CLASSES_JAR, "/WEB-INF/classes", possibleJar.getURL(), "/");
        }
        return new ClassloaderController() {
			
			@Override
			public void unload() {
			}
			
			@Override
			public ClassLoader load() {
				return null;
			}
		};
	}

	@Override
	public Pattern getSpecificExtensions() {
		return VWAR_PATTERN;
	}


}
