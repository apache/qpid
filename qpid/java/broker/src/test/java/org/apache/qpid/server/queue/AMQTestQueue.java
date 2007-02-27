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

import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.AMQException;

//Skeleton test AMQQueue class implemented for use by AMQQueueMBeanTest unit tests
public class AMQTestQueue extends AMQQueue{

    protected long _queueDepth;

    public AMQTestQueue(String queueName) throws AMQException {
        super(queueName,false,"AMQTestQueue",false,
                ApplicationRegistry.getInstance().getQueueRegistry());
    }

    //overriden from super class to allow simpler unit testing
    public long getQueueDepth()
    {
        return _queueDepth;
    }

    //allows test classes to set the queue depth for test
    public void setQueueDepth(long depth)
    {
        _queueDepth = depth;
    }
}
