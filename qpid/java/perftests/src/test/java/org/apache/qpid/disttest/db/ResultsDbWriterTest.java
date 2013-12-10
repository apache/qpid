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
package org.apache.qpid.disttest.db;

import static org.apache.qpid.disttest.message.ParticipantAttribute.ITERATION_NUMBER;
import static org.apache.qpid.disttest.message.ParticipantAttribute.PARTICIPANT_NAME;
import static org.apache.qpid.disttest.message.ParticipantAttribute.TEST_NAME;
import static org.apache.qpid.disttest.message.ParticipantAttribute.THROUGHPUT;
import static org.apache.qpid.test.utils.TestFileUtils.createTestDirectory;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Hashtable;
import java.util.TimeZone;

import javax.naming.Context;
import javax.naming.NamingException;

import org.apache.qpid.disttest.controller.ResultsForAllTests;
import org.apache.qpid.disttest.db.ResultsDbWriter.Clock;
import org.apache.qpid.disttest.message.ParticipantResult;
import org.apache.qpid.disttest.results.ResultsTestFixture;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.util.FileUtils;

public class ResultsDbWriterTest extends QpidTestCase
{
    private static final long _dummyTimestamp = 1234;

    private File _tempDbDirectory;
    private Clock _clock = mock(Clock.class);
    private ResultsTestFixture _resultsTestFixture = new ResultsTestFixture();

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        _tempDbDirectory = createTestDirectory();
        when(_clock.currentTimeMillis()).thenReturn(_dummyTimestamp);
    }


    @Override
    protected void tearDown() throws Exception
    {
        try
        {
            FileUtils.deleteDirectory(_tempDbDirectory.getAbsolutePath());
        }
        finally
        {
            super.tearDown();
        }
    }


    public void testWriteResults() throws Exception
    {
        Context context = getContext();
        ResultsForAllTests results = _resultsTestFixture.createResultsForAllTests();
        String runId = "myRunId";

        ResultsDbWriter resultsDbWriter = new ResultsDbWriter(context, runId, _clock);
        resultsDbWriter.createResultsTableIfNecessary();

        resultsDbWriter.writeResults(results);

        ParticipantResult expectedResult = _resultsTestFixture.getFirstParticipantResult(results);
        assertResultsAreInDb(context, expectedResult, runId);
    }

    public void testDefaultRunId() throws Exception
    {
        TimeZone defaultTimeZone = TimeZone.getDefault();
        try
        {
            // set non-GMT timezone to make the test more rigorous.
            TimeZone.setDefault(TimeZone.getTimeZone("GMT-05:00"));
            ResultsDbWriter resultsDbWriter = new ResultsDbWriter(getContext(), null, _clock);
            String runId = resultsDbWriter.getRunId();
            assertEquals(
                    "Default run id '" + runId + "' should correspond to dummy timestamp " + _clock.currentTimeMillis(),
                    "run 1970-01-01 00:00:01.234",
                    runId);
        }
        finally
        {
            TimeZone.setDefault(defaultTimeZone);
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Context getContext() throws NamingException
    {
        Context context = mock(Context.class);
        Hashtable environment = new Hashtable();

        environment.put(ResultsDbWriter.DRIVER_NAME, "org.apache.derby.jdbc.EmbeddedDriver");
        environment.put(ResultsDbWriter.URL, "jdbc:derby:" + _tempDbDirectory + "perftestResultsDb;create=true");

        when(context.getEnvironment()).thenReturn(environment);
        return context;
    }

    @SuppressWarnings("unchecked")
    private void assertResultsAreInDb(Context context, ParticipantResult participantResult, String expectedRunId) throws Exception
    {
        String driverName = (String) context.getEnvironment().get(ResultsDbWriter.DRIVER_NAME);
        Class<? extends Driver> driverClass = (Class<? extends Driver>) Class.forName(driverName);
        driverClass.newInstance();
        String url = (String) context.getEnvironment().get(ResultsDbWriter.URL);

        Connection connection = DriverManager.getConnection(url);
        Statement statement = connection.createStatement();
        ResultSet rs = statement.executeQuery(
                "SELECT * FROM results WHERE testName='" + participantResult.getTestName() +
                "' AND runId='" + expectedRunId + "'");

        try
        {
            rs.next();
            assertEquals(participantResult.getTestName(), rs.getString(TEST_NAME.getDisplayName()));
            assertEquals(participantResult.getIterationNumber(), rs.getInt(ITERATION_NUMBER.getDisplayName()));
            assertEquals(participantResult.getParticipantName(), rs.getString(PARTICIPANT_NAME.getDisplayName()));
            assertEquals(participantResult.getThroughput(), rs.getDouble(THROUGHPUT.getDisplayName()));
            assertEquals(expectedRunId, rs.getString(ResultsDbWriter.RUN_ID));
            assertEquals(new Timestamp(_dummyTimestamp), rs.getTimestamp(ResultsDbWriter.INSERTED_TIMESTAMP));
        }
        finally
        {
            connection.close();
        }
    }
}
