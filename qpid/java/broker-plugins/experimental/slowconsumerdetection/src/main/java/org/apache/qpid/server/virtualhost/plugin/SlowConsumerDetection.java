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
package org.apache.qpid.server.virtualhost.plugin;

import java.util.concurrent.TimeUnit;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.qpid.server.configuration.plugin.SlowConsumerDetectionConfiguration;
import org.apache.qpid.server.configuration.plugin.SlowConsumerDetectionQueueConfiguration;
import org.apache.qpid.server.configuration.plugins.ConfigurationPlugin;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.virtualhost.plugin.logging.SlowConsumerDetectionMessages;
import org.apache.qpid.server.virtualhost.plugins.VirtualHostHouseKeepingPlugin;
import org.apache.qpid.server.virtualhost.plugins.VirtualHostPluginFactory;

class SlowConsumerDetection extends VirtualHostHouseKeepingPlugin
{
    private SlowConsumerDetectionConfiguration _config;

    public static class SlowConsumerFactory implements VirtualHostPluginFactory
    {
        public Class<SlowConsumerDetection> getPluginClass()
        {
            return SlowConsumerDetection.class;
        }

        public String getPluginName()
        {
            return SlowConsumerDetection.class.getName();
        }

        public SlowConsumerDetection newInstance(VirtualHost vhost) throws ConfigurationException
        {
            SlowConsumerDetection plugin = new SlowConsumerDetection(vhost);
            plugin.configure(vhost.getConfiguration());
            return plugin;
        }
    }

    public void configure(ConfigurationPlugin config) throws ConfigurationException
    {        
        _config = config.getConfiguration(SlowConsumerDetectionConfiguration.class);
        
        if (_config == null)
        {
            throw new IllegalArgumentException("Plugin has not been configured");
        }
    }
    
    public SlowConsumerDetection(VirtualHost vhost)
    {
        super(vhost);
    }

    @Override
    public void execute()
    {
        SlowConsumerDetectionMessages.SCD_RUNNING();

        for (AMQQueue q : _virtualhost.getQueueRegistry().getQueues())
        {
            SlowConsumerDetectionMessages.SCD_CHECKING_QUEUE(q.getName());
            try
            {
                SlowConsumerDetectionQueueConfiguration config =
                            q.getConfiguration().getConfiguration(SlowConsumerDetectionQueueConfiguration.class);

                if (checkQueueStatus(q, config))
                {
                    config.getPolicy().performPolicy(q);
                }
            }
            catch (Exception e)
            {
                _logger.error("Exception in SlowConsumersDetection " +
                              "for queue: " +
                              q.getNameShortString().toString(), e);
                //Don't throw exceptions as this will stop the
                // house keeping task from running.
            }
        }

        SlowConsumerDetectionMessages.SCD_COMPLETE();
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
            if ((config.getMessageCount() != 0 && q.getMessageCount() >= config.getMessageCount()) ||
                    (config.getDepth() != 0 && q.getQueueDepth() >= config.getDepth()) ||
                    (config.getMessageAge() != 0 && q.getOldestMessageArrivalTime() >= config.getMessageAge()))
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
