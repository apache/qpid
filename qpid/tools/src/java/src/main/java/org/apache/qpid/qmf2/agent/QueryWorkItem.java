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
import org.apache.qpid.qmf2.common.QmfQuery;
import org.apache.qpid.qmf2.common.WorkItem;

/**
 * Descriptions below are taken from <a href=https://cwiki.apache.org/qpid/qmfv2-api-proposal.html>QMF2 API Proposal</a> 
 * <pre>
 * QUERY: The QUERY WorkItem describes a query that the application must service. The application should call the 
 *        queryResponse() method for each object that satisfies the query. When complete, the application must call the 
 *        queryComplete() method. If a failure occurs, the application should indicate the error to the agent by calling
 *        the query_complete() method with a description of the error.
 *
 *        The getParams() method of a QUERY WorkItem will return an instance of the QmfQuery class.
 *
 *        The getHandle() WorkItem method returns the reply handle which should be passed to the Agent's queryResponse()
 *        and queryComplete() methods.
 * </pre>
 * Note that the API is a bit sketchy on the description of the QUERY WorkItem and whereas most WorkItems seem to return
 * defined classes for their getParams() it's not so obvious here. There's an implication that getParams() just returns
 * QmfQuery, but it's not clear where the uset_id bit fits.
 * <p>
 * As the API doesn't define a "QueryParams" class I've not included one, but I've added a getUserId() method to 
 * QueryWorkItem, this is a bit inconsistent with the approach for the other WorkItems though.
 *
 * @author Fraser Adams
 */

public final class QueryWorkItem extends WorkItem
{
    /**
     * Construct a QueryWorkItem. Convenience constructor not in API.
     *
     * @param handle the reply handle.
     * @param params the QmfQuery used to populate the WorkItem's param.
     */
    public QueryWorkItem(final Handle handle, final QmfQuery params)
    {
        super(WorkItemType.QUERY, handle, params);
    }

    /**
     * Return the QmfQuery stored in the params Map.
     * @return the QmfQuery stored in the params Map.
     */
    public QmfQuery getQmfQuery()
    {
        return (QmfQuery)getParams();
    }

    /**
     * Return authenticated user id of caller if present, else null.
     * @return authenticated user id of caller if present, else null.
     */
    public String getUserId()
    {
        Map map = getQmfQuery().mapEncode();
        return (String)map.get("_user_id");
    }
}

