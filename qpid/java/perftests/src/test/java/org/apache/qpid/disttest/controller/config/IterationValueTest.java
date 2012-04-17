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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import java.util.Collections;
import java.util.Map;

import junit.framework.TestCase;

import org.apache.qpid.disttest.message.CreateConsumerCommand;
import org.apache.qpid.disttest.message.CreateProducerCommand;

public class IterationValueTest extends TestCase
{
    private static final int MESSAGE_SIZE = 10;

    private CreateProducerCommand _createProducerCommand;
    private CreateConsumerCommand _createConsumerCommand;
    private Map<String, String> _iterationValueMap;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        _createProducerCommand = mock(CreateProducerCommand.class);
        _createConsumerCommand = mock(CreateConsumerCommand.class);

        _iterationValueMap = Collections.singletonMap("_messageSize", String.valueOf(MESSAGE_SIZE));
    }

    public void testApplyPopulatedIterationValueToCommandWithMatchingProperties() throws Exception
    {
        IterationValue iterationValue = new IterationValue(_iterationValueMap);

        iterationValue.applyToCommand(_createProducerCommand);

        verify(_createProducerCommand).setMessageSize(MESSAGE_SIZE);
    }

    public void testApplyPopulatedIterationValueToCommandWithoutMatchingProperties() throws Exception
    {
        IterationValue iterationValue = new IterationValue(_iterationValueMap);

        iterationValue.applyToCommand(_createConsumerCommand);

        verifyZeroInteractions(_createConsumerCommand);
    }

    public void testApplyUnpopulatedIterationValueToCommand() throws Exception
    {
        IterationValue iterationValue = new IterationValue();

        iterationValue.applyToCommand(_createProducerCommand);

        verifyZeroInteractions(_createProducerCommand);
    }

}
