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
package org.apache.qpid.disttest.controller.config;

import org.apache.commons.lang.ObjectUtils;
import org.apache.qpid.disttest.message.CreateParticpantCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ParticipantConfig
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ParticipantConfig.class);

    public static final String DURATION_OVERRIDE_SYSTEM_PROPERTY = "qpid.disttest.duration";

    /** we cache the overridden duration so that we only compute and - more importantly - log it once */
    private static Long cachedOverriddenDuration;

    private String _destinationName;
    private long _numberOfMessages;
    private String _name;
    private int _batchSize;
    private long _maximumDuration;

    // For GSON
    public ParticipantConfig()
    {
        _name = null;
        _destinationName = null;
        _numberOfMessages = 0;
        _batchSize = 0;
        _maximumDuration = 0;
    }

    public ParticipantConfig(
            String name,
            String destinationName,
            long numberOfMessages,
            int batchSize,
            long maximumDuration)
    {
        _name = name;
        _destinationName = destinationName;
        _numberOfMessages = numberOfMessages;
        _batchSize = batchSize;
        _maximumDuration = maximumDuration;
    }

    protected void setParticipantProperties(CreateParticpantCommand createParticipantCommand)
    {
        createParticipantCommand.setParticipantName(_name);
        createParticipantCommand.setDestinationName(_destinationName);
        createParticipantCommand.setNumberOfMessages(_numberOfMessages);
        createParticipantCommand.setBatchSize(_batchSize);

        Long maximumDuration = (Long)ObjectUtils.defaultIfNull(getOverriddenDuration(), _maximumDuration);
        createParticipantCommand.setMaximumDuration(maximumDuration);
    }

    private Long getOverriddenDuration()
    {
        if(cachedOverriddenDuration != null)
        {
            return cachedOverriddenDuration;
        }

        String overriddenDurationString = System.getProperty(DURATION_OVERRIDE_SYSTEM_PROPERTY);
        if(overriddenDurationString != null)
        {
            try
            {
                long overriddenDuration = Long.valueOf(overriddenDurationString);
                LOGGER.info("Applied overridden maximum duration " + overriddenDuration);
                cachedOverriddenDuration = overriddenDuration;
                return overriddenDuration;
            }
            catch (NumberFormatException e)
            {
                LOGGER.error("Couldn't parse overridden duration " + overriddenDurationString, e);
            }
        }

        return null;
    }
}