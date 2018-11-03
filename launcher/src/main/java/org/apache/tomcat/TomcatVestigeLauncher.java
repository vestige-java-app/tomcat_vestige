package org.apache.tomcat;

import java.io.File;
import java.lang.reflect.Field;
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
import org.apache.catalina.loader.WebappLoader;
import org.apache.catalina.startup.Catalina;
import org.apache.catalina.webresources.TomcatURLStreamHandlerFactory;
import org.apache.tomcat.util.log.SystemLogHandler;
import org.apache.tomcat.util.modeler.BaseModelMBean;
import org.apache.tomcat.util.modeler.Registry;

/**
 * @author gaellalire
 */
public class TomcatVestigeLauncher implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(TomcatVestigeLauncher.class.getName());

    public void setVestigeSystem(final TomcatVestigeSystem vestigeSystem) {
        TomcatURLStreamHandlerFactory.disable();
        vestigeSystem.setURLStreamHandlerFactory(TomcatURLStreamHandlerFactory.getInstance());
        vestigeSystem.setOut(new SystemLogHandler(vestigeSystem.getOut()));
        vestigeSystem.setErr(new SystemLogHandler(vestigeSystem.getErr()));
        if (System.getSecurityManager() != null) {
            // policy activated
            vestigeSystem.setPolicy(new Policy() {

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

            });
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
    }

    private volatile boolean started = false;

    public void run() {

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

            // StatusManagerServlet.destroy should call mBeanServer.removeNotificationListener
        } finally {
            started = false;
        }
    }

}
