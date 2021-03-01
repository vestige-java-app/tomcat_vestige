FROM gaellalire/vestige:10.1.0-alpine-openjdk11
RUN export VESTIGE_INSTALL_URL=https://gaellalire.fr/vestige/repository/tomcat/tomcat-10.0.2.xml \
 && export VESTIGE_STOP_AFTER_START=true \
 && export VESTIGE_INSTALL_M2_REPO=/tmp/m2Repository \
 && export VESTIGE_INSTALL_LOCAL_NAME=tomcat \
 && export VESTIGE_START_INTERRUPTED=true \
 && /opt/vestige/vestige \
 && rm -rf /tmp/m2Repository
CMD /opt/vestige/vestige
