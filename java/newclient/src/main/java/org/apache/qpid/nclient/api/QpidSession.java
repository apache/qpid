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
package org.apache.qpid.nclient.api;

/**
 * The Helper classes are added in order to avoid the session class 
 * from having too many methods and an implementation becoming too
 * complicated. Having a lengthy class can impact readability and
 * manageability.
 */
public interface QpidSession
{
			
	public void open() throws QpidException;
	
	public void close() throws QpidException;
	
	public void resume() throws QpidException;
	
	public void suspend() throws QpidException;
	
	public void failover() throws QpidException;
	
	public QpidMessageProducer createProducer() throws QpidException;
	
	public QpidMessageConsumer createConsumer() throws QpidException;
	
	public QpidMessageHelper getMessageHelper() throws QpidException;
	
	public QpidExchangeHelper getExchangeHelper() throws QpidException;
	
	public QpidQueueHelper getQueueHelper() throws QpidException;
	
	public QpidTransactionHelper getTransactionHelper()throws QpidException;
}
