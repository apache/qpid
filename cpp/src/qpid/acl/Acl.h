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



#include "qpid/shared_ptr.h"
#include "qpid/RefCounted.h"
#include "qpid/broker/AclModule.h"
#include <map>
#include <string>


namespace qpid {
namespace broker {
class Broker;
}

namespace acl {

struct AclValues {
    public:
	bool noEnforce;
    std::string aclFile;

    AclValues() {noEnforce = false; aclFile = "policy.acl"; }
};


class Acl : public broker::AclModule, public RefCounted 
{

private:
   acl::AclValues aclValues;
   broker::Broker* broker;
   bool transferAcl;


public:
   Acl (AclValues& av, broker::Broker& b);

   void initialize();
   
   inline virtual bool doTransferAcl() {return transferAcl;};
   
   // create specilied authorise methods for cases that need faster matching as needed.
   virtual bool authorise(std::string id, acl::Action action, acl::ObjectType objType, std::string name, std::map<std::string, std::string>* params);
   virtual bool authorise(std::string id, acl::Action action, acl::ObjectType objType, std::string ExchangeName, std::string RoutingKey);

   virtual ~Acl();
private:
   std::string printAction(acl::Action action);
   std::string printObjType(acl::ObjectType objType);
   bool result(AclResult aclreslt, std::string id, acl::Action action, acl::ObjectType objType, std::string name);
   bool readAclFile();
      
};


    
}} // namespace qpid::acl

#endif // QPID_ACL_ACL_H
