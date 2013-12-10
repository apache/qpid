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
 */
package org.apache.qpid.systest.disttest.endtoend;

import static org.apache.qpid.disttest.AbstractRunner.JNDI_CONFIG_PROP;
import static org.apache.qpid.disttest.ControllerRunner.OUTPUT_DIR_PROP;
import static org.apache.qpid.disttest.ControllerRunner.RUN_ID;
import static org.apache.qpid.disttest.ControllerRunner.TEST_CONFIG_PROP;
import static org.apache.qpid.disttest.ControllerRunner.WRITE_TO_DB;

import java.io.File;
import java.io.IOException;

import org.apache.qpid.disttest.ControllerRunner;
import org.apache.qpid.disttest.message.ParticipantAttribute;
import org.apache.qpid.disttest.results.aggregation.TestResultAggregator;
import org.apache.qpid.test.utils.QpidBrokerTestCase;
import org.apache.qpid.util.FileUtils;

public class EndToEndTest extends QpidBrokerTestCase
{
    private ControllerRunner _runner;
    private static final String TEST_CONFIG = "perftests/src/test/java/org/apache/qpid/systest/disttest/endtoend/endtoend.json";
    private static final String JNDI_CONFIG_FILE = "perftests/src/test/java/org/apache/qpid/systest/disttest/perftests.systests.properties";
    private static final String RUN1 = "run1";

    public void testRunner() throws Exception
    {
        File csvOutputDir = createTemporaryCsvDirectory();
        assertTrue("CSV output dir must not exist",csvOutputDir.isDirectory());

        final String[] args = new String[] {TEST_CONFIG_PROP + "=" + TEST_CONFIG,
                                            JNDI_CONFIG_PROP + "=" + JNDI_CONFIG_FILE,
                                            WRITE_TO_DB + "=true",
                                            RUN_ID + "=" + RUN1,
                                            OUTPUT_DIR_PROP  + "=" + csvOutputDir.getAbsolutePath()};
        _runner = new ControllerRunner();
        _runner.parseArgumentsIntoConfig(args);
        _runner.runController();

        File expectedCsvOutputFile = new File(csvOutputDir, "endtoend.csv");
        assertTrue("CSV output file must exist", expectedCsvOutputFile.exists());
        final String csvContents = FileUtils.readFileAsString(expectedCsvOutputFile);
        final String[] csvLines = csvContents.split("\n");

        int numberOfHeaders = 1;
        int numberOfParticipants = 2;
        int numberOfSummaries = 3;

        int numberOfExpectedRows = numberOfHeaders + numberOfParticipants + numberOfSummaries;
        assertEquals("Unexpected number of lines in CSV", numberOfExpectedRows, csvLines.length);

        assertDataRowsHaveCorrectTestAndClientName("End To End 1", "producingClient", "participantProducer1", csvLines[1], 1);
        assertDataRowsHaveCorrectTestAndClientName("End To End 1", "consumingClient", "participantConsumer1", csvLines[3], 1);

        assertDataRowsHaveCorrectTestAndClientName("End To End 1", "", TestResultAggregator.ALL_PARTICIPANTS_NAME, csvLines[4], 1);
        assertDataRowsHaveCorrectTestAndClientName("End To End 1", "", TestResultAggregator.ALL_CONSUMER_PARTICIPANTS_NAME, csvLines[2], 1);
        assertDataRowsHaveCorrectTestAndClientName("End To End 1", "", TestResultAggregator.ALL_PRODUCER_PARTICIPANTS_NAME, csvLines[5], 1);

    }

    private void assertDataRowsHaveCorrectTestAndClientName(String testName, String clientName, String participantName, String csvLine, int expectedNumberOfMessagesProcessed)
    {
        final int DONT_STRIP_EMPTY_LAST_FIELD_FLAG = -1;
        String[] cells = csvLine.split(",", DONT_STRIP_EMPTY_LAST_FIELD_FLAG);
        // All attributes become cells in the CSV, so this will be true
        assertEquals("Unexpected number of cells in CSV line " + csvLine, ParticipantAttribute.values().length, cells.length);
        assertEquals("Unexpected test name in CSV line " + csvLine, testName, cells[ParticipantAttribute.TEST_NAME.ordinal()]);
        assertEquals("Unexpected client name in CSV line " + csvLine, clientName, cells[ParticipantAttribute.CONFIGURED_CLIENT_NAME.ordinal()]);
        assertEquals("Unexpected participant name in CSV line " + csvLine, participantName, cells[ParticipantAttribute.PARTICIPANT_NAME.ordinal()]);
        assertEquals("Unexpected number of messages processed in CSV line " + csvLine, String.valueOf(expectedNumberOfMessagesProcessed), cells[ParticipantAttribute.NUMBER_OF_MESSAGES_PROCESSED.ordinal()]);

    }

    private File createTemporaryCsvDirectory() throws IOException
    {
        String tmpDir = System.getProperty("java.io.tmpdir");
        File csvDir = new File(tmpDir, "csv");
        csvDir.mkdir();
        csvDir.deleteOnExit();
        return csvDir;
    }

}
