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
package org.apache.qpid.disttest.results.formatting;


import org.apache.qpid.disttest.message.ConsumerParticipantResult;
import org.apache.qpid.disttest.message.ParticipantResult;
import org.apache.qpid.disttest.message.ProducerParticipantResult;
import org.apache.qpid.disttest.results.aggregation.TestResultAggregator;
import org.apache.qpid.test.utils.QpidTestCase;

public class CSVOrderParticipantResultComparatorTest extends QpidTestCase
{
    CSVOrderParticipantResultComparator _comparator = new CSVOrderParticipantResultComparator();

    public void testOrderedConsumerParticipants() throws Exception
    {
        assertCompare(
                new ConsumerParticipantResult("apple"),
                new ConsumerParticipantResult("banana"));

    }
    public void testProducerPrecedesConsumerParticipants() throws Exception
    {
        assertCompare(
                new ProducerParticipantResult(),
                new ConsumerParticipantResult());
    }

    public void testProducerPrecedesAllProducersResult()
    {
        assertCompare(
                new ProducerParticipantResult("participantName"),
                new ParticipantResult(TestResultAggregator.ALL_PRODUCER_PARTICIPANTS_NAME));
    }

    public void testConsumerPrecedesAllConsumersResult()
    {
        assertCompare(
                new ConsumerParticipantResult("participantName"),
                new ParticipantResult(TestResultAggregator.ALL_CONSUMER_PARTICIPANTS_NAME));
    }

    public void testAllParticipantsPrecedesAllConsumersResult()
    {
        assertCompare(
                new ParticipantResult(TestResultAggregator.ALL_PARTICIPANTS_NAME),
                new ParticipantResult(TestResultAggregator.ALL_CONSUMER_PARTICIPANTS_NAME));
    }

    public void testAllParticipantsPrecedesAllProducersResult()
    {
        assertCompare(
                new ParticipantResult(TestResultAggregator.ALL_PARTICIPANTS_NAME),
                new ParticipantResult(TestResultAggregator.ALL_PRODUCER_PARTICIPANTS_NAME));
    }

    private void assertCompare(ParticipantResult smaller, ParticipantResult bigger)
    {
        assertEquals("Expected " + smaller + " to 'equal' itself",
                0,
                _comparator.compare(smaller, smaller));

        String failureMsg = "Expected " + smaller + " to be smaller than " + bigger;

        assertTrue(failureMsg, _comparator.compare(smaller, bigger) < 0);
        assertTrue(failureMsg, _comparator.compare(bigger, smaller) > 0);
    }

}
// <ParticipantResult>
