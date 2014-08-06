===========================
Running the client examples
===========================

Use maven to build the module, and additionally copy the dependencies
alongside their output:

  mvn clean package dependency:copy-dependencies -DincludeScope=runtime -DskipTests

Now you can run the examples using commands of the format:

  java -cp "target/classes/:target/dependency/*" org.apache.qpid.amqp_1_0.jms.example.Hello

NOTE: This example requires that you first create a queue named "queue" on your broker.


NOTE: The earlier build command will cause Maven to resolve the client artifact
dependencies against its local and remote repositories. If you wish to use a
locally-built client, ensure to install it in your local maven repo first.
