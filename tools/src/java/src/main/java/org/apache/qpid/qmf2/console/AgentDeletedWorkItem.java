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
package org.apache.qpid.qmf2.console;

/**
 * Descriptions below are taken from <a href=https://cwiki.apache.org/qpid/qmfv2-api-proposal.html>QMF2 API Proposal</a> 
 * <pre>
 * AGENT_DELETED: When a known Agent stops sending heartbeat messages, the Console will time out that Agent.
 *                On Agent timeout, an AGENT_DELETED WorkItem is pushed onto the work-queue. The WorkItem's
 *                getParam() call returns a map which contains a reference to the Agent instance that has
 *                been deleted. The reference is indexed from the map using the key string "agent". There is
 *                no handle associated with this WorkItem.
 *
 *                The Console application must release all saved references to the Agent before returning the
 *                WorkItem.
 * </pre>
 * @author Fraser Adams
 */

public final class AgentDeletedWorkItem extends AgentAccessWorkItem
{
    /**
     * Construct an AgentDeletedWorkItem. Convenience constructor not in API
     *
     * @param agent the Agent used to populate the WorkItem's param
     */
    public AgentDeletedWorkItem(final Agent agent)
    {
        super(WorkItemType.AGENT_DELETED, null, newParams(agent, null));
    }
}

