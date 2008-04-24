/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.qpid.junit.maven;

import org.apache.maven.plugin.AbstractMojo;

import java.io.File;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

/**
 * TKTestRunnerMojo is a JUnit test runner plugin for Maven 2. It is intended to be compatible with the surefire
 * plugin (though not all features of that are yet implemented), with some extended capabilities.
 *
 * <p/>This plugin adds the ability to use different JUnit test runners, and to pass arbitrary options to them.
 *
 * <p><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * </table>
 *
 * @author Rupert Smith
 *
 * @goal tktest
 * @phase test
 * @requiresDependencyResolution test
 */
public class TKTestRunnerMojo extends AbstractMojo
{
    private static final BitSet UNRESERVED = new BitSet(256);

    /**
     * Set this to 'true' to bypass unit tests entirely. Its use is NOT RECOMMENDED, but quite convenient on occasion.
     *
     * @parameter expression="${maven.test.skip}"
     */
    private boolean skip;

    /**
     * The TKTest runner command lines. There are passed directly to the TKTestRunner main method.
     *
     * @parameter
     */
    private Map<String, String> commands = new LinkedHashMap<String, String>();

    /**
     * The base directory of the project being tested. This can be obtained in your unit test by
     * System.getProperty("basedir").
     *
     * @parameter expression="${basedir}"
     * @required
     */
    private File basedir;

    /**
     * The directory containing generated classes of the project being tested.
     *
     * @parameter expression="${project.build.outputDirectory}"
     * @required
     */
    private File classesDirectory;

    /**
     * The directory containing generated test classes of the project being tested.
     *
     * @parameter expression="${project.build.testOutputDirectory}"
     * @required
     */
    private File testClassesDirectory;

    /**
     * The classpath elements of the project being tested.
     *
     * @parameter expression="${project.testClasspathElements}"
     * @required
     * @readonly
     */
    private List classpathElements;

    /**
     * List of System properties to pass to the tests.
     *
     * @parameter
     */
    private Properties systemProperties;

    /**
     * Map of of plugin artifacts.
     *
     * @parameter expression="${plugin.artifactMap}"
     * @required
     * @readonly
     */
    private Map pluginArtifactMap;

    /**
     * Map of of project artifacts.
     *
     * @parameter expression="${project.artifactMap}"
     * @required
     * @readonly
     */
    private Map projectArtifactMap;

    /**
     * Option to specify the forking mode. Can be "never" (default), "once" or "always".
     * "none" and "pertest" are also accepted for backwards compatibility.
     *
     * @parameter expression="${forkMode}" default-value="once"
     */
    private String forkMode;

    /**
     * Option to specify the jvm (or path to the java executable) to use with
     * the forking options. For the default we will assume that java is in the path.
     *
     * @parameter expression="${jvm}"
     * default-value="java"
     */
    private String jvm;

    /**
     * The test runner to use.
     *
     * @parameter
     */
    private String testrunner;

    /**
     * The additional properties to append to the test runner invocation command line.
     *
     * @parameter
     */
    private Properties testrunnerproperties;

    /**
     * The options to pass to all test runner invocation command lines.
     *
     * @parameter
     */
    private String[] testrunneroptions;

    /**
     * Implementation of the tktest goal.
     */
    public void execute()
    {
        // Skip these tests if test skipping is turned on.
        if (skip)
        {
            getLog().info("Skipping Tests.");

            return;
        }

        // Log out the classpath if debugging is on.
        if (getLog().isDebugEnabled())
        {
            getLog().info("Test Classpath :");

            for (Object classpathElement1 : classpathElements)
            {
                String classpathElement = (String) classpathElement1;
                getLog().info("  " + classpathElement);
            }
        }

        try
        {
            // Create a class loader to load the test runner with. This also gets set as the context loader for this
            // thread, so that all subsequent class loading activity by the test runner or the test code, has the
            // test classes available to it. The system loader is set up for the maven build, which is why a seperate
            // loader needs to be created; in order to inject the test dependencies into it.
            ClassLoader runnerClassLoader = createClassLoader(classpathElements, ClassLoader.getSystemClassLoader(), true);
            Thread.currentThread().setContextClassLoader(runnerClassLoader);

            // Load the test runner implementation that will be used to run the tests.
            if ((testrunner == null) || "".equals(testrunner))
            {
                testrunner = "org.apache.qpid.junit.extensions.TKTestRunner";
            }

            Class testRunnerClass = Class.forName(testrunner, false, runnerClassLoader);
            Method run = testRunnerClass.getMethod("main", String[].class);

            // Concatenate all of the options to pass on the command line to the test runner.
            String preOptions = "";

            for (String option : testrunneroptions)
            {
                preOptions += option + " ";
            }

            // Concatenate all of the additional properties as name=value pairs on the command line.
            String nvPairs = "";

            if (testrunnerproperties != null)
            {
                for (Object objKey : testrunnerproperties.keySet())
                {
                    String key = (String) objKey;
                    String value = testrunnerproperties.getProperty(key);

                    nvPairs = key + "=" + value + " ";
                }
            }

            // Pass each of the test runner command lines in turn to the toolkit test runner.
            // The command line is made up of the options, the command specific command line, then the trailing
            // name=value pairs.
            for (String testName : commands.keySet())
            {
                String commandLine = preOptions + " " + commands.get(testName) + " " + nvPairs;
                getLog().info("commandLine = " + commandLine);

                // Tokenize the command line on white space, into an array of string components.
                String[] tokenizedCommandLine = commandLine.split("\\s+");

                // Run the tests.
                run.invoke(testRunnerClass, new Object[] { tokenizedCommandLine });
            }
        }
        catch (Exception e)
        {
            getLog().error("There was an exception: " + e.getMessage(), e);
        }
    }

    private static ClassLoader createClassLoader(List classPathUrls, ClassLoader parent, boolean childDelegation)
        throws MalformedURLException
    {
        List urls = new ArrayList();

        for (Iterator i = classPathUrls.iterator(); i.hasNext();)
        {
            String url = (String) i.next();

            if (url != null)
            {
                File f = new File(url);
                urls.add(f.toURL());
            }
        }

        IsolatedClassLoader classLoader = new IsolatedClassLoader(parent, childDelegation);

        for (Iterator iter = urls.iterator(); iter.hasNext();)
        {
            URL url = (URL) iter.next();
            classLoader.addURL(url);
        }

        return classLoader;
    }
}
