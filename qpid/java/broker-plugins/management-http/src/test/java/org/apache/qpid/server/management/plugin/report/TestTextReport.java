/*
 *
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
package org.apache.qpid.server.management.plugin.report;

import java.util.Arrays;

public class TestTextReport extends QueueTextReport
{
    public static final String NAME = "testText";
    private int _count;
    private String _stringParam;
    private String[] _stringArrayParam;

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getContentType()
    {
        return "text/plain";
    }

    @Override
    public void addMessage(final ReportableMessage reportableMessage)
    {
        _count++;
    }

    @Override
    public boolean isComplete()
    {
        return false;
    }

    @Override
    public String getReport()
    {
        StringBuilder result = new StringBuilder("There are " + _count + " messages on the queue.");
        if(_stringParam != null)
        {
            result.append(" stringParam = " + _stringParam + ".");
        }
        if(_stringArrayParam != null)
        {
            result.append(" stringArrayParam = " + Arrays.asList(_stringArrayParam) + ".");
        }
        return result.toString();
    }

    @SuppressWarnings("unused")
    public void setStringParam(final String value)
    {
        _stringParam = value;
    }

    @SuppressWarnings("unused")
    public void setStringArrayParam(final String[] value)
    {
        _stringArrayParam = value;
    }


}
