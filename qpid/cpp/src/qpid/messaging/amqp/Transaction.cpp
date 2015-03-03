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

#include "Transaction.h"
#include "SessionContext.h"
#include "ConnectionContext.h"
#include "PnData.h"
#include <proton/engine.h>
#include <qpid/Exception.h>
#include <qpid/amqp/descriptors.h>
#include <qpid/messaging/exceptions.h>
#include <qpid/log/Statement.h>
#include "qpid/messaging/Message.h"

namespace qpid {
namespace messaging {
namespace amqp {

using namespace types;
using types::Exception;

namespace {
const std::string LOCAL_TRANSACTIONS("amqp:local-transactions");
const std::string TX_COORDINATOR("tx-transaction");
const std::string ADDRESS("tx-transaction;{link:{reliability:at-least-once}}");
}

Transaction::Transaction(pn_session_t* session) :
    SenderContext(session, TX_COORDINATOR, Address(ADDRESS), false), committing(false)
{}

void Transaction::clear() {
    id.clear();
    sendState.reset();
    acceptState.reset();
}

void Transaction::configure() {
    SenderContext::configure();
    pn_terminus_t* target = pn_link_target(sender);
    pn_terminus_set_type(target, PN_COORDINATOR);
    PnData(pn_terminus_capabilities(target)).putSymbol(LOCAL_TRANSACTIONS);
}

void Transaction::verify() {}

const std::string& Transaction::getTarget() const { return getName(); }

void Transaction::declare(SendFunction send, const SessionPtr& session) {
    committing = false;
    error.raise();
    clear();
    Variant declare = Variant::described(qpid::amqp::transaction::DECLARE_CODE, Variant::List());
    SenderContext::Delivery* delivery = 0;
    send(session, shared_from_this(), Message(declare), true, &delivery);
    setId(*delivery);
}

void Transaction::discharge(SendFunction send, const SessionPtr& session, bool fail) {
    error.raise();
    committing = !fail;
    try {
        // Send a discharge message to the remote coordinator.
        Variant::List dischargeList;
        dischargeList.push_back(Variant(id));
        dischargeList.push_back(Variant(fail));
        Variant discharge(dischargeList);
        discharge.setDescriptor(qpid::amqp::transaction::DISCHARGE_CODE);
        SenderContext::Delivery* delivery = 0;
        send(session, shared_from_this(), Message(discharge), true, &delivery);
        if (!delivery->accepted())
            throw TransactionAborted(delivery->error());
        committing = false;
    }
    catch(const TransactionError&) {
        throw;
    }
    catch(const Exception& e) {
        committing = false;
        throw TransactionAborted(e.what());
    }
}

// Set the transaction ID from the delivery returned by the remote coordinator.
void Transaction::setId(const SenderContext::Delivery& delivery)
{
    if (delivery.getToken() &&
        pn_delivery_remote_state(delivery.getToken()) == qpid::amqp::transaction::DECLARED_CODE)
    {
        pn_data_t* data = pn_disposition_data(pn_delivery_remote(delivery.getToken()));
        if (data && pn_data_next(data)) {
            size_t count = pn_data_get_list(data);
            if (count > 0) {
                pn_data_enter(data);
                pn_data_next(data);
                setId(PnData::string(pn_data_get_binary(data)));
                pn_data_exit(data);
                return;
            }
        }
    }
    throw TransactionError("No transaction ID returned by remote coordinator.");
}

void Transaction::setId(const std::string& id_) {
    id = id_;
    if (id.empty()) {
        clear();
    }
    else {
        // NOTE: The send and accept states are NOT described, the descriptor
        // is added in pn_delivery_update.
        Variant::List list;
        list.push_back(Variant(id, "binary"));
        sendState = Variant(list);

        Variant accepted = Variant::described(qpid::amqp::message::ACCEPTED_CODE, Variant::List());
        list.push_back(accepted);
        acceptState = Variant(list);
    }
}

types::Variant Transaction::getSendState() const {
    error.raise();
    return sendState;
}

void Transaction::acknowledge(pn_delivery_t* delivery)
{
    error.raise();
    PnData data(pn_disposition_data(pn_delivery_local(delivery)));
    data.put(acceptState);
    pn_delivery_update(delivery, qpid::amqp::transaction::TRANSACTIONAL_STATE_CODE);
    pn_delivery_settle(delivery);
}



}}} // namespace qpid::messaging::amqp
