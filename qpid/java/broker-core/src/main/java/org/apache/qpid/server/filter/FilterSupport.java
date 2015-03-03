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

package org.apache.qpid.server.filter;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import org.apache.qpid.common.AMQPFilterTypes;
import org.apache.qpid.filter.SelectorParsingException;
import org.apache.qpid.filter.selector.ParseException;
import org.apache.qpid.filter.selector.TokenMgrError;
import org.apache.qpid.server.consumer.ConsumerImpl;
import org.apache.qpid.server.message.MessageSource;
import org.apache.qpid.server.plugin.PluggableService;
import org.apache.qpid.server.queue.AMQQueue;

public class FilterSupport
{
    private static final Map<String, WeakReference<JMSSelectorFilter>> _selectorCache =
            Collections.synchronizedMap(new WeakHashMap<String, WeakReference<JMSSelectorFilter>>());

    static MessageFilter createJMSSelectorFilter(Map<String, Object> args) throws AMQInvalidArgumentException
    {
        final String selectorString = (String) args.get(AMQPFilterTypes.JMS_SELECTOR.toString());
        return getMessageFilter(selectorString);
    }


    private static MessageFilter getMessageFilter(String selectorString) throws AMQInvalidArgumentException
    {
        WeakReference<JMSSelectorFilter> selectorRef = _selectorCache.get(selectorString);
        JMSSelectorFilter selector = null;

        if(selectorRef == null || (selector = selectorRef.get())==null)
        {
            try
            {
                selector = new JMSSelectorFilter(selectorString);
            }
            catch (ParseException | SelectorParsingException | TokenMgrError e)
            {
                throw new AMQInvalidArgumentException("Cannot parse JMS selector \"" + selectorString + "\"", e);
            }
            _selectorCache.put(selectorString, new WeakReference<JMSSelectorFilter>(selector));
        }
        return selector;
    }

    public static boolean argumentsContainFilter(final Map<String, Object> args)
    {
        return argumentsContainNoLocal(args) || argumentsContainJMSSelector(args);
    }


    public static void removeFilters(final Map<String, Object> args)
    {
        args.remove(AMQPFilterTypes.JMS_SELECTOR.toString());
        args.remove(AMQPFilterTypes.NO_LOCAL.toString());
    }



    static boolean argumentsContainNoLocal(final Map<String, Object> args)
    {
        return args != null
                && args.containsKey(AMQPFilterTypes.NO_LOCAL.toString())
                && Boolean.TRUE.equals(args.get(AMQPFilterTypes.NO_LOCAL.toString()));
    }

    static boolean argumentsContainJMSSelector(final Map<String,Object> args)
    {
        return args != null && (args.get(AMQPFilterTypes.JMS_SELECTOR.toString()) instanceof String)
                       && ((String)args.get(AMQPFilterTypes.JMS_SELECTOR.toString())).trim().length() != 0;
    }

    public static MessageFilter createMessageFilter(final Map<String,Object> args, AMQQueue queue) throws AMQInvalidArgumentException
    {
        if(argumentsContainNoLocal(args))
        {
            MessageFilter filter = new NoLocalFilter(queue);

            if(argumentsContainJMSSelector(args))
            {
                filter = new CompoundFilter(filter, createJMSSelectorFilter(args));
            }
            return filter;
        }
        else
        {
            return createJMSSelectorFilter(args);
        }
    }

    @PluggableService
    public static final class NoLocalFilter implements MessageFilter
    {
        private final MessageSource _queue;

        private NoLocalFilter(MessageSource queue)
        {
            _queue = queue;
        }

        @Override
        public String getName()
        {
            return AMQPFilterTypes.NO_LOCAL.toString();
        }

        public boolean matches(Filterable message)
        {

            final Collection<? extends ConsumerImpl> consumers = _queue.getConsumers();
            for(ConsumerImpl c : consumers)
            {
                if(c.getSessionModel().getConnectionReference() == message.getConnectionReference())
                {
                    return false;
                }
            }
            return !consumers.isEmpty();
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o)
            {
                return true;
            }

            if (o == null || getClass() != o.getClass())
            {
                return false;
            }

            NoLocalFilter that = (NoLocalFilter) o;

            return _queue == null ? that._queue == null : _queue.equals(that._queue);
        }

        @Override
        public int hashCode()
        {
            return _queue != null ? _queue.hashCode() : 0;
        }


    }

    static final class CompoundFilter implements MessageFilter
    {
        private MessageFilter _noLocalFilter;
        private MessageFilter _jmsSelectorFilter;

        public CompoundFilter(MessageFilter filter, MessageFilter jmsSelectorFilter)
        {
            _noLocalFilter = filter;
            _jmsSelectorFilter = jmsSelectorFilter;
        }

        @Override
        public String getName()
        {
            return "";
        }

        public boolean matches(Filterable message)
        {
            return _noLocalFilter.matches(message) && _jmsSelectorFilter.matches(message);
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o)
            {
                return true;
            }
            if (o == null || getClass() != o.getClass())
            {
                return false;
            }

            CompoundFilter that = (CompoundFilter) o;

            if (_jmsSelectorFilter != null ? !_jmsSelectorFilter.equals(that._jmsSelectorFilter) : that._jmsSelectorFilter != null)
            {
                return false;
            }
            if (_noLocalFilter != null ? !_noLocalFilter.equals(that._noLocalFilter) : that._noLocalFilter != null)
            {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode()
        {
            int result = _noLocalFilter != null ? _noLocalFilter.hashCode() : 0;
            result = 31 * result + (_jmsSelectorFilter != null ? _jmsSelectorFilter.hashCode() : 0);
            return result;
        }


    }
}
