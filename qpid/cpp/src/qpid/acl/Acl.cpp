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

#include "qpid/acl/Acl.h"
#include "qpid/acl/AclData.h"

#include "qpid/broker/Broker.h"
#include "qpid/Plugin.h"
#include "qpid/Options.h"
#include "qpid/shared_ptr.h"
#include "qpid/log/Logger.h"

#include <map>

#include <boost/utility/in_place_factory.hpp>

namespace qpid {
namespace acl {

using namespace std;

   Acl::Acl (AclValues& av, broker::Broker& b): aclValues(av), broker(&b), transferAcl(false)
   {
       if (!readAclFile()) throw Exception("Could not read ACL file");
	   QPID_LOG(info, "ACL Plugin loaded");

   }

   bool Acl::authorise(const std::string& id, const Action& action, const ObjectType& objType, const std::string& name, std::map<Property, std::string>* params)
   {
      if (aclValues.noEnforce) return true;
      boost::shared_ptr<AclData> dataLocal = data;  //rcu copy
      
      // add real ACL check here... 
      AclResult aclreslt = dataLocal->lookup(id,action,objType,name,params);
	  
	  
	  return result(aclreslt, id, action, objType, name); 
   }

   bool Acl::authorise(const std::string& id, const Action& action, const ObjectType& objType, const std::string& ExchangeName, const std::string& RoutingKey)
   {
      if (aclValues.noEnforce) return true;
      boost::shared_ptr<AclData> dataLocal = data;  //rcu copy
      
      // only use dataLocal here...
      AclResult aclreslt = dataLocal->lookup(id,action,objType,ExchangeName,RoutingKey);  
	  
	  return result(aclreslt, id, action, objType, ExchangeName); 
   }

   
   bool Acl::result(const AclResult& aclreslt, const std::string& id, const Action& action, const ObjectType& objType, const std::string& name)
   {
	  switch (aclreslt)
	  {
	  case ALLOWLOG:
          QPID_LOG(info, "ACL Allow id:" << id <<" action:" << AclHelper::getActionStr(action) << " ObjectType:" << AclHelper::getObjectTypeStr(objType) << " Name:" << name );  
	  case ALLOW:
	      return true;
	  case DENY:
	      return false;
	  case DENYLOG:
	  default:
	      QPID_LOG(info, "ACL Deny id:" << id << " action:" << AclHelper::getActionStr(action) << " ObjectType:" << AclHelper::getObjectTypeStr(objType) << " Name:" << name);  
	      return false;
	  }
      return false;  
   }
      
   bool Acl::readAclFile()
   {
      // only set transferAcl = true if a rule implies the use of ACL on transfer, else keep false for permormance reasons.
      return readAclFile(aclValues.aclFile);
   }

   bool Acl::readAclFile(std::string& aclFile) {
      boost::shared_ptr<AclData> d(new AclData);
      AclReader ar;
      if (ar.read(aclFile, d))
          return false;
 
      data = d;
	  transferAcl = data->transferAcl; // any transfer ACL
      return true;
   }

   Acl::~Acl(){}


    
}} // namespace qpid::acl
