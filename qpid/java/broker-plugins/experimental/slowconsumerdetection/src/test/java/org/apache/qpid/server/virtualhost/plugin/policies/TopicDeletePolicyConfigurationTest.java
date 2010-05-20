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
package org.apache.qpid.server.virtualhost.plugin.policies;

import junit.framework.TestCase;
import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;

public class TopicDeletePolicyConfigurationTest extends TestCase
{

    public void testNoConfigNoDeletePersistent()
    {
        TopicDeletePolicyConfiguration config = new TopicDeletePolicyConfiguration();

        assertFalse("TopicDelete Configuration with no config should not delete persistent queues.",
                    config.deletePersistent());
    }


    public void testConfigDeletePersistent()
    {
        TopicDeletePolicyConfiguration config = new TopicDeletePolicyConfiguration();

        XMLConfiguration xmlconfig = new XMLConfiguration();

        xmlconfig.addProperty("delete-persistent","");

        // Create a CompositeConfiguration as this is what the broker uses
        CompositeConfiguration composite = new CompositeConfiguration();
        composite.addConfiguration(xmlconfig);

        try
        {
            config.setConfiguration("",composite);
        }
        catch (ConfigurationException e)
        {
            fail(e.getMessage());
        }

        assertTrue("A configured TopicDelete should delete persistent queues.",
                    config.deletePersistent());
    }

}
