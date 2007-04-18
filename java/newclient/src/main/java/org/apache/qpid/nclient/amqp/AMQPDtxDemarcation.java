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

import org.apache.qpid.framing.DtxDemarcationEndBody;
import org.apache.qpid.framing.DtxDemarcationEndOkBody;
import org.apache.qpid.framing.DtxDemarcationSelectBody;
import org.apache.qpid.framing.DtxDemarcationSelectOkBody;
import org.apache.qpid.framing.DtxDemarcationStartBody;
import org.apache.qpid.framing.DtxDemarcationStartOkBody;
import org.apache.qpid.nclient.core.AMQPException;

public interface AMQPDtxDemarcation
{
	public DtxDemarcationSelectOkBody select(DtxDemarcationSelectBody dtxDemarcationSelectBody) throws AMQPException;
	
	public DtxDemarcationStartOkBody start(DtxDemarcationStartBody dtxDemarcationStartBody) throws AMQPException;
	
	public DtxDemarcationEndOkBody end(DtxDemarcationEndBody dtxDemarcationEndBody) throws AMQPException;
}
