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
package org.apache.qpid.jms;

import javax.jms.JMSException;
import javax.jms.Queue;

public class QpidQueue extends QpidDestination implements Queue 
{
	public QpidQueue()
	{
		super(DestinationType.QUEUE);
	}

	public QpidQueue(String str) throws JMSException
	{
		super(DestinationType.QUEUE);
        setDestinationString(str);
	}

	@Override
	public String getQueueName() throws JMSException 
	{
       return address.getName();
	}

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }

        if (!(obj instanceof QpidQueue))
        {
            return false;
        }

        QpidQueue queue = (QpidQueue)obj;
        try
        {
            return getQueueName().equals(queue.getQueueName());
        }
        catch (Exception e)
        {
            return false;
        }
    }
}
