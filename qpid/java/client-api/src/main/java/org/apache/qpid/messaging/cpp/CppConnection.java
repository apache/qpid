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

import org.apache.qpid.messaging.Connection;
import org.apache.qpid.messaging.Session;

public class CppConnection implements Connection
{
    private org.apache.qpid.messaging.cpp.jni.Connection _cppConn;
    
    public CppConnection(String url)
    {
        _cppConn = new org.apache.qpid.messaging.cpp.jni.Connection(url);
    }

    @Override
    public void open()
    {
        _cppConn.open();
    }

    @Override
    public boolean isOpen()
    {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void close()
    {
        _cppConn.close();
    }

    @Override
    public Session createSession(String name)
    {
        return new CppSession(_cppConn.createSession());
    }

    @Override
    public Session createTransactionalSession(String name)
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getAuthenticatedUsername()
    {
        // TODO Auto-generated method stub
        return null;
    }

}
