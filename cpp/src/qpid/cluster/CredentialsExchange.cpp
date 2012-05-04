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
#include "CredentialsExchange.h"
#include "Cluster.h"
#include "qpid/broker/ConnectionState.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/sys/Time.h"

namespace qpid {
namespace cluster {

using namespace std;

const string CredentialsExchange::NAME=("qpid.cluster-credentials");

namespace {
const string ANONYMOUS_MECH("ANONYMOUS");
const string ANONYMOUS_USER("anonymous");

string effectiveUserId(const string& username, const string& mechanism) {
    if (mechanism == ANONYMOUS_MECH && username.empty())
        return ANONYMOUS_USER;
    else
        return username;
}
}

CredentialsExchange::CredentialsExchange(Cluster& cluster)
    : broker::Exchange(NAME, &cluster),
      username(effectiveUserId(cluster.getSettings().username,
                               cluster.getSettings().mechanism)),
      timeout(120*sys::TIME_SEC),
      authenticate(cluster.getBroker().getOptions().auth)
{}

static const string anonymous("anonymous");

bool CredentialsExchange::check(MemberId member) {
    sys::Mutex::ScopedLock l(lock);
    Map::iterator i = map.find(member);
    if (i == map.end()) return false;
    bool valid = (sys::Duration(i->second, sys::AbsTime::now()) < timeout);
    map.erase(i);
    return valid;
}

void CredentialsExchange::route(broker::Deliverable& msg) {
    const framing::FieldTable* args = msg.getMessage().getApplicationHeaders();
    sys::Mutex::ScopedLock l(lock);
    const broker::ConnectionState* connection =
        static_cast<const broker::ConnectionState*>(msg.getMessage().getPublisher());
    if (authenticate && !connection->isAuthenticatedUser(username))
        throw framing::UnauthorizedAccessException(
            QPID_MSG("Unauthorized user " << connection->getUserId() << " for " << NAME
                     << ", should be " << username));
    if (!args || !args->isSet(NAME))
        throw framing::InvalidArgumentException(
            QPID_MSG("Invalid message received by " << NAME));
    MemberId member(args->getAsUInt64(NAME));
    map[member] = sys::AbsTime::now();
}

string CredentialsExchange::getType() const { return NAME; }

namespace {
void throwIllegal() {
    throw framing::NotAllowedException(
        QPID_MSG("Illegal use of " << CredentialsExchange::NAME+" exchange"));
}
}

bool CredentialsExchange::bind(boost::shared_ptr<broker::Queue> , const string& /*routingKey*/, const framing::FieldTable* ) { throwIllegal(); return false; }
bool CredentialsExchange::unbind(boost::shared_ptr<broker::Queue> , const string& /*routingKey*/, const framing::FieldTable* ) { throwIllegal(); return false; }
bool CredentialsExchange::isBound(boost::shared_ptr<broker::Queue>, const string* const /*routingKey*/, const framing::FieldTable* const ) { throwIllegal(); return false; }


}} // Namespace qpid::cluster
