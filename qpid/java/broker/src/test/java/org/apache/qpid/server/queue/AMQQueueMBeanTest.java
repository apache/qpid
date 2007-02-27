/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.server.queue;

import junit.framework.TestCase;
import org.apache.log4j.Logger;

public class AMQQueueMBeanTest extends TestCase{

    private static final Logger _logger = Logger.getLogger(AMQQueueMBeanTest.class);

    protected AMQTestQueue _queue;
    protected AMQQueueMBean _queueMBean;

    protected void setUp() throws Exception
    {
        super.setUp();
        
        //create a test queue for this class
        _queue = new AMQTestQueue("TestQueue");
        _queueMBean = new AMQQueueMBean(_queue);

    }

    public void testGetQueueDepth()
    {
        //set queue depth to a known value
        _queue.setQueueDepth(10240L);

        assertEquals("Mismatched queue depths",new Long(10240>>10),_queueMBean.getQueueDepth());
        
    }


}
