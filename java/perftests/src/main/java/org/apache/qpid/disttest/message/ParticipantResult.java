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
package org.apache.qpid.disttest.message;

import static org.apache.qpid.disttest.message.ParticipantAttribute.BATCH_SIZE;
import static org.apache.qpid.disttest.message.ParticipantAttribute.CONFIGURED_CLIENT_NAME;
import static org.apache.qpid.disttest.message.ParticipantAttribute.ITERATION_NUMBER;
import static org.apache.qpid.disttest.message.ParticipantAttribute.MAXIMUM_DURATION;
import static org.apache.qpid.disttest.message.ParticipantAttribute.PAYLOAD_SIZE;
import static org.apache.qpid.disttest.message.ParticipantAttribute.NUMBER_OF_MESSAGES_PROCESSED;
import static org.apache.qpid.disttest.message.ParticipantAttribute.THROUGHPUT;
import static org.apache.qpid.disttest.message.ParticipantAttribute.PARTICIPANT_NAME;
import static org.apache.qpid.disttest.message.ParticipantAttribute.TEST_NAME;

import java.util.Comparator;
import java.util.Date;
import java.util.Map;

public class ParticipantResult extends Response
{
    private String _testName;
    private String _participantName;
    private long _startInMillis;
    private long _endInMillis;
    private int _batchSize;
    private long _maximumDuration;
    private int _iterationNumber;

    private String _configuredClientName;

    private long _numberOfMessagesProcessed;
    private long _totalPayloadProcessed;
    private int _payloadSize;
    private double _throughput;

    public static final Comparator<? super ParticipantResult> PARTICIPANT_NAME_COMPARATOR = new Comparator<ParticipantResult>()
    {
        @Override
        public int compare(ParticipantResult participantResult1, ParticipantResult participantResult2)
        {
            return participantResult1.getParticipantName().compareTo(participantResult2.getParticipantName());
        }
    };

    public ParticipantResult()
    {
        this(CommandType.PARTICIPANT_RESULT);
    }

    public ParticipantResult(CommandType commandType)
    {
        super(commandType);
    }

    public ParticipantResult(String participantName)
    {
        this();
        setParticipantName(participantName);
    }

    @OutputAttribute(attribute=TEST_NAME)
    public String getTestName()
    {
        return _testName;
    }

    public void setTestName(String testName)
    {
        _testName = testName;
    }

    @OutputAttribute(attribute=ITERATION_NUMBER)
    public int getIterationNumber()
    {
        return _iterationNumber;
    }

    public void setIterationNumber(int iterationNumber)
    {
        _iterationNumber = iterationNumber;
    }

    public void setStartDate(Date start)
    {
        _startInMillis = start.getTime();
    }

    public void setEndDate(Date end)
    {
        _endInMillis = end.getTime();
    }

    public Date getStartDate()
    {
        return new Date(_startInMillis);
    }

    public Date getEndDate()
    {
        return new Date(_endInMillis);
    }


    public long getStartInMillis()
    {
        return _startInMillis;
    }

    public long getEndInMillis()
    {
        return _endInMillis;
    }


    @OutputAttribute(attribute=PARTICIPANT_NAME)
    public String getParticipantName()
    {
        return _participantName;
    }


    public void setParticipantName(String participantName)
    {
        _participantName = participantName;
    }

    @OutputAttribute(attribute=ParticipantAttribute.TIME_TAKEN)
    public long getTimeTaken()
    {
        return _endInMillis - _startInMillis;
    }

    @OutputAttribute(attribute=CONFIGURED_CLIENT_NAME)
    public String getConfiguredClientName()
    {
        return _configuredClientName;
    }

    public void setConfiguredClientName(String configuredClientName)
    {
        _configuredClientName = configuredClientName;
    }

    @OutputAttribute(attribute=NUMBER_OF_MESSAGES_PROCESSED)
    public long getNumberOfMessagesProcessed()
    {
        return _numberOfMessagesProcessed;
    }

    public void setNumberOfMessagesProcessed(long numberOfMessagesProcessed)
    {
        _numberOfMessagesProcessed = numberOfMessagesProcessed;
    }

    @OutputAttribute(attribute=ParticipantAttribute.TOTAL_PAYLOAD_PROCESSED)
    public long getTotalPayloadProcessed()
    {
        return _totalPayloadProcessed;
    }

    @OutputAttribute(attribute = PAYLOAD_SIZE)
    public int getPayloadSize()
    {
        return _payloadSize;
    }

    public void setPayloadSize(int payloadSize)
    {
        _payloadSize = payloadSize;
    }

    public void setTotalPayloadProcessed(long totalPayloadProcessed)
    {
        _totalPayloadProcessed = totalPayloadProcessed;
    }

    public Map<ParticipantAttribute, Object> getAttributes()
    {
        return ParticipantAttributeExtractor.getAttributes(this);
    }

    public void setBatchSize(int batchSize)
    {
        _batchSize = batchSize;
    }

    @OutputAttribute(attribute=BATCH_SIZE)
    public int getBatchSize()
    {
        return _batchSize;
    }

    public void setMaximumDuration(long maximumDuration)
    {
        _maximumDuration = maximumDuration;
    }

    @OutputAttribute(attribute=MAXIMUM_DURATION)
    public long getMaximumDuration()
    {
        return _maximumDuration;
    }

    @OutputAttribute(attribute=THROUGHPUT)
    public double getThroughput()
    {
        return _throughput;
    }

    public void setThroughput(double throughput)
    {
        _throughput = throughput;
    }

}
