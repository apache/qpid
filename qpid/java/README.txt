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

Use maven to copy the dependencies for the examples:

  mvn clean package dependency:copy-dependencies -DskipTests

Now you can run the examples as follows:

  java -cp "client/example/target/classes/:client/example/target/dependency/*" org.apache.qpid.example.Drain
