/*
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
package org.apache.qpid.disttest;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.naming.Context;

import org.apache.qpid.disttest.controller.Controller;
import org.apache.qpid.disttest.controller.ResultsForAllTests;
import org.apache.qpid.disttest.controller.config.Config;
import org.apache.qpid.disttest.controller.config.ConfigReader;
import org.apache.qpid.disttest.jms.ControllerJmsDelegate;
import org.apache.qpid.disttest.results.aggregation.Aggregator;
import org.apache.qpid.disttest.results.formatting.CSVFormater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ControllerRunner extends AbstractRunner
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ControllerRunner.class);

    public static final String TEST_CONFIG_PROP = "test-config";
    public static final String DISTRIBUTED_PROP = "distributed";
    public static final String OUTPUT_DIR_PROP = "outputdir";

    private static final String TEST_CONFIG_DEFAULT = "perftests-config.json";
    private static final String DISTRIBUTED_DEFAULT = "false";
    private static final String OUTPUT_DIR_DEFAULT = ".";

    private final Aggregator _aggregator = new Aggregator();


    public ControllerRunner()
    {
        getCliOptions().put(TEST_CONFIG_PROP, TEST_CONFIG_DEFAULT);
        getCliOptions().put(DISTRIBUTED_PROP, DISTRIBUTED_DEFAULT);
        getCliOptions().put(OUTPUT_DIR_PROP, OUTPUT_DIR_DEFAULT);
    }

    public static void main(String[] args) throws Exception
    {
        ControllerRunner runner = new ControllerRunner();
        runner.parseArgumentsIntoConfig(args);
        runner.runController();
    }

    public void runController() throws Exception
    {
        Context context = getContext();

        ControllerJmsDelegate jmsDelegate = new ControllerJmsDelegate(context);

        try
        {
            runTests(jmsDelegate);
        }
        finally
        {
            jmsDelegate.closeConnections();
        }
    }

    private void runTests(ControllerJmsDelegate jmsDelegate)
    {
        Controller controller = new Controller(jmsDelegate, DistributedTestConstants.REGISTRATION_TIMEOUT, DistributedTestConstants.COMMAND_RESPONSE_TIMEOUT);

        final List<String> testConfigFiles = getTestConfigFiles();
        createClientsIfNotDistributed(testConfigFiles);

        try
        {
            for (String testConfigFile : testConfigFiles)
            {
                final Config testConfig = buildTestConfigFrom(testConfigFile);
                controller.setConfig(testConfig);

                controller.awaitClientRegistrations();

                LOGGER.info("Running test : " + testConfigFile);
                runTest(controller, testConfigFile);
            }
        }
        finally
        {
            controller.stopAllRegisteredClients();
        }

    }

    private void runTest(Controller controller, String testConfigFile)
    {
        final Config testConfig = buildTestConfigFrom(testConfigFile);
        controller.setConfig(testConfig);

        ResultsForAllTests rawResultsForAllTests = controller.runAllTests();
        ResultsForAllTests resultsForAllTests = _aggregator.aggregateResults(rawResultsForAllTests);

        final String outputFile = generateOutputCsvNameFrom(testConfigFile);
        writeResultsToFile(resultsForAllTests, outputFile);
    }

    private void createClientsIfNotDistributed(final List<String> testConfigFiles)
    {
        if(!isDistributed())
        {
            int maxNumberOfClients = 0;
            for (String testConfigFile : testConfigFiles)
            {
                final Config testConfig = buildTestConfigFrom(testConfigFile);
                final int numClients = testConfig.getTotalNumberOfClients();
                maxNumberOfClients = Math.max(numClients, maxNumberOfClients);
            }

            //we must create the required test clients, running in single-jvm mode
            for (int i = 1; i <= maxNumberOfClients; i++)
            {
                ClientRunner clientRunner = new ClientRunner();
                clientRunner.setJndiPropertiesFileLocation(getJndiConfig());
                clientRunner.runClients();
            }
        }
    }

    private void writeResultsToFile(ResultsForAllTests resultsForAllTests, String outputFile)
    {
        FileWriter writer = null;
        try
        {
            final String outputCsv = new CSVFormater().format(resultsForAllTests);
            writer = new FileWriter(outputFile);
            writer.write(outputCsv);
            LOGGER.info("Wrote " + resultsForAllTests.getTestResults().size() + " test result(s) to output file " + outputFile);
        }
        catch (IOException e)
        {
            throw new DistributedTestException("Unable to write output file " + outputFile, e);
        }
        finally
        {
            if (writer != null)
            {
                try
                {
                    writer.close();
                }
                catch (IOException e)
                {
                    LOGGER.error("Failed to close stream for file " + outputFile, e);
                }
            }
        }
    }

    private String generateOutputCsvNameFrom(String testConfigFile)
    {
        final String filenameOnlyWithExtension = new File(testConfigFile).getName();
        final String cvsFile = filenameOnlyWithExtension.replaceFirst(".?\\w*$", ".csv");
        final String outputDir = String.valueOf(getCliOptions().get(ControllerRunner.OUTPUT_DIR_PROP));

        return new File(outputDir, cvsFile).getAbsolutePath();
    }

    private List<String> getTestConfigFiles()
    {
        final List<String> testConfigFile = new ArrayList<String>();
        final File configFileOrDirectory = new File(getCliOptions().get(ControllerRunner.TEST_CONFIG_PROP));

        if (configFileOrDirectory.isDirectory())
        {
            final String[] configFiles = configFileOrDirectory.list(new FilenameFilter()
            {
                @Override
                public boolean accept(File dir, String name)
                {
                    return new File(dir, name).isFile() && name.endsWith(".json");
                }
            });

            for (String configFile : configFiles)
            {
                testConfigFile.add(new File(configFileOrDirectory, configFile).getAbsolutePath());
            }
        }
        else
        {
            testConfigFile.add(configFileOrDirectory.getAbsolutePath());
        }

        return testConfigFile;
    }

    private Config buildTestConfigFrom(String testConfigFile)
    {
        ConfigReader configReader = new ConfigReader();
        Config testConfig;
        try
        {
            testConfig = configReader.getConfigFromFile(testConfigFile);
        }
        catch (FileNotFoundException e)
        {
            throw new DistributedTestException("Exception while loading test config from " + testConfigFile, e);
        }
        return testConfig;
    }

    private boolean isDistributed()
    {
        return Boolean.valueOf(getCliOptions().get(ControllerRunner.DISTRIBUTED_PROP));
    }


}
