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
package org.apache.qpidity.transport;


/**
 * ProtocolEvent
 *
 */

public interface ProtocolEvent
{

    public interface Switch<C>
    {
        void init(C context, ProtocolHeader header);
        void method(C context, Method method);
        void header(C context, Header header);
        void data(C context, Data data);
        void error(C context, ProtocolError error);
    }

    // XXX: could do this switching with cascading defaults for the
    // specific dispatch methods
    <C> void delegate(C context, Switch sw);

    <C> void delegate(C context, Delegate<C> delegate);

    byte getEncodedTrack();

}
