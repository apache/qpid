#ifndef QPID_ACL_ACL_H
#define QPID_ACL_ACL_H


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



#include "qpid/acl/AclReader.h"
#include "qpid/AclHost.h"
#include "qpid/RefCounted.h"
#include "qpid/broker/AclModule.h"
#include "qpid/management/Manageable.h"
#include "qpid/management/ManagementAgent.h"
#include "qmf/org/apache/qpid/acl/Acl.h"
#include "qpid/sys/Mutex.h"

#include <boost/shared_ptr.hpp>
#include <map>
#include <string>


namespace qpid {
namespace broker {
class Broker;
class Connection;
}

namespace acl {
class ConnectionCounter;
class ResourceCounter;

struct AclValues {
    std::string aclFile;
    uint16_t    aclMaxConnectPerUser;
    uint16_t    aclMaxConnectPerIp;
    uint16_t    aclMaxConnectTotal;
    uint16_t    aclMaxQueuesPerUser;
};


class Acl : public broker::AclModule, public RefCounted, public management::Manageable
{

private:
    acl::AclValues                       aclValues;
    broker::Broker*                      broker;
    bool                                 transferAcl;
    boost::shared_ptr<AclData>           data;
    qmf::org::apache::qpid::acl::Acl::shared_ptr mgmtObject;
    qpid::management::ManagementAgent*   agent;
    mutable qpid::sys::Mutex             dataLock;
    boost::shared_ptr<ConnectionCounter> connectionCounter;
    boost::shared_ptr<ResourceCounter>   resourceCounter;
    bool                                 userRules;

public:
    Acl (AclValues& av, broker::Broker& b);

    /** reportConnectLimit
     * issue management counts and alerts for denied connections
     */
    void reportConnectLimit(const std::string user, const std::string addr);
    void reportQueueLimit(const std::string user, const std::string queueName);

    inline virtual bool doTransferAcl() {
        return transferAcl;
    };

    inline virtual uint16_t getMaxConnectTotal() {
        return aclValues.aclMaxConnectTotal;
    };

    inline virtual bool userAclRules() {
      return userRules;
    };

// create specilied authorise methods for cases that need faster matching as needed.
    virtual bool authorise(
        const std::string&               id,
        const Action&                    action,
        const ObjectType&                objType,
        const std::string&               name,
        std::map<Property, std::string>* params=0);

    virtual bool authorise(
        const std::string&               id,
        const Action&                    action,
        const ObjectType&                objType,
        const std::string&               ExchangeName,
        const std::string&               RoutingKey);

    // Resource quota tracking
    virtual bool approveConnection(const broker::Connection& connection);
    virtual bool approveCreateQueue(const std::string& userId, const std::string& queueName);
    virtual void recordDestroyQueue(const std::string& queueName);

    virtual ~Acl();
private:
    bool result(
        const AclResult&   aclreslt,
        const std::string& id,
        const Action&      action,
        const ObjectType&  objType,
        const std::string& name);
    bool readAclFile(std::string& errorText);
    bool readAclFile(std::string& aclFile, std::string& errorText);
    void loadEmptyAclRuleset();
    Manageable::status_t lookup       (management::Args& args, std::string& text);
    Manageable::status_t lookupPublish(management::Args& args, std::string& text);
    virtual qpid::management::ManagementObject::shared_ptr GetManagementObject(void) const;
    virtual management::Manageable::status_t ManagementMethod (uint32_t methodId, management::Args& args, std::string& text);

};

}} // namespace qpid::acl

#endif // QPID_ACL_ACL_H
