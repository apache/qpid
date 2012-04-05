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
package org.apache.qpid.disttest.message;

public abstract class CreateParticpantCommand extends Command
{
    private String _participantName;
    private String _sessionName;
    private String _destinationName;
    private long _numberOfMessages;
    private int _batchSize;
    private long _maximumDuration;

    public CreateParticpantCommand(CommandType type)
    {
        super(type);
    }

    public String getParticipantName()
    {
        return _participantName;
    }

    public void setParticipantName(final String participantName)
    {
        _participantName = participantName;
    }

    public String getSessionName()
    {
        return _sessionName;
    }

    public void setSessionName(final String sessionName)
    {
        _sessionName = sessionName;
    }

    public String getDestinationName()
    {
        return _destinationName;
    }

    public void setDestinationName(final String destinationName)
    {
        _destinationName = destinationName;
    }

    public long getNumberOfMessages()
    {
        return _numberOfMessages;
    }

    public void setNumberOfMessages(final long numberOfMessages)
    {
        _numberOfMessages = numberOfMessages;
    }

    public int getBatchSize()
    {
        return _batchSize;
    }

    public void setBatchSize(int batchSize)
    {
        _batchSize = batchSize;
    }

    public long getMaximumDuration()
    {
        return _maximumDuration;
    }

    public void setMaximumDuration(long maximumDuration)
    {
        _maximumDuration = maximumDuration;
    }

}
