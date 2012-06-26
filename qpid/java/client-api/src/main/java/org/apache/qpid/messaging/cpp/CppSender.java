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
import org.apache.qpid.messaging.cpp.CppMessageFactory.CppMessageDelegate;
import org.apache.qpid.messaging.cpp.jni.NativeMessage;
import org.apache.qpid.messaging.cpp.jni.NativeSender;
import org.apache.qpid.messaging.internal.MessageInternal;
import org.apache.qpid.messaging.internal.SenderInternal;

public class CppSender implements SenderInternal
{
    private final CppSession _ssn;
    private NativeSender _cppSender;
    private final CppMessageFactory _msgFactory;
    private final String _address;

    public CppSender(CppSession ssn, NativeSender cppSender, String address) throws MessagingException
    {
        _ssn = ssn;
        _cppSender = cppSender;
        _msgFactory = (CppMessageFactory)ssn.getConnection().getMessageFactory();
        _address = address;
    }

    @Override
    public void send(Message message, boolean sync) throws MessagingException
    {
        NativeMessage m = convertForSending(message);
        _cppSender.send(m,true);
    }

    private NativeMessage convertForSending(Message m) throws MessagingException
    {
        if((m instanceof MessageInternal) &&
           (_msgFactory.getClass() == ((MessageInternal)m).getMessageFactoryClass())
          )
        {
            CppMessageDelegate delegate = (CppMessageDelegate)((MessageInternal)m).getFactorySpecificMessageDelegate();
            NativeMessage msg = delegate.getNativeMessage();
            msg.setContentAsByteBuffer(m.getContent());
            return msg;
        }
        else
        {
            throw new MessagingException("Incompatible message implementation." +
                     "You need to use the MessageFactory given by the connection that owns this ");
        }
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

    @Override
    public void recreate() throws MessagingException
    {
        _cppSender = _ssn.getNativeSession().createSender(_address);
    }
}
