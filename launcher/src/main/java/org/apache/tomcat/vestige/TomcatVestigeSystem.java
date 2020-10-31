package org.apache.tomcat.vestige;

import java.io.PrintStream;
import java.net.URLStreamHandlerFactory;
import java.security.Policy;

/**
 * @author gaellalire
 */
public interface TomcatVestigeSystem {

    URLStreamHandlerFactory getURLStreamHandlerFactory();

    void setURLStreamHandlerFactory(URLStreamHandlerFactory urlStreamHandlerFactory);

    void setOut(PrintStream out);

    void setErr(PrintStream err);

    PrintStream getOut();

    PrintStream getErr();

    void setPolicy(Policy policy);

}
