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
package org.apache.qpid.systest;

import org.apache.commons.configuration.ConfigurationException;

import javax.naming.NamingException;
import java.io.IOException;

/**
 * This Topic test extends the Global queue test so it will run all the topic
 * and subscription tests.
 *
 * We redefine the CONFIG_SECTION here so that the configuration is written
 * against a topic element.
 *
 * To complete the migration to testing 'topic' elements we also override
 * the setConfig to use the test name as the topic name.
 *
 */
public class TopicTest extends GlobalQueuesTest
{
    private int _count=0;

    @Override
    public void setUp() throws Exception
    {
        CONFIG_SECTION = ".topics.topic";
        super.setUp();
    }

    /**
     * Add configuration for the queue that relates just to this test.
     * We use the getTestQueueName() as our subscription. To ensure the
     * config sections do not overlap we identify each section with a _count
     * value.
     *
     * This would allow each test to configure more than one section.
     *
     * @param property to set
     * @param value the value to set
     * @param deleteDurable should deleteDurable be set.
     * @throws NamingException
     * @throws IOException
     * @throws ConfigurationException
     */
    @Override
    public void setConfig(String property, String value, boolean deleteDurable) throws NamingException, IOException, ConfigurationException
    {
        setProperty(CONFIG_SECTION + "("+_count+").name", getName());

        setProperty(CONFIG_SECTION + "("+_count+").slow-consumer-detection." +
                    "policy.name", "TopicDelete");

        setProperty(CONFIG_SECTION + "("+_count+").slow-consumer-detection." +
                    property, value);

        if (deleteDurable)
        {
            setProperty(CONFIG_SECTION + "("+_count+").slow-consumer-detection." +
                        "policy.topicdelete.delete-persistent", "");
        }
        _count++;
    }

    
}
