/* Licensed to the Apache Software Foundation (ASF) under one
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
package org.apache.qpid.messaging.cpp;

import org.apache.qpid.messaging.Message;
import org.apache.qpid.messaging.MessagingException;
import org.apache.qpid.messaging.Sender;
import org.apache.qpid.messaging.Session;

public class CppSender implements Sender
{
    private CppSession _ssn;
    private org.apache.qpid.messaging.cpp.jni.Sender _cppSender;

    public CppSender(CppSession ssn,
            org.apache.qpid.messaging.cpp.jni.Sender cppSender)
    {
        _ssn = ssn;
        _cppSender = cppSender;
    }

    @Override
    public void send(Message message, boolean sync) throws MessagingException
    {
        _cppSender.send(((TextMessage)message).getCppMessage(),true);
    }

    @Override
    public void close() throws MessagingException
    {
        try
        {
            _cppSender.close();
        }
        finally
        {
            _cppSender.delete();
        }
    }

    @Override
    public void setCapacity(int capacity) throws MessagingException
    {
        _cppSender.setCapacity(capacity);
    }

    @Override
    public int getCapacity() throws MessagingException
    {
        return _cppSender.getCapacity();
    }

    @Override
    public int getAvailable() throws MessagingException
    {
        return _cppSender.getAvailable();
    }

    @Override
    public int getUnsettled() throws MessagingException
    {
        return _cppSender.getUnsettled();
    }

    @Override
    public boolean isClosed()
    {
        // The C++ version does not support it.
        // Needs to be supported at a higher level.
        throw new UnsupportedOperationException("Not supported by the underlying c++ client");
    }

    @Override
    public String getName() throws MessagingException
    {
        return _cppSender.getName();
    }

    @Override
    public Session getSession() throws MessagingException
    {
        _ssn.checkError();
        return _ssn;
    }

}
