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

import java.util.List;
import java.util.Map;

import org.apache.qpid.messaging.ConnectionException;
import org.apache.qpid.messaging.MessageFactory;
import org.apache.qpid.messaging.MessagingException;
import org.apache.qpid.messaging.Session;
import org.apache.qpid.messaging.TransportFailureException;
import org.apache.qpid.messaging.cpp.jni.NativeConnection;
import org.apache.qpid.messaging.internal.ConnectionInternal;
import org.apache.qpid.messaging.internal.ConnectionEventListener;
import org.apache.qpid.messaging.internal.SessionInternal;

/**
 *  This class relies on the ConnectionManagementDecorator for
 *  management and synchronized access to it's resources.
 *  This class is merely a delegate/wrapper for the,
 *  underlying c++ connection object.
 */
public class CppConnection implements ConnectionInternal
{
    private static MessageFactory _MSG_FACTORY = new CppMessageFactory();

    private NativeConnection _cppConn;
    private String _url;
    private Map<String,Object> _options;
    private long _serialNumber = 0L; // used for avoiding spurious failover calls.

    public CppConnection(String url, Map<String,Object> options)
    {
        _cppConn = createNativeConnection(url,options);
    }

    private NativeConnection createNativeConnection(String url, Map<String,Object> options)
    {
        _url = url;
        _options = options;
        if (options == null || options.size() == 0)
        {
            return new NativeConnection(url);
        }
        else
        {
            return new NativeConnection(url,options);
        }
    }

    @Override
    public void open() throws MessagingException
    {
        _cppConn.open();
        _serialNumber++;  //wrap around ?
    }

    public void reconnect(String url,Map<String,Object> options) throws TransportFailureException
    {
        try
        {
            if (_cppConn != null && _cppConn.isOpen())
            {
                close();
            }
            _cppConn = createNativeConnection(url,options);
            open();
        }
        catch (TransportFailureException e)
        {
            throw e;
        }
        catch (MessagingException e)
        {
            throw new TransportFailureException("Error reconnecting",e);
        }
    }

    @Override
    public boolean isOpen() throws MessagingException
    {
        return _cppConn.isOpen();
    }

    @Override
    public void close() throws MessagingException
    {
        try
        {
            _cppConn.close();
        }
        finally
        {
            _cppConn.delete(); //clean up the c++ object
            _cppConn = null;
        }
    }

    @Override
    public Session createSession(String name) throws MessagingException
    {
        return new CppSession(this,_cppConn.createSession(name),name);
    }

    @Override
    public Session createTransactionalSession(String name) throws MessagingException
    {
        return new CppSession(this,_cppConn.createTransactionalSession(name),name);
    }

    @Override
    public String getAuthenticatedUsername() throws MessagingException
    {
        return _cppConn.getAuthenticatedUsername();
    }

    @Override
    public MessageFactory getMessageFactory()
    {
        return _MSG_FACTORY;
    }

    @Override
    public void addConnectionEventListener(ConnectionEventListener l)
            throws ConnectionException
    {  // NOOP
    }

    @Override
    public void removeConnectionEventListener(ConnectionEventListener l)
            throws ConnectionException
    {  // NOOP
    }

    @Override
    public List<SessionInternal> getSessions() throws ConnectionException
    {  // NOOP
       return null;
    }

    @Override
    public void exception(TransportFailureException e, long serialNumber)
    {  // NOOP
    }

    @Override
    public void recreate() throws MessagingException
    {  // NOOP
    }

    @Override
    public void unregisterSession(SessionInternal sesion)
    {  // NOOP
    }

    @Override
    public Object getConnectionLock()
    {  // NOOP
       return null;
    }

    @Override
    public String getConnectionURL()
    {
        return _url;
    }

    @Override
    public Map<String, Object> getConnectionOptions()
    {
        return _options;
    }

    @Override
    public long getSerialNumber()
    {
        return _serialNumber;
    }

    NativeConnection getNativeConnection()
    {
        return _cppConn;
    }
}
