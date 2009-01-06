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

/**
 * An Event is a continuation, which is used to break a Mina filter chain and save the current point in the chain
 * for later processing. It is an abstract class, with different implementations for continuations of different kinds
 * of Mina events.
 *
 * <p/>These continuations are typically batched by {@link Job} for processing by a worker thread pool.
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Process a continuation in the context of a Mina session.
 * </table>
 *
 * @todo Pull up _nextFilter and getNextFilter into Event, as all events use it. Inner classes need to be non-static
 *       to use instance variables in the parent. Consequently they need to be non-inner to be instantiable outside of
 *       the context of the outer Event class. The inner class construction used here is preventing common code re-use
 *       (though not by a huge amount), but makes for an inelegent way of handling inheritance and doesn't seem like
 *       a justifiable use of inner classes. Move the inner classes out into their own files.
 *
 * @todo Could make Event implement Runnable, FutureTask, or a custom Continuation interface, to clarify its status as
 *       a continuation. Job is also a continuation, as is the job completion handler. Or, as Event is totally abstract,
 *       it is really an interface, so could just drop it and use the continuation interface instead.
 */
public abstract class Event
{
    /**
     * Creates a continuation.
     */
    public Event()
    { }

    /**
     * Processes the continuation in the context of a Mina session.
     *
     * @param session The Mina session.
     */
    public abstract void process(IoSession session);

    /**
     * A continuation ({@link Event}) that takes a Mina messageReceived event, and passes it to a NextFilter.
     *
     * <p/><table id="crc"><caption>CRC Card</caption>
     * <tr><th> Responsibilities <th> Collaborations
     * <tr><td> Pass a Mina messageReceived event to a NextFilter. <td> {@link IoFilter.NextFilter}, {@link IoSession}
     * </table>
     */
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

    /**
     * A continuation ({@link Event}) that takes a Mina filterWrite event, and passes it to a NextFilter.
     *
     * <p/><table id="crc"><caption>CRC Card</caption>
     * <tr><th> Responsibilities <th> Collaborations
     * <tr><td> Pass a Mina filterWrite event to a NextFilter.
     *     <td> {@link IoFilter.NextFilter}, {@link IoFilter.WriteRequest}, {@link IoSession}
     * </table>
     */
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

    /**
     * A continuation ({@link Event}) that takes a Mina sessionClosed event, and passes it to a NextFilter.
     *
     * <p/><table id="crc"><caption>CRC Card</caption>
     * <tr><th> Responsibilities <th> Collaborations
     * <tr><td> Pass a Mina sessionClosed event to a NextFilter. <td> {@link IoFilter.NextFilter}, {@link IoSession}
     * </table>
     */
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
