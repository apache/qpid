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
import org.apache.maven.plugin.MojoExecutionException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * <p><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * </table>
 *
 * @author Rupert Smith
 * @goal tkscriptgen
 * @phase test
 * @execute phase="test"
 * @requiresDependencyResolution test
 */
public class TKTestScriptGenMojo extends AbstractMojo
{
    private static final String _scriptLanguage = "#!/bin/bash\n\n";

    private static final String _javaOptArgParser =
        "# Parse arguements taking all - prefixed args as JAVA_OPTS\n" + "for arg in \"$@\"; do\n"
        + "    if [[ $arg == -java:* ]]; then\n" + "        JAVA_OPTS=\"${JAVA_OPTS}-`echo $arg|cut -d ':' -f 2`  \"\n"
        + "    else\n" + "        ARGS=\"${ARGS}$arg \"\n" + "    fi\n" + "done\n\n";

    /**
     * Where to write out the scripts.
     *
     * @parameter
     */
    private String scriptOutDirectory;

    /**
     * The all-in-one test jar location.
     *
     * @parameter
     */
    private String testJar;

    /**
     * The system properties to pass to java runtime.
     *
     * @parameter
     */
    private Properties systemproperties;

    /**
     * The TKTest runner command lines. There are passed directly to the TKTestRunner main method.
     *
     * @parameter
     */
    private Map<String, String> commands = new LinkedHashMap<String, String>();

    /**
     * Implementation of the tkscriptgen goal.
     *
     * @throws MojoExecutionException
     */
    public void execute() throws MojoExecutionException
    {
        // Turn each of the test runner command lines into a script.
        for (String testName : commands.keySet())
        {
            String testOptions = commands.get(testName);
            String commandLine = "java ";

            String logdir = null;

            for (Object key : systemproperties.keySet())
            {
                String keyString = (String) key;
                String value = systemproperties.getProperty(keyString);

                if (keyString.equals("logdir"))
                {
                    logdir = value;
                }
                else
                {
                    if (keyString.startsWith("-X"))
                    {
                        commandLine += keyString + value + " ";
                    }
                    else
                    {
                        commandLine += "-D" + keyString + "=" + value + " ";
                    }
                }
            }

            commandLine +=
                "${JAVA_OPTS} -cp " + testJar + " org.apache.qpid.junit.extensions.TKTestRunner " + testOptions + " ${ARGS}";

            getLog().info("Generating Script for test: " + testName);
            getLog().debug(commandLine);

            String fileName = scriptOutDirectory + "/" + testName + ".sh";

            try
            {
                File scriptFile = new File(fileName);
                Writer scriptWriter = new FileWriter(scriptFile);
                scriptWriter.write(_scriptLanguage);
                scriptWriter.write(_javaOptArgParser);
                if (logdir != null)
                {
                    scriptWriter.write("mkdir -p " + logdir + "\n");
                }

                scriptWriter.write(commandLine);
                scriptWriter.flush();
                scriptWriter.close();
            }
            catch (IOException e)
            {
                getLog().error("Failed to write: " + fileName);
            }
        }
    }
}
