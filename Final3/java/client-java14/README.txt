An implementation of SASL is provided here, for the PLAIN and CRAM-MD5 mechanisms as well as maven assembly
instructions for producing a backported Java client for Java 1.4. In order to use the custom SASL implementation
on JRE 1.4 the following steps must be taken:

 * Install the SASL JSR-28 API jar.
 * Install the qpid-client-java14 jar or set it up as a dynamically registered SASL provider.
 * Set up java.security to add the SASL provider to the list of security providers if hte SASL implemenation jar was installed as an extension.

Installing the SASL JSR-28 API jar.

 Download, http://www.worldspot.com/jsr28/v1.1/download/sasl.jar, and copy it to the JAVA_HOME\lib\ext directory.

Install or set up the qpid-client-java14 jar.

 Copy the output jar for this project, qpid-client-java14-1.0-incubating-M2-SNAPSHOT.jar, to JAVA_HOME\lib\ext. 
 
 OR
 
 Create a properties file and register the SASL implementations in the jar so that Qpids dynamic SASL registry can find them. In a properties file
 add the lines:

  PLAIN=org.apache.qpid.sasl.ClientFactoryImpl
  CRAM-MD5=org.apache.qpid.sasl.ClientFactoryImpl

 Place this somewhere on the classpath and put the qpid-client-java14-1.0-incubating-M2-SNAPSHOT.jar on the classpath too. When starting your application
 pass in the system property amq.dynamicsaslregistrar.properties and set it to point to the location of the properties file.

Set up the SASL provider.

 You only need to do this if the custom SASL jar, qpid-client-java14-1.0-incubating-M2-SNAPSHOT.jar, was copied to JAVA_HOME\lib\ext. 
 Add the following line to JAVA_HOME\lib\security\java.security file (where n is the providers preference order):

 security.provider.n=org.apache.qpid.sasl.Provider