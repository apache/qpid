Documentation for the Qpid components can be accessed on our website at:

http://qpid.apache.org/documentation.html

Some initial helper info can be found below.


==== Building the code and running the tests ====

Here are some example Maven build commands that you may find usefull.

Clean previous builds output and install all modules to local repository without
running any of the unit or system tests.

  mvn clean install -DskipTests

Clean previous builds output and installs all modules to the local repository
after running all the tests using the Java BDB 0-10 profile

  mvn clean install -Pjava-bdb.0-10

Perform a subset of the QA (int or sys tests) on the packaged release artifacts

  mvn verify -Dtest=TestNamePattern* -DfailIfNoTests=false

Execute the unit tests and then produce the code coverage report

  mvn test jacoco:report

For more details on how to build see:
https://cwiki.apache.org/confluence/display/qpid/Qpid+Java+Build+How+To


==== Running client examples =====

Use maven to build the modules, and additionally copy the dependencies alongside their output:

  mvn clean package dependency:copy-dependencies -DskipTests

Now you can then run the examples as follows:

  java -cp "client/example/target/classes/:client/example/target/dependency/*" org.apache.qpid.example.Drain

  java -cp "amqp-1-0-client-jms/example/target/classes/:amqp-1-0-client-jms/example/target/dependency/*" org.apache.qpid.amqp_1_0.jms.example.Hello
