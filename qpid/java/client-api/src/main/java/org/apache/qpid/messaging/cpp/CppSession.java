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

import org.apache.qpid.messaging.Address;
import org.apache.qpid.messaging.Connection;
import org.apache.qpid.messaging.Message;
import org.apache.qpid.messaging.MessagingException;
import org.apache.qpid.messaging.Receiver;
import org.apache.qpid.messaging.Sender;
import org.apache.qpid.messaging.SessionException;
import org.apache.qpid.messaging.TransportFailureException;
import org.apache.qpid.messaging.cpp.jni.NativeMessage;
import org.apache.qpid.messaging.cpp.jni.NativeSession;
import org.apache.qpid.messaging.internal.ConnectionInternal;
import org.apache.qpid.messaging.internal.ReceiverInternal;
import org.apache.qpid.messaging.internal.SenderInternal;
import org.apache.qpid.messaging.internal.SessionInternal;

/**
 *  This class relies on the SessionManagementDecorator for
 *  management and synchronized access to it's resources.
 *  This class is merely a delegate/wrapper for the,
 *  underlying c++ session object.
 */
public class CppSession implements SessionInternal
{
    private NativeSession _cppSession;
    private CppConnection _conn;
    private String _name;

    public CppSession(CppConnection conn,NativeSession cppSsn, String name)
    {
        _cppSession = cppSsn;
        _conn = conn;
        _name = name;
    }

    @Override
    public boolean isClosed()
    {
        return _cppSession.hasError();
    }

    @Override
    public void close() throws MessagingException
    {
        try
        {
            _cppSession.close();
        }
        finally
        {
            _cppSession.delete(); // delete c++ object.
        }
    }

    @Override
    public void commit() throws MessagingException
    {
        _cppSession.commit();
    }

    @Override
    public void rollback()
    {
        _cppSession.rollback();
    }

    @Override
    public void acknowledge(boolean sync) throws MessagingException
    {
        _cppSession.acknowledge(sync);
    }

    @Override
    public void acknowledge(Message message, boolean sync) throws MessagingException
    {
        _cppSession.acknowledge((NativeMessage)message, sync);
    }

    @Override
    public void reject(Message message) throws MessagingException
    {
        _cppSession.reject((NativeMessage)message);
    }

    @Override
    public void release(Message message) throws MessagingException
    {
        _cppSession.release((NativeMessage)message);
    }

    @Override
    public void sync(boolean block) throws MessagingException
    {
        _cppSession.sync(block);
    }

    @Override
    public int getReceivable() throws MessagingException
    {
        return _cppSession.getReceivable();
    }

    @Override
    public int getUnsettledAcks() throws MessagingException
    {
        return _cppSession.getUnsettledAcks();
    }

    @Override
    public Receiver nextReceiver(long timeout) throws MessagingException
    {
        // This needs to be revisited.
        //return new CppReceiver(this,_cppSession.nextReceiver(CppDuration.getDuration(timeout)));
        return null;
    }

    @Override
    public Sender createSender(Address address) throws MessagingException
    {
        return new CppSender(this, _cppSession.createSender(address.toString()),address.toString());
    }

    @Override
    public Sender createSender(String address) throws MessagingException
    {
        return new CppSender(this,_cppSession.createSender(address),address);
    }

    @Override
    public Receiver createReceiver(Address address) throws MessagingException
    {
        return new CppReceiver(this, _cppSession.createReceiver(address.toString()),address.toString());
    }

    @Override
    public Receiver createReceiver(String address) throws MessagingException
    {
        return new CppReceiver(this,_cppSession.createReceiver(address),address);
    }

    @Override
    public Connection getConnection() throws MessagingException
    {
        return _conn;
    }


    @Override
    public boolean hasError()
    {
        return _cppSession.hasError();
    }


    @Override
    public void checkError() throws MessagingException
    {
        _cppSession.checkError();
    }

    @Override
    public ConnectionInternal getConnectionInternal()
    {
        return _conn;
    }

    @Override
    public void exception(TransportFailureException e, long serialNumber)
    {//NOOP
    }

    @Override
    public void exception(SessionException e)
    {//NOOP
    }

    @Override
    public void recreate() throws MessagingException
    {
        // TODO need to keep track if it's transactional or not
        _cppSession = _conn.getNativeConnection().createSession(_name);
    }

    @Override
    public String getName()
    {//NOOP
        return _name;
    }

    @Override
    public void unregisterReceiver(ReceiverInternal receiver)
    {//NOOP
    }

    @Override
    public void unregisterSender(SenderInternal sender)
    {//NOOP
    }

    NativeSession getNativeSession()
    {
        return _cppSession;
    }
}
