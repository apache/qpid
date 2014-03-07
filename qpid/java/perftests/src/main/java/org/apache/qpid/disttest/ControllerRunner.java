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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.naming.Context;

import org.apache.qpid.disttest.controller.Controller;
import org.apache.qpid.disttest.controller.ResultsForAllTests;
import org.apache.qpid.disttest.controller.config.Config;
import org.apache.qpid.disttest.controller.config.ConfigReader;
import org.apache.qpid.disttest.db.ResultsDbWriter;
import org.apache.qpid.disttest.jms.ControllerJmsDelegate;
import org.apache.qpid.disttest.results.ResultsCsvWriter;
import org.apache.qpid.disttest.results.ResultsWriter;
import org.apache.qpid.disttest.results.aggregation.Aggregator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ControllerRunner extends AbstractRunner
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ControllerRunner.class);

    public static final String TEST_CONFIG_PROP = "test-config";
    public static final String DISTRIBUTED_PROP = "distributed";
    public static final String OUTPUT_DIR_PROP = "outputdir";
    public static final String WRITE_TO_DB = "writeToDb";
    public static final String RUN_ID = "runId";

    private static final String TEST_CONFIG_DEFAULT = "perftests-config.json";
    private static final String DISTRIBUTED_DEFAULT = "false";
    private static final String OUTPUT_DIR_DEFAULT = ".";
    public static final String WRITE_TO_DB_DEFAULT = "false";

    private final Aggregator _aggregator = new Aggregator();

    private final ConfigFileHelper _configFileHelper = new ConfigFileHelper();

    private ResultsWriter _resultsFileWriter;

    private ResultsDbWriter _resultsDbWriter;

    public ControllerRunner()
    {
        getCliOptions().put(TEST_CONFIG_PROP, TEST_CONFIG_DEFAULT);
        getCliOptions().put(DISTRIBUTED_PROP, DISTRIBUTED_DEFAULT);
        getCliOptions().put(OUTPUT_DIR_PROP, OUTPUT_DIR_DEFAULT);
        getCliOptions().put(WRITE_TO_DB, WRITE_TO_DB_DEFAULT);
        getCliOptions().put(RUN_ID, null);
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
        setUpResultFilesWriter();
        setUpResultsDbWriter();

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

    private void setUpResultsDbWriter()
    {
        String writeToDbStr = getCliOptions().get(WRITE_TO_DB);
        if(Boolean.valueOf(writeToDbStr))
        {
            String runId = getCliOptions().get(RUN_ID);
            _resultsDbWriter = new ResultsDbWriter(getContext(), runId);
            _resultsDbWriter.createResultsTableIfNecessary();
        }
    }

    void setUpResultFilesWriter()
    {
        String outputDirString = getCliOptions().get(ControllerRunner.OUTPUT_DIR_PROP);
        File outputDir = new File(outputDirString);
        _resultsFileWriter = new ResultsCsvWriter(outputDir);
    }

    private void runTests(ControllerJmsDelegate jmsDelegate)
    {
        Controller controller = new Controller(jmsDelegate, DistributedTestConstants.REGISTRATION_TIMEOUT, DistributedTestConstants.COMMAND_RESPONSE_TIMEOUT);

        String testConfigPath = getCliOptions().get(ControllerRunner.TEST_CONFIG_PROP);
        List<String> testConfigFiles = _configFileHelper.getTestConfigFiles(testConfigPath);
        createClientsIfNotDistributed(testConfigFiles);

        try
        {
            List<ResultsForAllTests> results = new ArrayList<ResultsForAllTests>();

            for (String testConfigFile : testConfigFiles)
            {
                final Config testConfig = buildTestConfigFrom(testConfigFile);
                controller.setConfig(testConfig);

                controller.awaitClientRegistrations();

                LOGGER.info("Running test : " + testConfigFile);
                ResultsForAllTests testResult = runTest(controller, testConfigFile);
                results.add(testResult);
            }

            _resultsFileWriter.writeResultsSummary(results);
        }
        catch(Exception e)
        {
            LOGGER.error("Problem running test", e);
        }
        finally
        {
            controller.stopAllRegisteredClients();
        }
    }

    private ResultsForAllTests runTest(Controller controller, String testConfigFile)
    {
        final Config testConfig = buildTestConfigFrom(testConfigFile);
        controller.setConfig(testConfig);

        ResultsForAllTests rawResultsForAllTests = controller.runAllTests();
        ResultsForAllTests resultsForAllTests = _aggregator.aggregateResults(rawResultsForAllTests);

        _resultsFileWriter.writeResults(resultsForAllTests, testConfigFile);
        if(_resultsDbWriter != null)
        {
            _resultsDbWriter.writeResults(resultsForAllTests);
        }

        return resultsForAllTests;
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

    private Config buildTestConfigFrom(String testConfigFile)
    {
        ConfigReader configReader = new ConfigReader();
        Config testConfig;
        try
        {
            testConfig = configReader.getConfigFromFile(testConfigFile);
        }
        catch (IOException e)
        {
            throw new DistributedTestException("Exception while loading test config from '" + testConfigFile + "'", e);
        }
        return testConfig;
    }

    private boolean isDistributed()
    {
        return Boolean.valueOf(getCliOptions().get(ControllerRunner.DISTRIBUTED_PROP));
    }


}
