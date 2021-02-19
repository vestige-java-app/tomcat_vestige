module org.apache.tomcat.vestige {

    exports org.apache.tomcat.vestige;

    exports org.apache.tomcat.vestige.webresources;

    requires transitive org.apache.tomcat.catalina;
    
    requires fr.gaellalire.vestige.spi.resolver;
    
    requires transitive fr.gaellalire.vestige.spi.resolver.maven;
    
    requires org.apache.tomcat.jasper;
    
    requires java.xml.bind;
        
    opens org.apache.tomcat.vwar to java.xml.bind;

}
