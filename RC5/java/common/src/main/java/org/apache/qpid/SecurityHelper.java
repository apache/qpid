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
package org.apache.qpid;

import java.io.UnsupportedEncodingException;
import java.util.HashSet;
import java.util.List;
import java.util.StringTokenizer;

import org.apache.qpid.security.AMQPCallbackHandler;
import org.apache.qpid.security.CallbackHandlerRegistry;

public class SecurityHelper
{
    public static String chooseMechanism(List<Object> mechanisms) throws UnsupportedEncodingException
    {
        HashSet mechanismSet = new HashSet();
        for (Object m : mechanisms)
        {
            mechanismSet.add(m);
        }

        String preferredMechanisms = CallbackHandlerRegistry.getInstance().getMechanisms();
        StringTokenizer prefTokenizer = new StringTokenizer(preferredMechanisms, " ");
        while (prefTokenizer.hasMoreTokens())
        {
            String mech = prefTokenizer.nextToken();
            if (mechanismSet.contains(mech))
            {
                return mech;
            }
        }
        return null;
    }

    public static AMQPCallbackHandler createCallbackHandler(String mechanism, String username,String password)
            throws QpidException
    {
        Class mechanismClass = CallbackHandlerRegistry.getInstance().getCallbackHandlerClass(mechanism);
        try
        {
            Object instance = mechanismClass.newInstance();
            AMQPCallbackHandler cbh = (AMQPCallbackHandler) instance;
            cbh.initialise(username,password);
            return cbh;
        }
        catch (Exception e)
        {
            throw new QpidException("Unable to create callback handler: " + e,ErrorCode.UNDEFINED, e.getCause());
        }
    }

}
