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
import org.apache.qpid.messaging.Receiver;
import org.apache.qpid.messaging.Sender;
import org.apache.qpid.messaging.Session;

public class CppSession implements Session
{
    org.apache.qpid.messaging.cpp.jni.Session _cppSession;
    
    public CppSession(org.apache.qpid.messaging.cpp.jni.Session cppSsn)
    {
        _cppSession = cppSsn;
    }
    

    @Override
    public boolean isClosed()
    {
        return false;
    }

    @Override
    public void close()
    {
        _cppSession.close();
    }

    @Override
    public void commit()
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void rollback()
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void acknowledge(boolean sync)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public <T> void acknowledge(Message message, boolean sync)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public <T> void reject(Message message)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public <T> void release(Message message)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void sync(boolean block)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public int getReceivable()
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int getUnsettledAcks()
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public Receiver nextReceiver(long timeout)
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Sender createSender(Address address)
    {        
        return new CppSender(_cppSession
                .createSender(new org.apache.qpid.messaging.cpp.jni.Address(
                        address.toString())));
    }

    @Override
    public Sender createSender(String address)
    {
        return new CppSender(_cppSession.createSender(address));
    }

    @Override
    public Receiver createReceiver(Address address)
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Receiver createReceiver(String address)
    {
        return new CppReceiver(_cppSession.createReceiver(address));
    }

    @Override
    public Connection getConnection()
    {
        // TODO Auto-generated method stub
        return null;
    }

}
