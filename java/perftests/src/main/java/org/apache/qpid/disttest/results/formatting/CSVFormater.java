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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.qpid.disttest.controller.ResultsForAllTests;
import org.apache.qpid.disttest.message.ParticipantAttribute;
import org.apache.qpid.disttest.message.ParticipantResult;
import org.apache.qpid.disttest.results.aggregation.ITestResult;

/**
 * produces CSV output using the ordered enums in {@link ParticipantAttribute}
 */
public class CSVFormater
{
    public String format(ResultsForAllTests results)
    {
        StringBuilder builder = new StringBuilder();

        builder.append(header());

        List<ITestResult> testResults = results.getTestResults();

        for (ITestResult testResult : testResults)
        {

            List<ParticipantResult> participantResults = new ArrayList<ParticipantResult>(testResult.getParticipantResults());
            Collections.sort(participantResults, new CSVOrderParticipantResultComparator());

            for (ParticipantResult participantResult : participantResults)
            {
                Map<ParticipantAttribute, Object> attributes = participantResult.getAttributes();
                builder.append(row(attributes));
            }
        }

        return builder.toString();
    }

    /**
     * return a row, including a newline character at the end
     */
    private String row(Map<ParticipantAttribute, Object> attributeValueMap)
    {
        List<Object> attributeValues = new ArrayList<Object>();
        for (ParticipantAttribute attribute : ParticipantAttribute.values())
        {
            attributeValues.add(attributeValueMap.get(attribute));
        }

        String row = StringUtils.join(attributeValues.toArray(), ",");
        return row + "\n";
    }

    /** return the header row, including a newline at the end */
    private String header()
    {
        List<String> displayNames = new ArrayList<String>();
        for (ParticipantAttribute attribute : ParticipantAttribute.values())
        {
            displayNames.add(attribute.getDisplayName());
        }

        String header = StringUtils.join(displayNames.toArray(), ",");
        return header + "\n";
    }

}
