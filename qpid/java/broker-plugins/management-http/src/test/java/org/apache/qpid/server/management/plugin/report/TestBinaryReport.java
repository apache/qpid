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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

public class TestBinaryReport extends QueueBinaryReport
{


    private int _limit;
    private String _propertyName;
    private int _count;
    private final ByteArrayOutputStream _bytesOutputStream = new ByteArrayOutputStream();
    private final ObjectOutputStream _objectOutputStream;
    public static final String NAME = "testBinary";

    public TestBinaryReport()
    {
        try
        {
            _objectOutputStream = new ObjectOutputStream(_bytesOutputStream);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
        ;
    }

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getContentType()
    {
        return "application/octet-stream";
    }

    @Override
    public void addMessage(final ReportableMessage reportableMessage)
    {
        if(_propertyName != null)
        {
            Object value = reportableMessage.getMessageHeader().getHeader(_propertyName);
            if(value != null)
            {
                try
                {
                    _objectOutputStream.writeObject(value);
                }
                catch (IOException e)
                {
                    // ignore
                }
            }
        }
        _count++;
    }

    @Override
    public boolean isComplete()
    {
        return _limit != 0 && _count >= _limit;
    }

    @Override
    public byte[] getReport()
    {
        try
        {
            _objectOutputStream.flush();

            return _bytesOutputStream.toByteArray();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public void setLimit(final String limit)
    {
        _limit = Integer.parseInt(limit);
    }

    public void setPropertyName(final String propertyName)
    {
        this._propertyName = propertyName;
    }
}
