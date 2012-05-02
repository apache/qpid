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
import java.util.List;

import org.apache.qpid.disttest.results.aggregation.ITestResult;

public class ResultsForAllTests
{
    private List<ITestResult> _results = new ArrayList<ITestResult>();
    private boolean _hasErrors;

    public List<ITestResult> getTestResults()
    {
        return _results;
    }

    public void add(ITestResult testResult)
    {
        _results.add(testResult);
        if(testResult.hasErrors())
        {
            _hasErrors = true;
        }
    }

    public boolean hasErrors()
    {
        return _hasErrors;
    }
}
