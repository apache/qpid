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
package org.apache.qpid.server.cluster;

import org.apache.qpid.server.cluster.handler.ClusterMethodHandlerFactory;
import org.apache.qpid.server.cluster.replay.RecordingMethodHandlerFactory;
import org.apache.qpid.server.cluster.replay.ReplayStore;

import java.net.InetSocketAddress;

class ClusterBuilder
{
    private final LoadTable loadTable = new LoadTable();
    private final ReplayStore replayStore = new ReplayStore();
    private final MemberHandle handle;
    private final GroupManager groupMgr;

    ClusterBuilder(InetSocketAddress address)
    {
        handle = new SimpleMemberHandle(address.getHostName(), address.getPort()).resolve();
        groupMgr = new DefaultGroupManager(handle, getBrokerFactory(), replayStore, loadTable);
    }

    GroupManager getGroupManager()
    {
        return groupMgr;
    }

    ServerHandlerRegistry getHandlerRegistry()
    {
        return new ServerHandlerRegistry(getHandlerFactory(), null, null);
    }

    private MethodHandlerFactory getHandlerFactory()
    {
        MethodHandlerFactory factory = new ClusterMethodHandlerFactory(groupMgr, loadTable);
        //need to wrap relevant handlers with recording handler for easy replay:
        return new RecordingMethodHandlerFactory(factory, replayStore);
    }

    private BrokerFactory getBrokerFactory()
    {
        return new MinaBrokerProxyFactory(handle);
    }
}
