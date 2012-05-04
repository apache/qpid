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
package org.apache.qpid.server.protocol.v1_0;

import org.apache.qpid.amqp_1_0.transport.ConnectionEventListener;
import org.apache.qpid.amqp_1_0.transport.SessionEndpoint;

import org.apache.qpid.server.registry.IApplicationRegistry;
import org.apache.qpid.server.virtualhost.VirtualHost;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Connection_1_0 implements ConnectionEventListener
{

    private IApplicationRegistry _appRegistry;
    private VirtualHost _vhost;


    public static interface Task
    {
        public void doTask(Connection_1_0 connection);
    }


    private List<Task> _closeTasks =
            Collections.synchronizedList(new ArrayList<Task>());



    public Connection_1_0(IApplicationRegistry appRegistry)
    {
        _appRegistry = appRegistry;
        _vhost = _appRegistry.getVirtualHostRegistry().getDefaultVirtualHost();
    }

    public void remoteSessionCreation(SessionEndpoint endpoint)
    {
        Session_1_0 session = new Session_1_0(_vhost, _appRegistry, this);
        endpoint.setSessionEventListener(session);
    }


    void removeConnectionCloseTask(final Task task)
    {
        _closeTasks.remove( task );
    }

    void addConnectionCloseTask(final Task task)
    {
        _closeTasks.add( task );
    }

    public void closeReceived()
    {
        List<Task> taskCopy;
        synchronized (_closeTasks)
        {
            taskCopy = new ArrayList<Task>(_closeTasks);
        }
        for(Task task : taskCopy)
        {
            task.doTask(this);
        }
        synchronized (_closeTasks)
        {
            _closeTasks.clear();
        }

    }

    public void closed()
    {
        closeReceived();
    }


}
