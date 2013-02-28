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

import static org.apache.qpid.disttest.message.ParticipantAttribute.ACKNOWLEDGE_MODE;
import static org.apache.qpid.disttest.message.ParticipantAttribute.AVERAGE_LATENCY;
import static org.apache.qpid.disttest.message.ParticipantAttribute.BATCH_SIZE;
import static org.apache.qpid.disttest.message.ParticipantAttribute.CONFIGURED_CLIENT_NAME;
import static org.apache.qpid.disttest.message.ParticipantAttribute.DELIVERY_MODE;
import static org.apache.qpid.disttest.message.ParticipantAttribute.ERROR_MESSAGE;
import static org.apache.qpid.disttest.message.ParticipantAttribute.IS_BROWSING_SUBSCRIPTION;
import static org.apache.qpid.disttest.message.ParticipantAttribute.IS_DURABLE_SUBSCRIPTION;
import static org.apache.qpid.disttest.message.ParticipantAttribute.IS_NO_LOCAL;
import static org.apache.qpid.disttest.message.ParticipantAttribute.IS_SELECTOR;
import static org.apache.qpid.disttest.message.ParticipantAttribute.IS_SYNCHRONOUS_CONSUMER;
import static org.apache.qpid.disttest.message.ParticipantAttribute.IS_TOPIC;
import static org.apache.qpid.disttest.message.ParticipantAttribute.ITERATION_NUMBER;
import static org.apache.qpid.disttest.message.ParticipantAttribute.LATENCY_STANDARD_DEVIATION;
import static org.apache.qpid.disttest.message.ParticipantAttribute.MAXIMUM_DURATION;
import static org.apache.qpid.disttest.message.ParticipantAttribute.MAX_LATENCY;
import static org.apache.qpid.disttest.message.ParticipantAttribute.MIN_LATENCY;
import static org.apache.qpid.disttest.message.ParticipantAttribute.NUMBER_OF_MESSAGES_PROCESSED;
import static org.apache.qpid.disttest.message.ParticipantAttribute.PARTICIPANT_NAME;
import static org.apache.qpid.disttest.message.ParticipantAttribute.PAYLOAD_SIZE;
import static org.apache.qpid.disttest.message.ParticipantAttribute.PRIORITY;
import static org.apache.qpid.disttest.message.ParticipantAttribute.PRODUCER_INTERVAL;
import static org.apache.qpid.disttest.message.ParticipantAttribute.PRODUCER_START_DELAY;
import static org.apache.qpid.disttest.message.ParticipantAttribute.TEST_NAME;
import static org.apache.qpid.disttest.message.ParticipantAttribute.THROUGHPUT;
import static org.apache.qpid.disttest.message.ParticipantAttribute.TIME_TAKEN;
import static org.apache.qpid.disttest.message.ParticipantAttribute.TIME_TO_LIVE;
import static org.apache.qpid.disttest.message.ParticipantAttribute.TOTAL_NUMBER_OF_CONSUMERS;
import static org.apache.qpid.disttest.message.ParticipantAttribute.TOTAL_NUMBER_OF_PRODUCERS;
import static org.apache.qpid.disttest.message.ParticipantAttribute.TOTAL_PAYLOAD_PROCESSED;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Date;
import java.util.Hashtable;
import java.util.TimeZone;

import javax.naming.Context;
import javax.naming.NamingException;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.apache.log4j.Logger;
import org.apache.qpid.disttest.controller.ResultsForAllTests;
import org.apache.qpid.disttest.message.ParticipantResult;
import org.apache.qpid.disttest.results.aggregation.ITestResult;

/**
 * Intended call sequence:
 * <ul>
 * <li>{@link #ResultsDbWriter(Context, String)}</li>
 * <li>{@link #createResultsTableIfNecessary()}</li>
 * <li>{@link #writeResults(ResultsForAllTests)} (usually multiple times)</li>
 * </ul>
 */
public class ResultsDbWriter
{
    private static final Logger _logger = Logger.getLogger(ResultsDbWriter.class);

    private static final String RESULTS_TABLE_NAME = "RESULTS";

    /** column name */
    static final String INSERTED_TIMESTAMP = "insertedTimestamp";
    /** column name */
    static final String RUN_ID = "runId";

    private static final String TABLE_EXISTENCE_QUERY = "SELECT 1 FROM SYS.SYSTABLES WHERE TABLENAME = ?";

    private static final String CREATE_RESULTS_TABLE = String.format(
            "CREATE TABLE %1$s (" +
            "%2$s varchar(200) not null" +   // TEST_NAME
            ", %3$s bigint not null" +       // ITERATION_NUMBER
            ", %4$s varchar(200) not null" + // PARTICIPANT_NAME
            ", %5$s double not null" +       // THROUGHPUT
            ", %6$s double" +                // AVERAGE_LATENCY
            ", %7$s varchar(200)" + // CONFIGURED_CLIENT_NAME
            ", %8$s bigint" +       // NUMBER_OF_MESSAGES_PROCESSED
            ", %9$s bigint" +       // PAYLOAD_SIZE
            ", %10$s bigint" +      // PRIORITY
            ", %11$s bigint" +      // TIME_TO_LIVE
            ", %12$s bigint" +      // ACKNOWLEDGE_MODE
            ", %13$s bigint" +      // DELIVERY_MODE
            ", %14$s bigint" +      // BATCH_SIZE
            ", %15$s bigint" +      // MAXIMUM_DURATION
            ", %16$s bigint" +      // PRODUCER_START_DELAY
            ", %17$s bigint" +      // PRODUCER_INTERVAL
            ", %18$s bigint" +      // IS_TOPIC
            ", %19$s bigint" +      // IS_DURABLE_SUBSCRIPTION
            ", %20$s bigint" +      // IS_BROWSING_SUBSCRIPTION
            ", %21$s bigint" +      // IS_SELECTOR
            ", %22$s bigint" +      // IS_NO_LOCAL
            ", %23$s bigint" +      // IS_SYNCHRONOUS_CONSUMER
            ", %24$s bigint" +      // TOTAL_NUMBER_OF_CONSUMERS
            ", %25$s bigint" +      // TOTAL_NUMBER_OF_PRODUCERS
            ", %26$s bigint" +      // TOTAL_PAYLOAD_PROCESSED
            ", %27$s bigint" +      // TIME_TAKEN
            ", %28$s varchar(2000)" +  // ERROR_MESSAGE
            ", %29$s bigint" +      // MIN_LATENCY
            ", %30$s bigint" +      // MAX_LATENCY
            ", %31$s double" +      // LATENCY_STANDARD_DEVIATION
            ", %32$s varchar(200) not null" +
            ", %33$s timestamp not null" +
            ")",
            RESULTS_TABLE_NAME,
            TEST_NAME.getDisplayName(),
            ITERATION_NUMBER.getDisplayName(),
            PARTICIPANT_NAME.getDisplayName(),
            THROUGHPUT.getDisplayName(),
            AVERAGE_LATENCY.getDisplayName(),
            CONFIGURED_CLIENT_NAME.getDisplayName(),
            NUMBER_OF_MESSAGES_PROCESSED.getDisplayName(),
            PAYLOAD_SIZE.getDisplayName(),
            PRIORITY.getDisplayName(),
            TIME_TO_LIVE.getDisplayName(),
            ACKNOWLEDGE_MODE.getDisplayName(),
            DELIVERY_MODE.getDisplayName(),
            BATCH_SIZE.getDisplayName(),
            MAXIMUM_DURATION.getDisplayName(),
            PRODUCER_START_DELAY.getDisplayName(),
            PRODUCER_INTERVAL.getDisplayName(),
            IS_TOPIC.getDisplayName(),
            IS_DURABLE_SUBSCRIPTION.getDisplayName(),
            IS_BROWSING_SUBSCRIPTION.getDisplayName(),
            IS_SELECTOR.getDisplayName(),
            IS_NO_LOCAL.getDisplayName(),
            IS_SYNCHRONOUS_CONSUMER.getDisplayName(),
            TOTAL_NUMBER_OF_CONSUMERS.getDisplayName(),
            TOTAL_NUMBER_OF_PRODUCERS.getDisplayName(),
            TOTAL_PAYLOAD_PROCESSED.getDisplayName(),
            TIME_TAKEN.getDisplayName(),
            ERROR_MESSAGE.getDisplayName(),
            MIN_LATENCY.getDisplayName(),
            MAX_LATENCY.getDisplayName(),
            LATENCY_STANDARD_DEVIATION.getDisplayName(),
            RUN_ID,
            INSERTED_TIMESTAMP
        );

    public static final String DRIVER_NAME = "jdbcDriverClass";
    public static final String URL = "jdbcUrl";

    private final String _url;
    private final String _runId;

    private final Clock _clock;

    /**
     * @param runId may be null, in which case a default value is chosen based on current GMT time
     * @param context must contain environment entries {@value #DRIVER_NAME} and {@value #URL}.
     */
    public ResultsDbWriter(Context context, String runId)
    {
        this(context, runId, new Clock());
    }

    /** only call directly from tests */
    ResultsDbWriter(Context context, String runId, Clock clock)
    {
        _clock = clock;
        _runId = defaultIfNullRunId(runId);

        _url = initialiseJdbc(context);
    }

    private String defaultIfNullRunId(String runId)
    {
        if(runId == null)
        {
            Date dateNow = new Date(_clock.currentTimeMillis());
            Calendar calNow = Calendar.getInstance(TimeZone.getTimeZone("GMT+00:00"));
            calNow.setTime(dateNow);
            return String.format("run %1$tF %1$tT.%tL", calNow);
        }
        else
        {
            return runId;
        }
    }

    public String getRunId()
    {
        return _runId;
    }

    /**
     * Uses the context's environment to load the JDBC driver class and return the
     * JDBC URL specified therein.
     * @return the JDBC URL
     */
    private String initialiseJdbc(Context context)
    {
        Hashtable<?, ?> environment = null;
        try
        {
            environment = context.getEnvironment();

            String driverName = (String) environment.get(DRIVER_NAME);
            if(driverName == null)
            {
                throw new IllegalArgumentException("JDBC driver name " + DRIVER_NAME
                        + " missing from context environment: " + environment);
            }

            Class.forName(driverName);

            Object url = environment.get(URL);
            if(url == null)
            {
                throw new IllegalArgumentException("JDBC URL " + URL + " missing from context environment: " + environment);
            }
            return (String) url;
        }
        catch (NamingException e)
        {
            throw constructorRethrow(e, environment);
        }
        catch (ClassNotFoundException e)
        {
            throw constructorRethrow(e, environment);
        }
    }

    private RuntimeException constructorRethrow(Exception e, Hashtable<?, ?> environment)
    {
        return new RuntimeException("Couldn't initialise ResultsDbWriter from context with environment" + environment, e);
    }

    public void createResultsTableIfNecessary()
    {
        try
        {
            Connection connection = null;
            try
            {
                connection = DriverManager.getConnection(_url);
                if(!tableExists(RESULTS_TABLE_NAME, connection))
                {
                    Statement statement = connection.createStatement();
                    try
                    {
                        _logger.info("About to create results table using SQL: " + CREATE_RESULTS_TABLE);
                        statement.execute(CREATE_RESULTS_TABLE);
                    }
                    finally
                    {
                        statement.close();
                    }
                }
            }
            finally
            {
                if(connection != null)
                {
                    connection.close();
                }
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeException("Couldn't create results table", e);
        }

    }

    private boolean tableExists(final String tableName, final Connection conn) throws SQLException
    {
        PreparedStatement stmt = conn.prepareStatement(TABLE_EXISTENCE_QUERY);
        try
        {
            stmt.setString(1, tableName);
            ResultSet rs = stmt.executeQuery();
            try
            {
                return rs.next();
            }
            finally
            {
                rs.close();
            }
        }
        finally
        {
            stmt.close();
        }
    }

    public void writeResults(ResultsForAllTests results)
    {
        try
        {
            writeResultsThrowingException(results);
        }
        catch (SQLException e)
        {
            throw new RuntimeException("Couldn't write results " + results, e);
        }
        _logger.info(this + " wrote " + results.getTestResults().size() + " results to database");
    }

    private void writeResultsThrowingException(ResultsForAllTests results) throws SQLException
    {
        Connection connection = null;
        try
        {
            connection = DriverManager.getConnection(_url);

            for (ITestResult testResult : results.getTestResults())
            {
                for (ParticipantResult participantResult : testResult.getParticipantResults())
                {
                    writeParticipantResult(connection, participantResult);
                }
            }
        }
        finally
        {
            if(connection != null)
            {
                connection.close();
            }
        }
    }

    private void writeParticipantResult(Connection connection, ParticipantResult participantResult) throws SQLException
    {
        if(_logger.isDebugEnabled())
        {
            _logger.debug("About to write to DB the following participant result: " + participantResult);
        }

        PreparedStatement statement = null;
        try
        {
            String sqlTemplate = String.format(
                    "INSERT INTO %s (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s) " +
                    "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    RESULTS_TABLE_NAME,
                    TEST_NAME.getDisplayName(),
                    ITERATION_NUMBER.getDisplayName(),
                    PARTICIPANT_NAME.getDisplayName(),
                    THROUGHPUT.getDisplayName(),
                    AVERAGE_LATENCY.getDisplayName(),
                    CONFIGURED_CLIENT_NAME.getDisplayName(),
                    NUMBER_OF_MESSAGES_PROCESSED.getDisplayName(),
                    PAYLOAD_SIZE.getDisplayName(),
                    PRIORITY.getDisplayName(),
                    TIME_TO_LIVE.getDisplayName(),
                    ACKNOWLEDGE_MODE.getDisplayName(),
                    DELIVERY_MODE.getDisplayName(),
                    BATCH_SIZE.getDisplayName(),
                    MAXIMUM_DURATION.getDisplayName(),
                    PRODUCER_START_DELAY.getDisplayName(),
                    PRODUCER_INTERVAL.getDisplayName(),
                    IS_TOPIC.getDisplayName(),
                    IS_DURABLE_SUBSCRIPTION.getDisplayName(),
                    IS_BROWSING_SUBSCRIPTION.getDisplayName(),
                    IS_SELECTOR.getDisplayName(),
                    IS_NO_LOCAL.getDisplayName(),
                    IS_SYNCHRONOUS_CONSUMER.getDisplayName(),
                    TOTAL_NUMBER_OF_CONSUMERS.getDisplayName(),
                    TOTAL_NUMBER_OF_PRODUCERS.getDisplayName(),
                    TOTAL_PAYLOAD_PROCESSED.getDisplayName(),
                    TIME_TAKEN.getDisplayName(),
                    ERROR_MESSAGE.getDisplayName(),
                    MIN_LATENCY.getDisplayName(),
                    MAX_LATENCY.getDisplayName(),
                    LATENCY_STANDARD_DEVIATION.getDisplayName(),
                    RUN_ID,
                    INSERTED_TIMESTAMP
                    );
            statement = connection.prepareStatement(sqlTemplate);

            int columnIndex = 1;
            statement.setString(columnIndex++, participantResult.getTestName());
            statement.setInt(columnIndex++, participantResult.getIterationNumber());
            statement.setString(columnIndex++, participantResult.getParticipantName());
            statement.setDouble(columnIndex++, participantResult.getThroughput());
            statement.setDouble(columnIndex++, participantResult.getAverageLatency());
            statement.setString(columnIndex++, participantResult.getConfiguredClientName());
            statement.setLong(columnIndex++, participantResult.getNumberOfMessagesProcessed());
            statement.setLong(columnIndex++, participantResult.getPayloadSize());
            statement.setLong(columnIndex++, participantResult.getPriority());
            statement.setLong(columnIndex++, participantResult.getTimeToLive());
            statement.setLong(columnIndex++, participantResult.getAcknowledgeMode());
            statement.setLong(columnIndex++, participantResult.getDeliveryMode());
            statement.setLong(columnIndex++, participantResult.getBatchSize());
            statement.setLong(columnIndex++, participantResult.getMaximumDuration());
            statement.setLong(columnIndex++, 0 /* TODO PRODUCER_START_DELAY*/);
            statement.setLong(columnIndex++, 0 /* TODO PRODUCER_INTERVAL*/);
            statement.setLong(columnIndex++, 0 /* TODO IS_TOPIC*/);
            statement.setLong(columnIndex++, 0 /* TODO IS_DURABLE_SUBSCRIPTION*/);
            statement.setLong(columnIndex++, 0 /* TODO IS_BROWSING_SUBSCRIPTION*/);
            statement.setLong(columnIndex++, 0 /* TODO IS_SELECTOR*/);
            statement.setLong(columnIndex++, 0 /* TODO IS_NO_LOCAL*/);
            statement.setLong(columnIndex++, 0 /* TODO IS_SYNCHRONOUS_CONSUMER*/);
            statement.setLong(columnIndex++, participantResult.getTotalNumberOfConsumers());
            statement.setLong(columnIndex++, participantResult.getTotalNumberOfProducers());
            statement.setLong(columnIndex++, participantResult.getTotalPayloadProcessed());
            statement.setLong(columnIndex++, participantResult.getTimeTaken());
            statement.setString(columnIndex++, participantResult.getErrorMessage());
            statement.setLong(columnIndex++, participantResult.getMinLatency());
            statement.setLong(columnIndex++, participantResult.getMaxLatency());
            statement.setDouble(columnIndex++, participantResult.getLatencyStandardDeviation());

            statement.setString(columnIndex++, _runId);
            statement.setTimestamp(columnIndex++, new Timestamp(_clock.currentTimeMillis()));

            statement.execute();
            connection.commit();
        }
        catch(SQLException e)
        {
            _logger.error("Couldn't write " + participantResult, e);
        }
        finally
        {
            if (statement != null)
            {
                statement.close();
            }
        }
    }

    public static class Clock
    {
        public long currentTimeMillis()
        {
            return System.currentTimeMillis();
        }
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
            .append("runId", _runId)
            .append("url", _url)
            .toString();
    }
}
