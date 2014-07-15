#ifndef QPID_ACLMODULE_ACL_H
#define QPID_ACLMODULE_ACL_H


/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

#include "qpid/acl/AclLexer.h"
#include <map>
#include <string>

namespace qpid {
namespace broker {

    class Connection;

    class AclModule
    {

    public:

        // Some ACLs are invoked on every message transfer.
        // doTransferAcl prevents time consuming ACL calls on a per-message basis.
        virtual bool doTransferAcl()=0;

        virtual uint16_t getMaxConnectTotal()=0;

        virtual bool userAclRules()=0;

        virtual bool authorise(
            const std::string&                    id,
            const acl::Action&                    action,
            const acl::ObjectType&                objType,
            const std::string&                    name,
            std::map<acl::Property, std::string>* params=0)=0;

        virtual bool authorise(
            const std::string&      id,
            const acl::Action&      action,
            const acl::ObjectType&  objType,
            const std::string&      ExchangeName,
            const std::string&      RoutingKey)=0;

        // Add specialized authorise() methods as required.

        /** Approve connection by counting connections total, per-IP, and
         *  per-user.
         */
        virtual bool approveConnection (const Connection& connection)=0;

        /** Approve queue creation by counting per-user.
         */
        virtual bool approveCreateQueue(const std::string& userId, const std::string& queueName)=0;
        virtual void recordDestroyQueue(const std::string& queueName)=0;

        virtual ~AclModule() {};
    };
}} // namespace qpid::broker

#endif // QPID_ACLMODULE_ACL_H
