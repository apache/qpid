package org.apache.qpidity.api;

import org.apache.qpidity.ApplicationProperties;
import org.apache.qpidity.DeliveryProperties;

/*
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
 */

public interface Message
{
	public ApplicationProperties getApplicationProperties();

	public DeliveryProperties getDeliveryProperties();
        
	/**
	 * This will abstract the underlying message data.
	 * The Message implementation may not hold all message
	 * data in memory (especially in the case of large messages)
	 * 
	 * The appendData function might write data to 
	 * <ul>
	 * <li> Memory (Ex: ByteBuffer)
	 * <li> To Disk
	 * <li> To Socket (Stream)
	 * </ul>
	 * @param src
	 */
	public void appendData(byte[] src);

	/**
	 * This will abstract the underlying message data.
	 * The Message implementation may not hold all message
	 * data in memory (especially in the case of large messages)
	 * 
	 * The read function might copy data from a 
	 * <ul>
	 * <li> From memory (Ex: ByteBuffer)
	 * <li> From Disk
	 * <li> From Socket as and when it gets streamed
	 * </ul>
	 * @param target
	 */
    public void readData(byte[] target);   

}

