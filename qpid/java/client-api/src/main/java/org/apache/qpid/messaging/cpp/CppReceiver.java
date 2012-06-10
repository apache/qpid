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
import org.apache.qpid.messaging.Receiver;
import org.apache.qpid.messaging.Session;

public class CppReceiver implements Receiver
{
    org.apache.qpid.messaging.cpp.jni.Receiver _cppReceiver;
    
    public CppReceiver(org.apache.qpid.messaging.cpp.jni.Receiver cppReceiver)
    {
        _cppReceiver = cppReceiver;
    }

    @Override
    public Message get(long timeout)
    {
        org.apache.qpid.messaging.cpp.jni.Message m = _cppReceiver.get();
        return new TextMessage(m.getContent());
        
    }

    @Override
    public Message fetch(long timeout)
    {
        org.apache.qpid.messaging.cpp.jni.Message m = _cppReceiver.fetch();
        return new TextMessage(m);
    }

    @Override
    public void setCapacity(int capacity)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public int getCapacity()
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int getAvailable()
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int getUnsettled()
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void close()
    {
        // TODO Auto-generated method stub

    }

    @Override
    public boolean isClosed()
    {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public String getName()
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Session getSession()
    {
        // TODO Auto-generated method stub
        return null;
    }

}
