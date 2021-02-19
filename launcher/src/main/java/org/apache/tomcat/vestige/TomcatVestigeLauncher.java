package org.apache.tomcat.vestige;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.Policy;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.MBeanServer;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

import org.apache.catalina.Globals;
import org.apache.catalina.TomcatControllerHandler;
import org.apache.catalina.loader.WebappLoader;
import org.apache.catalina.startup.Catalina;
import org.apache.catalina.webresources.TomcatURLStreamHandlerFactory;
import org.apache.tomcat.util.log.SystemLogHandler;
import org.apache.tomcat.util.modeler.BaseModelMBean;
import org.apache.tomcat.util.modeler.Registry;

import fr.gaellalire.vestige.spi.resolver.maven.VestigeMavenResolver;

/**
 * @author gaellalire
 */
public class TomcatVestigeLauncher implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(TomcatVestigeLauncher.class.getName());

    private VestigeMavenResolver mavenResolver;

    public void setMavenResolver(final VestigeMavenResolver mavenResolver) {
        this.mavenResolver = mavenResolver;
    }

    public void setVestigeSystem(final TomcatVestigeSystem vestigeSystem) {
        TomcatURLStreamHandlerFactory.disable();
        TomcatURLStreamHandlerFactory tomcatURLStreamHandlerFactory = TomcatURLStreamHandlerFactory.getInstance();
        URLStreamHandlerFactory urlStreamHandlerFactory = vestigeSystem.getURLStreamHandlerFactory();
        vestigeSystem.setURLStreamHandlerFactory(new URLStreamHandlerFactory() {

            @Override
            public URLStreamHandler createURLStreamHandler(String protocol) {
                URLStreamHandler urlStreamHandler = tomcatURLStreamHandlerFactory.createURLStreamHandler(protocol);
                if (urlStreamHandler != null) {
                    return urlStreamHandler;
                }
                if (urlStreamHandlerFactory != null) {
                    urlStreamHandler = urlStreamHandlerFactory.createURLStreamHandler(protocol);
                }
                return urlStreamHandler;
            }
        });
        vestigeSystem.setOut(new SystemLogHandler(vestigeSystem.getOut()));
        vestigeSystem.setErr(new SystemLogHandler(vestigeSystem.getErr()));
        if (System.getSecurityManager() != null) {
            // policy activated
            Policy policy = new Policy() {

                private Map<CodeSource, Permissions> permissionsByCodeSource = new HashMap<CodeSource, Permissions>();

                @Override
                public PermissionCollection getPermissions(final CodeSource codesource) {
                    Permissions permissions = permissionsByCodeSource.get(codesource);
                    if (permissions == null || permissions.isReadOnly()) {
                        permissions = new Permissions();
                        permissionsByCodeSource.put(codesource, permissions);
                    }
                    return permissions;
                }

                @Override
                public boolean implies(final ProtectionDomain domain, final Permission permission) {
                    // all permissions
                    return true;
                }

            };
            vestigeSystem.setPolicy(policy);
        }
    }

    public TomcatVestigeLauncher(final File base, final File data) {
        // avoid call to URL.setURLStreamHandlerFactory
        try {
            Field declaredField = WebappLoader.class.getDeclaredField("first");
            declaredField.setAccessible(true);
            declaredField.set(null, false);
            declaredField.setAccessible(false);
        } catch (Exception e) {
        }
        System.setProperty(Globals.CATALINA_BASE_PROP, base.getPath());
        System.setProperty(Globals.CATALINA_HOME_PROP, base.getPath());
        System.setProperty("com.sun.xml.bind.v2.bytecode.ClassTailor.noOptimize", "true");
    }

    private volatile boolean started = false;

    public void run() {
        TomcatControllerHandler.setTomcatController(new VestigeTomcatController());
        VestigeWar.init(mavenResolver);

        final Catalina catalina = new Catalina() {
            @Override
            protected void initStreams() {
                // system.out & system.err are already hooked
            }
        };
        catalina.setUseShutdownHook(false);
        catalina.setParentClassLoader(TomcatVestigeLauncher.class.getClassLoader());

        Thread launcherThread = new Thread() {
            @Override
            public void run() {
                catalina.start();
                started = true;
            }
        };
        launcherThread.start();
        try {
            synchronized (this) {
                wait();
            }
        } catch (InterruptedException e) {
            while (true) {
                try {
                    launcherThread.join();
                    break;
                } catch (InterruptedException e1) {
                    LOGGER.log(Level.FINE, "Ignore interrupt", e1);
                }
            }
            if (started) {
                catalina.stop();
            }
            List<ObjectName> toRemove = new ArrayList<ObjectName>();
            MBeanServer mBeanServer = Registry.getRegistry(null, null).getMBeanServer();
            for (ObjectInstance o : mBeanServer.queryMBeans(null, null)) {
                if (o.getClassName().equals(BaseModelMBean.class.getName())) {
                    toRemove.add(o.getObjectName());
                }
            }
            for (ObjectName name : toRemove) {
                try {
                    mBeanServer.unregisterMBean(name);
                } catch (Exception e1) {
                    e1.printStackTrace();
                }
            }

            Thread currentThread = Thread.currentThread();
            ThreadGroup threadGroup = currentThread.getThreadGroup();
            int activeCount = threadGroup.activeCount();
            while (activeCount != 1) {
                Thread[] list = new Thread[activeCount];
                int enumerate = threadGroup.enumerate(list);
                for (int i = 0; i < enumerate; i++) {
                    Thread t = list[i];
                    if (t == currentThread) {
                        continue;
                    }
                    t.interrupt();
                }
                for (int i = 0; i < enumerate; i++) {
                    Thread t = list[i];
                    if (t == currentThread) {
                        continue;
                    }
                    try {
                        t.join();
                    } catch (InterruptedException e1) {
                        LOGGER.log(Level.FINE, "Interrupted", e1);
                        break;
                    }
                }
                activeCount = threadGroup.activeCount();
            }

            // StatusManagerServlet.destroy should call
            // mBeanServer.removeNotificationListener
        } finally {
            started = false;
        }
    }

}
