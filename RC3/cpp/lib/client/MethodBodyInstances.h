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
#include <framing/amqp_framing.h>

#ifndef _MethodBodyInstances_h_
#define _MethodBodyInstances_h_

namespace qpid {
namespace client {

/**
 * A list of method body instances that can be used to compare against
 * incoming bodies.
 */
class MethodBodyInstances
{
private:
	qpid::framing::ProtocolVersion	version;
public:
	const qpid::framing::BasicCancelOkBody		basic_cancel_ok;
	const qpid::framing::BasicConsumeOkBody		basic_consume_ok;
	const qpid::framing::BasicDeliverBody		basic_deliver;
	const qpid::framing::BasicGetEmptyBody		basic_get_empty;
	const qpid::framing::BasicGetOkBody		basic_get_ok;
	const qpid::framing::BasicQosOkBody		basic_qos_ok;
	const qpid::framing::BasicReturnBody		basic_return;
	const qpid::framing::ChannelCloseBody		channel_close;
        const qpid::framing::ChannelCloseOkBody		channel_close_ok;
	const qpid::framing::ChannelFlowBody		channel_flow;
        const qpid::framing::ChannelOpenOkBody		channel_open_ok;
        const qpid::framing::ConnectionCloseBody	connection_close;
        const qpid::framing::ConnectionCloseOkBody	connection_close_ok;
        const qpid::framing::ConnectionOpenOkBody	connection_open_ok;
        const qpid::framing::ConnectionRedirectBody	connection_redirect;
        const qpid::framing::ConnectionStartBody	connection_start;
        const qpid::framing::ConnectionTuneBody		connection_tune;
	const qpid::framing::ExchangeDeclareOkBody	exchange_declare_ok;      
	const qpid::framing::ExchangeDeleteOkBody	exchange_delete_ok;
	const qpid::framing::QueueDeclareOkBody		queue_declare_ok;
	const qpid::framing::QueueDeleteOkBody		queue_delete_ok;
	const qpid::framing::QueueBindOkBody		queue_bind_ok;
	const qpid::framing::TxCommitOkBody		tx_commit_ok;
	const qpid::framing::TxRollbackOkBody		tx_rollback_ok;
	const qpid::framing::TxSelectOkBody		tx_select_ok;

    MethodBodyInstances(u_int8_t major, u_int8_t minor) :
    	version(major, minor),
        basic_cancel_ok(version),
        basic_consume_ok(version),
        basic_deliver(version),
        basic_get_empty(version),
        basic_get_ok(version),
        basic_qos_ok(version),
        basic_return(version),
        channel_close(version),
        channel_close_ok(version),
        channel_flow(version),
        channel_open_ok(version),
        connection_close(version),
        connection_close_ok(version),
        connection_open_ok(version),
        connection_redirect(version),
        connection_start(version),
        connection_tune(version),
        exchange_declare_ok(version),      
        exchange_delete_ok(version),
        queue_declare_ok(version),
        queue_delete_ok(version),
        queue_bind_ok(version),
        tx_commit_ok(version),
        tx_rollback_ok(version),
        tx_select_ok(version)
    {}

};

static MethodBodyInstances method_bodies(8, 0);

} // namespace client
} // namespace qpid

#endif
