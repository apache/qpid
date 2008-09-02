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
   boost::shared_ptr<AclData> data;


public:
   Acl (AclValues& av, broker::Broker& b);

   void initialize();
   
   inline virtual bool doTransferAcl() {return transferAcl;};
   
   // create specilied authorise methods for cases that need faster matching as needed.
   virtual bool authorise(const std::string& id, const Action& action, const ObjectType& objType, const std::string& name, std::map<Property, std::string>* params);
   virtual bool authorise(const std::string& id, const Action& action, const ObjectType& objType, const std::string& ExchangeName,const std::string& RoutingKey);

   virtual ~Acl();
private:
   bool result(const AclResult& aclreslt, const std::string& id, const Action& action, const ObjectType& objType, const std::string& name);
   bool readAclFile();
   bool readAclFile(std::string& aclFile);      
};


    
}} // namespace qpid::acl

#endif // QPID_ACL_ACL_H
