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
package org.apache.qpid.pool;

import org.apache.mina.common.IoFilter;
import org.apache.mina.common.IoSession;


abstract public class Event
{

    public Event()
    {
    }


    abstract public void process(IoSession session);


    public static final class ReceivedEvent extends Event
    {
        private final Object _data;

        private final IoFilter.NextFilter _nextFilter;

        public ReceivedEvent(final IoFilter.NextFilter nextFilter, final Object data)
        {
            super();
            _nextFilter = nextFilter;
            _data = data;
        }

        public void process(IoSession session)
        {
            _nextFilter.messageReceived(session, _data);
        }

        public IoFilter.NextFilter getNextFilter()
        {
            return _nextFilter;
        }
    }


    public static final class WriteEvent extends Event
    {
        private final IoFilter.WriteRequest _data;
        private final IoFilter.NextFilter _nextFilter;

        public WriteEvent(final IoFilter.NextFilter nextFilter, final IoFilter.WriteRequest data)
        {
            super();
            _nextFilter = nextFilter;
            _data = data;
        }


        public void process(IoSession session)
        {
            _nextFilter.filterWrite(session, _data);
        }

        public IoFilter.NextFilter getNextFilter()
        {
            return _nextFilter;
        }
    }



    public static final class CloseEvent extends Event
    {
        private final IoFilter.NextFilter _nextFilter;

        public CloseEvent(final IoFilter.NextFilter nextFilter)
        {
            super();
            _nextFilter = nextFilter;
        }


        public void process(IoSession session)
        {
            _nextFilter.sessionClosed(session);
        }

        public IoFilter.NextFilter getNextFilter()
        {
            return _nextFilter;
        }
    }

}
