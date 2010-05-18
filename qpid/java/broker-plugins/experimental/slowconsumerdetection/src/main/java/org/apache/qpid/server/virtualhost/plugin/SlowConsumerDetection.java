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

import org.apache.log4j.Logger;
import org.apache.qpid.server.configuration.plugin.SlowConsumerDetectionConfiguration;
import org.apache.qpid.server.configuration.plugin.SlowConsumerDetectionQueueConfiguration;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.virtualhost.plugins.VirtualHostPlugin;
import org.apache.qpid.server.virtualhost.plugins.VirtualHostPluginFactory;
import org.apache.qpid.slowconsumerdetection.policies.SlowConsumerPolicyPlugin;

class SlowConsumerDetection extends VirtualHostPlugin
{
    Logger _logger = Logger.getLogger(SlowConsumerDetection.class);
    private VirtualHost _virtualhost;
    private SlowConsumerDetectionConfiguration _config;
    private SlowConsumerPolicyPlugin _policy;

    public static class SlowConsumerFactory implements VirtualHostPluginFactory
    {
        public VirtualHostPlugin newInstance(VirtualHost vhost)
        {
            return new SlowConsumerDetection(vhost);
        }
    }

    public SlowConsumerDetection(VirtualHost vhost)
    {
        _virtualhost = vhost;
        _config = vhost.getConfiguration().getConfiguration(SlowConsumerDetectionConfiguration.class);
        if (_config == null)
        {
            throw new IllegalArgumentException("Plugin has not been configured");
        }

    }

    @Override
    public void execute()
    {
        _logger.info("Starting the SlowConsumersDetection job");
        try
        {
            for (AMQQueue q : _virtualhost.getQueueRegistry().getQueues())
            {
                _logger.debug("Checking consumer status for queue: "
                              + q.getName());
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
            _logger.info("SlowConsumersDetection job completed.");
        }
        catch (Exception e)
        {
            _logger.error("SlowConsumersDetection job failed: " + e.getMessage(), e);
        }
        catch (Error e)
        {
            _logger.error("SlowConsumersDetection job failed with error: " + e.getMessage(), e);
        }
    }

    public long getDelay()
    {
        return _config.getDelay();
    }

    public String getTimeUnit()
    {
        return _config.getTimeUnit();
    }

    /**
     * Check the depth,messageSize,messageAge,messageCount values for this q
     *
     * @param q      the queue to check
     * @param config
     *
     * @return true if the queue has reached a threshold.
     */
    private boolean checkQueueStatus(AMQQueue q, SlowConsumerDetectionQueueConfiguration config)
    {

        _logger.info("Retrieved Queue(" + q.getName() + ") Config:" + config);

        return config != null &&
               (q.getMessageCount() >= config.getMessageCount() ||
                q.getQueueDepth() >= config.getDepth() ||
                q.getOldestMessageArrivalTime() >= config.getMessageAge());
    }
}
