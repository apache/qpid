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

import java.util.Map;

// QMF2 Imports
import org.apache.qpid.qmf2.common.Handle;
import org.apache.qpid.qmf2.common.WorkItem;

/**
 * Descriptions below are taken from <a href=https://cwiki.apache.org/qpid/qmfv2-api-proposal.html>QMF2 API Proposal</a> 
 * <pre>
 * METHOD_RESPONSE: The METHOD_RESPONSE WorkItem is generated in response to an asynchronous invokeMethod made
 *                  by a QmfConsoleData object.
 *
 *                  The getParams() method of a METHOD_RESPONSE WorkItem will return a MethodResult object.
 *                  The getHandle() method returns the reply handle provided to the method call.
 *                  This handle is merely the handle used for the asynchronous response, it is not associated
 *                  with the QmfConsoleData in any other way.
 * </pre>
 * @author Fraser Adams
 */

public final class MethodResponseWorkItem extends WorkItem
{
    /**
     * Construct a MethodResponseWorkItem. Convenience constructor not in API
     *
     * @param handle the reply handle used to associate requests and responses
     * @param params the MethodCallParams used to populate the WorkItem's param
     */
    public MethodResponseWorkItem(final Handle handle, final MethodResult params)
    {
        super(WorkItemType.METHOD_RESPONSE, handle, params);
    }

    /**
     * Return the MethodResult stored in the params.
     * @return the MethodResult stored in the params.
     */
    public MethodResult getMethodResult()
    {
        return (MethodResult)getParams();
    }
}

