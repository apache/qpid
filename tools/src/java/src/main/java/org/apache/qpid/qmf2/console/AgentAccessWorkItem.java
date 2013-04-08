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

import java.util.HashMap;
import java.util.Map;

// QMF2 Imports
import org.apache.qpid.qmf2.common.Handle;
import org.apache.qpid.qmf2.common.QmfEvent;
import org.apache.qpid.qmf2.common.WorkItem;

/**
 * Abstract class that acts as a superclass for all WorkItems that need to set and retrieve an Agent.
 * <p>
 * This class is a convenience class to enable neater access the the WorkItem params for this type of WorkItem.
 *
 * @author Fraser Adams
 */

public abstract class AgentAccessWorkItem extends WorkItem
{
    /**
     * Helper method to create the WorkItem params as a Map.
     *
     * @param agent the Agent associated with the WorkItem.
     * @param event the QmfEvent associated with the WorkItem.
     */
    protected static Map<String, Object> newParams(final Agent agent, final QmfEvent event)
    {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("agent", agent);
        if (event != null)
        {
            params.put("event", event);
        }
        return params;
    }

    /**
     * Construct an AgentAccessWorkItem. Convenience constructor not in API
     *
     * @param type the type of WorkItem specified by the WorkItemType enum
     * @param handle the handle passed by async calls - the correlation ID
     * @param params the payload of the WorkItem
     */
    public AgentAccessWorkItem(final WorkItemType type, final Handle handle, final Object params)
    {
        super(type, handle, params);
    }

    /**
     * Return the Agent stored in the params Map.
     * @return the Agent stored in the params Map.
     */
    public final Agent getAgent()
    {
        Map<String, Object> p = this.<Map<String, Object>>getParams();
        return (Agent)p.get("agent");
    }
}

