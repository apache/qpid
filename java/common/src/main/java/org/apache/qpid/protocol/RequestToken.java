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
package org.apache.qpid.protocol;

import org.apache.qpid.framing.AMQMethodBody;

/**
 * Allows the context for a request to be passed around, simplying the
 * task of responding to it.
 */
public class RequestToken<M extends AMQMethodBody>
{
    private final AMQProtocolWriter _session;
    private final AMQMethodEvent<M> _request;
    private final byte _major;
    private final byte _minor;

    public RequestToken(AMQProtocolWriter session, AMQMethodEvent<M> request, byte major, byte minor)
    {
        _session = session;
        _request = request;
        _major = major;
        _minor = minor;
    }

    /**
     * Sends a response to the request this token represents.
     */
    public void respond(AMQMethodBody response)
    {
        _session.writeResponse(_request.getChannelId(), _request.getRequestId(), response);
    }

    /**
     * Provides access to the original request
     */
    public M getRequest()
    {
        return _request.getMethod();
    }

    public byte getMajor()
    {
        return _major;
    }

    public byte getMinor()
    {
        return _minor;
    }
}
