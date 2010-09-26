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
package org.apache.qpid.server.virtualhost.plugins;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.apache.log4j.Logger;
import org.apache.qpid.server.binding.Binding;
import org.apache.qpid.server.configuration.plugins.SlowConsumerDetectionQueueConfiguration;
import org.apache.qpid.server.exchange.AbstractExchange;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.exchange.Exchange.BindingListener;
import org.apache.qpid.server.queue.AMQQueue;

/**
 * This is a listener that caches queues that are configured for slow consumer disconnection.
 * 
 * There should be one listener per virtual host, which can be added to all exchanges on
 * that host.
 * 
 * TODO In future, it will be possible to configure the policy at runtime, so only the queue
 * itself is cached, and the configuration looked up by the housekeeping thread. This means
 * that there may be occasions where the copy of the cache contents retrieved by the thread
 * does not contain queues that are configured, or that configured queues are not present.
 * 
 * @see BindingListener
 */
public class ConfiguredQueueBindingListener implements BindingListener
{
    private static final Logger _log = Logger.getLogger(ConfiguredQueueBindingListener.class);
    
    private String _vhostName;
    private Set<AMQQueue> _cache = Collections.synchronizedSet(new HashSet<AMQQueue>());
    
    public ConfiguredQueueBindingListener(String vhostName)
    {
        _vhostName = vhostName;
    }

    /**
     * @see BindingListener#bindingAdded(Exchange, Binding)
     */
    public void bindingAdded(Exchange exchange, Binding binding)
    {
        processBinding(binding);
    }

    /**
     * @see BindingListener#bindingRemoved(Exchange, Binding)
     */
    public void bindingRemoved(Exchange exchange, Binding binding)
    {
        processBinding(binding);
    }
    
    private void processBinding(Binding binding)
    {
        AMQQueue queue = binding.getQueue();
        
        SlowConsumerDetectionQueueConfiguration config =
            queue.getConfiguration().getConfiguration(SlowConsumerDetectionQueueConfiguration.class.getName());
        if (config != null)
        {
            _cache.add(queue);
        }
        else
        {
            _cache.remove(queue);
        }
    }
    
    /**
     * Lookup and return the cache of configured {@link AMQQueue}s.
     * 
	 * Note that when accessing the cached queues, the {@link Iterator} is not thread safe
	 * (see the {@link Collections#synchronizedSet(Set)} documentation) so a copy of the
	 * cache is returned.
     * 
     * @return a copy of the cached {@link java.util.Set} of queues
     */
    public Set<AMQQueue> getQueueCache()
    {
        return new HashSet<AMQQueue>(_cache);
    }
}
