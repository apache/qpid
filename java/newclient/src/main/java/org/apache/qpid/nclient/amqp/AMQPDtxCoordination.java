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
package org.apache.qpid.nclient.amqp;

import org.apache.qpid.framing.DtxCoordinationCommitBody;
import org.apache.qpid.framing.DtxCoordinationForgetBody;
import org.apache.qpid.framing.DtxCoordinationGetTimeoutBody;
import org.apache.qpid.framing.DtxCoordinationPrepareBody;
import org.apache.qpid.framing.DtxCoordinationRecoverBody;
import org.apache.qpid.framing.DtxCoordinationRollbackBody;
import org.apache.qpid.framing.DtxCoordinationSetTimeoutBody;
import org.apache.qpid.nclient.core.AMQPException;

public interface AMQPDtxCoordination
{
	public void commit(DtxCoordinationCommitBody dtxCoordinationCommitBody,AMQPCallBack cb) throws AMQPException;
	
	public void forget(DtxCoordinationForgetBody dtxCoordinationForgetBody,AMQPCallBack cb) throws AMQPException;
	
	public void getTimeOut(DtxCoordinationGetTimeoutBody dtxCoordinationGetTimeoutBody,AMQPCallBack cb) throws AMQPException;
	
	public void prepare(DtxCoordinationPrepareBody dtxCoordinationPrepareBody,AMQPCallBack cb) throws AMQPException;
	
	public void recover(DtxCoordinationRecoverBody dtxCoordinationRecoverBody,AMQPCallBack cb) throws AMQPException;
	
	public void rollback(DtxCoordinationRollbackBody dtxCoordinationRollbackBody,AMQPCallBack cb) throws AMQPException;
	
	public void setTimeOut(DtxCoordinationSetTimeoutBody dtxCoordinationSetTimeoutBody,AMQPCallBack cb) throws AMQPException;
}
