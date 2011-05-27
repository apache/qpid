/*
 *
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

import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.configuration.plugins.SlowConsumerDetectionConfiguration;
import org.apache.qpid.server.configuration.plugins.SlowConsumerDetectionQueueConfiguration;
import org.apache.qpid.server.configuration.plugins.ConfigurationPlugin;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.plugins.Plugin;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.virtualhost.plugins.logging.SlowConsumerDetectionMessages;

public class SlowConsumerDetection extends VirtualHostHouseKeepingPlugin
{
    private SlowConsumerDetectionConfiguration _config;
    private ConfiguredQueueBindingListener _listener;

    public static class SlowConsumerFactory implements VirtualHostPluginFactory
    {
        public SlowConsumerDetection newInstance(VirtualHost vhost)
        {
            SlowConsumerDetectionConfiguration config = vhost.getConfiguration().getConfiguration(SlowConsumerDetectionConfiguration.class.getName());

            if (config == null)
            {
                return null;
            }

            SlowConsumerDetection plugin = new SlowConsumerDetection(vhost);
            plugin.configure(config);
            return plugin;
        }
    }

    /**
     * Configures the slow consumer disconnect plugin by adding a listener to each exchange on this
     * cirtual host to record all the configured queues in a cache for processing by the housekeeping
     * thread.
     * 
     * @see Plugin#configure(ConfigurationPlugin)
     */
    public void configure(ConfigurationPlugin config)
    {        
        _config = (SlowConsumerDetectionConfiguration) config;
        _listener = new ConfiguredQueueBindingListener(getVirtualHost().getName());
        for (AMQShortString exchangeName : getVirtualHost().getExchangeRegistry().getExchangeNames())
        {
            getVirtualHost().getExchangeRegistry().getExchange(exchangeName).addBindingListener(_listener);
        }
    }
    
    public SlowConsumerDetection(VirtualHost vhost)
    {
        super(vhost);
    }

    public void execute()
    {
        CurrentActor.get().message(SlowConsumerDetectionMessages.RUNNING());
        
        Set<AMQQueue> cache = _listener.getQueueCache();
        for (AMQQueue q : cache)
        {
            CurrentActor.get().message(SlowConsumerDetectionMessages.CHECKING_QUEUE(q.getName()));
            
            try
            {
                SlowConsumerDetectionQueueConfiguration config =
                    q.getConfiguration().getConfiguration(SlowConsumerDetectionQueueConfiguration.class.getName());
                if (checkQueueStatus(q, config))
                {
                    config.getPolicy().performPolicy(q);
                }
            }
            catch (Exception e)
            {
                // Don't throw exceptions as this will stop the house keeping task from running.
                _logger.error("Exception in SlowConsumersDetection for queue: " + q.getName(), e);
            }
        }

        CurrentActor.get().message(SlowConsumerDetectionMessages.COMPLETE());
    }

    public long getDelay()
    {
        return _config.getDelay();
    }

    public TimeUnit getTimeUnit()
    {
        return _config.getTimeUnit();
    }

    /**
     * Check the depth,messageSize,messageAge,messageCount values for this q
     *
     * @param q      the queue to check
     * @param config the queue configuration to compare against the queue state
     *
     * @return true if the queue has reached a threshold.
     */
    private boolean checkQueueStatus(AMQQueue q, SlowConsumerDetectionQueueConfiguration config)
    {
        if (config != null)
        {
            _logger.info("Retrieved Queue(" + q.getName() + ") Config:" + config);

            int count = q.getMessageCount();

            // First Check message counts
            if ((config.getMessageCount() != 0 && count >= config.getMessageCount()) ||
                // The check queue depth
                (config.getDepth() != 0 && q.getQueueDepth() >= config.getDepth()) ||
                // finally if we have messages on the queue check Arrival time.
                // We must check count as OldestArrival time is Long.MAX_LONG when
                // there are no messages.
                (config.getMessageAge() != 0 &&
                 ((count > 0) && q.getOldestMessageArrivalTime() >= config.getMessageAge())))
            {
                
                if (_logger.isDebugEnabled())
                {
                    _logger.debug("Detected Slow Consumer on Queue(" + q.getName() + ")");
                    _logger.debug("Queue Count:" + q.getMessageCount() + ":" + config.getMessageCount());
                    _logger.debug("Queue Depth:" + q.getQueueDepth() + ":" + config.getDepth());
                    _logger.debug("Queue Arrival:" + q.getOldestMessageArrivalTime() + ":" + config.getMessageAge());
                }

                return true;
            }
        }
        return false;
    }

}
