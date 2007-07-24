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
package org.apache.qpidity;


/**
 * SessionResolver is a stateless handler that accepts incoming events
 * whose context is a Channel, and produces an event whose context is
 * a Session.
 *
 * @author Rafael H. Schloming
 */

class SessionResolver<T> implements Handler<Event<Channel,T>>
{

    final private Handler<Event<Session,T>> handler;

    public SessionResolver(Handler<Event<Session,T>> handler)
    {
        this.handler = handler;
    }

    public void handle(Event<Channel,T> event)
    {
        handler.handle(new Event<Session,T>(event.context.getSession(), event.target));
    }

}
