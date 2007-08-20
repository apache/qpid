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
package org.apache.qpidity.url;

import org.apache.qpidity.BrokerDetails;

import java.util.List;

/**
 * The format of the Qpid URL is based on the AMQP one.
 * The grammar is as follows:
 * <p> qpid_url          = "qpid:" [user_props] prot_addr_list ["/" future-parameters]
 * <p> prot_addr_list 	 = [prot_addr ","]* prot_addr
 * <p> prot_addr         = tcp_prot_addr | tls_prot_addr | future_prot_addr
 * <p> tcp_prot_addr     = tcp_id tcp_addr
 * <p> tcp_id            = "tcp:" | ""
 * <p> tcp_addr          = [host [":" port] ]
 * <p> host              = <as per [2]>
 * <p> port              = number
 * <p> tls_prot_addr     = tls_id tls_addr
 * <p> tls_id            = "tls:" | ""
 * <p> tls_addr          = [host [":" port] ]
 * <p> future_prot_addr  = future_prot_id future_prot_addr
 * <p> future_prot_id    = <placeholder, must end in ":". Example "sctp:">
 * <p> future_prot_addr  = <placeholder, protocl-specific address>
 * <p> future_parameters = <placeholder, not used in failover addresses>
 */
public interface QpidURL
{
    /**
     * Get all the broker details
     *
     * @return A list of BrokerDetails.
     */
    public List<BrokerDetails> getAllBrokerDetails();

    /**
     * Get this URL string form
     * @return This URL string form.
     */
    public String getURL();
}
