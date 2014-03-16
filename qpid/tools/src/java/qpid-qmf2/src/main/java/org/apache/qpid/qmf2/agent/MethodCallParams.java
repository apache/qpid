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
import org.apache.qpid.qmf2.common.ObjectId;
import org.apache.qpid.qmf2.common.QmfData;

/**
 * This class contains the values passed by the Agent class to the Agent implementation within the MethodCallWorkItem.
 * <p>
 * It contains information that is needed by the Agent implementation to identify the object on which the method.
 * is to be called via getObjectId(), the name of the method to be invoked via getName() and the arguments of the
 * method via getArgs().
 *
 * @author Fraser Adams
 */

public final class MethodCallParams
{
    private final String   _name;
    private final ObjectId _objectId;
    private final QmfData  _args;
    private final String   _userId;

    /**
     * Construct MethodCallParams.
     *
     * @param m the Map used to populate the WorkItem's parameters.
     */
    public MethodCallParams(final Map m)
    {
        _name = QmfData.getString(m.get("_method_name"));

        Map oid = (Map)m.get("_object_id");
        _objectId = (oid == null) ? null : new ObjectId(oid);

        Map args = (Map)m.get("_arguments");
        if (args == null)
        {
            _args = null;
        }
        else
        {
            _args = new QmfData(args);
            _args.setSubtypes((Map)m.get("_subtypes"));
        }
        _userId = QmfData.getString(m.get("_user_id"));
    }

    /**
     * Return a string containing the name of the method call.
     * @return a string containing the name of the method call.
     */
    public String getName()
    {
        return _name;
    }

    /**
     * Return the identifier for the object on which this method needs to be invoked.
     * @return the identifier for the object on which this method needs to be invoked.
     * <p>
     * Returns null iff there is no associated object (a method call against the agent itself).
     */
    public ObjectId getObjectId()
    {
        return _objectId;
    }

    /**
     * Return a map of input arguments for the method.
     * @return a map of input arguments for the method.
     * <p>
     * Arguments are in "name"=&lt;value&gt; pairs. Returns null if no arguments are supplied.
     */
    public QmfData getArgs()
    {
        return _args;
    }

    /**
     * Return authenticated user id of caller if present, else null.
     * @return authenticated user id of caller if present, else null.
     */
    public String getUserId()
    {
        return _userId;
    }

    /**
     * Helper/debug method to list the properties and their type.
     */
    public void listValues()
    {
        System.out.println("MethodCallParams:");
        System.out.println("name: " + _name);
        System.out.println("objectId: " + _objectId);
        System.out.println("args: " + _args);
        System.out.println("userId: " + _userId);
    }
}

