FROM tomcat:8-jre7

ENV EMAPI_DATA_DIR=/var/lib/emapi
ENV TOMCAT_DBCP_URL http://central.maven.org/maven2/org/apache/tomcat/tomcat-dbcp/7.0.30/tomcat-dbcp-7.0.30.jar

ADD $TOMCAT_DBCP_URL $CATALINA_HOME/lib/tomcat-dbcp-7.0.30.jar
COPY api-rest-service/target/em-api.war $CATALINA_HOME/webapps/.

VOLUME $EMAPI_DATA_DIR
