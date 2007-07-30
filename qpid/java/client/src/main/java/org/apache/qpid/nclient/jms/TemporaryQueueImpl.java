/* Licensed to the Apache Software Foundation (ASF) under one
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
 */
package org.apache.qpid.nclient.jms;

import javax.jms.TemporaryQueue;
import javax.jms.JMSException;

/**
 * Implements TemporaryQueue
 */
public class TemporaryQueueImpl extends QueueImpl implements TemporaryQueue, TemporaryDestination
{
    /**
     * Indicates whether this temporary queue is deleted.
     */
    private boolean _isDeleted = false;

    //--- constructor

    public TemporaryQueueImpl()
    {
        // temporary destinations do not have names and are not registered in the JNDI namespace.
        super("NAME_NOT_SET");
    }

    //-- TemporaryDestination Interface
    public boolean isdeleted()
    {
        return _isDeleted;
    }

    //-- TemporaryTopic Interface
    public void delete() throws JMSException
    {
        // todo delete this temporary queue
        _isDeleted = true;
    }
}
