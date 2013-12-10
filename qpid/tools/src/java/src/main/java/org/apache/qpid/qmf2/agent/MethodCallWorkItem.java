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
package org.apache.qpid.qmf2.agent;

import java.util.Map;

// QMF2 Imports
import org.apache.qpid.qmf2.common.Handle;
import org.apache.qpid.qmf2.common.WorkItem;

/**
 * Descriptions below are taken from <a href=https://cwiki.apache.org/qpid/qmfv2-api-proposal.html>QMF2 API Proposal</a> 
 * <pre>
 * METHOD_CALL: The METHOD_CALL WorkItem describes a method call that must be serviced by the application on 
 *              behalf of this Agent.
 *
 *              The getParams() method of a METHOD_CALL WorkItem will return an instance of the MethodCallParams class.
 *
 *              Use MethodCallWorkItem to enable neater access
 * </pre>
 * @author Fraser Adams
 */

public final class MethodCallWorkItem extends WorkItem
{
    /**
     * Construct a MethodCallWorkItem. Convenience constructor not in API
     *
     * @param handle the reply handle.
     * @param params the MethodCallParams used to populate the WorkItem's param.
     */
    public MethodCallWorkItem(final Handle handle, final MethodCallParams params)
    {
        super(WorkItemType.METHOD_CALL, handle, params);
    }

    /**
     * Return the MethodCallParams stored in the params Map.
     * @return the MethodCallParams stored in the params Map.
     */
    public MethodCallParams getMethodCallParams()
    {
        return (MethodCallParams)getParams();
    }
}

