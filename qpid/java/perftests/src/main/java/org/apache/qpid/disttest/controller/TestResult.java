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
package org.apache.qpid.disttest.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.qpid.disttest.message.ParticipantResult;
import org.apache.qpid.disttest.results.aggregation.ITestResult;

public class TestResult implements ITestResult
{
    private final SortedSet<ParticipantResult> _participantResults = Collections.synchronizedSortedSet(
            new TreeSet<ParticipantResult>(ParticipantResult.PARTICIPANT_NAME_COMPARATOR));

    private boolean _hasErrors;
    private String _name;

    public TestResult(String name)
    {
        _name = name;
    }

    @Override
    public List<ParticipantResult> getParticipantResults()
    {
        List<ParticipantResult> list = new ArrayList<ParticipantResult>(_participantResults);
        return Collections.unmodifiableList(list);
    }

    public void addParticipantResult(ParticipantResult participantResult)
    {
        _participantResults.add(participantResult);
        if(participantResult.hasError())
        {
            _hasErrors = true;
        }
    }

    @Override
    public boolean hasErrors()
    {
        return _hasErrors;
    }

    @Override
    public String getName()
    {
        return _name;
    }
}
