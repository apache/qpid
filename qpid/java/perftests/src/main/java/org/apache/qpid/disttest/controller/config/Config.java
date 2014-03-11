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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Config
{
    private List<TestConfig> _tests;

    public Config()
    {
        super();
        _tests = Collections.emptyList();
    }

    public Config(List<TestConfig> tests)
    {
        _tests = tests;
    }

    public Config(TestConfig... tests)
    {
        _tests = Arrays.asList(tests);
    }

    public List<TestInstance> getTests()
    {
        List<TestInstance> testInstances = new ArrayList<TestInstance>();
        for (TestConfig testConfig : _tests)
        {
            int iterationNumber = 0;

            List<IterationValue> iterationValues = testConfig.getIterationValues();
            if(iterationValues.isEmpty())
            {
               testInstances.add(new TestInstance(testConfig));
            }
            else
            {
                for (IterationValue iterationValue : iterationValues)
                {
                    testInstances.add(new TestInstance(testConfig, iterationNumber, iterationValue));
                    iterationNumber++;
                }
            }
        }

        return Collections.unmodifiableList(testInstances);
    }

    public List<TestConfig> getTestConfigs()
    {
        return Collections.unmodifiableList(_tests);
    }

    public int getTotalNumberOfClients()
    {
        int numberOfClients = 0;
        for (TestConfig testConfig : _tests)
        {
            numberOfClients = Math.max(testConfig.getTotalNumberOfClients(), numberOfClients);
        }
        return numberOfClients;
    }

}
