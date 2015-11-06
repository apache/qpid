#ifndef QPID_BROKER_AMQP_AUTHORISE_H
#define QPID_BROKER_AMQP_AUTHORISE_H

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
#include <string>
#include <boost/shared_ptr.hpp>

namespace qpid {
namespace broker {
class AclModule;
class Exchange;
class Message;
class Queue;
namespace amqp {
class Filter;

/**
 * Class to handle authorisation requests (and hide the ACL mess behind)
 */
class Authorise
{
  public:
    Authorise(const std::string& user, AclModule*);
    void access(const std::string& name);
    void access(boost::shared_ptr<Exchange>);
    void access(boost::shared_ptr<Queue>);
    void incoming(boost::shared_ptr<Exchange>);
    void incoming(boost::shared_ptr<Queue>);
    void outgoing(boost::shared_ptr<Exchange>, boost::shared_ptr<Queue>, const Filter&);
    void outgoing(boost::shared_ptr<Queue>);
    void route(boost::shared_ptr<Exchange>, const Message&);
    void interlink();
    /**
     * Used to determine whether the user has access permission for a
     * given node name. If a specific type of node was requested, only
     * acces to that type is checked. Otherwise access to either queue
     * or exchange is required.
     */
    void access(const std::string& name, bool queueRequested, bool exchangeRequested);
  private:
    const std::string user;
    AclModule* const acl;

};
}}} // namespace qpid::broker::amqp

#endif  /*!QPID_BROKER_AMQP_AUTHORISE_H*/
