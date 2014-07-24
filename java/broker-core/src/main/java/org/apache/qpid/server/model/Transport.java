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
package org.apache.qpid.server.model;

import java.util.EnumSet;

public enum Transport
{

    TCP,
    SSL(true),
    WS,
    WSS(true),
    SCTP;

    Transport()
    {
        this(false);
    }

    Transport(boolean secure)
    {
        _secure = secure;
    }

    private boolean _secure;

    public final boolean isSecure()
    {
        return _secure;
    }

    public static Transport valueOfObject(Object transportObject)
    {
        Transport transport;
        if (transportObject instanceof Transport)
        {
            transport = (Transport) transportObject;
        }
        else
        {
            try
            {
                transport = Transport.valueOf(String.valueOf(transportObject));
            }
            catch (Exception e)
            {
                throw new IllegalArgumentException("Can't convert '" + transportObject
                        + "' to one of the supported transports: " + EnumSet.allOf(Transport.class), e);
            }
        }
        return transport;
    }
}
