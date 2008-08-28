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

#include "Acl.h"


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

   std::string Acl::printAction(acl::Action action)
   {
      switch (action)
	  {
	   case CONSUME: return "Consume";
	   case PUBLISH: return "Publish";
	   case CREATE: return "Create";
	   case ACCESS: return "Access";
	   case BIND: return "Bind";
	   case UNBIND: return "Unbind";
	   case DELETE: return "Delete";
	   case PURGE: return "Purge";
	   case UPDATE: return "Update";
	   default: return "Unknown";
	  }
   }
   
   std::string Acl::printObjType(acl::ObjectType objType)
   {
      switch (objType)
	  {
      case QUEUE: return "Queue";
	  case EXCHANGE: return "Exchnage";
	  case BROKER: return "Broker";
	  case LINK: return "Link";
	  case ROUTE: return "Route";
	  default: return "Unknown";
	  }
   }

   bool Acl::authorise(std::string id, acl::Action action, acl::ObjectType objType, std::string name, std::map<std::string, std::string>*
   /*params*/)
   {
      if (aclValues.noEnforce) return true;
      boost::shared_ptr<AclData> dataLocal = data;  //rcu copy
      
      // only use dataLocal here...
   
      // add real ACL check here... 
      AclResult aclreslt = ALLOWLOG;  // hack to test, set based on real decision.
	  
	  
	  return result(aclreslt, id, action, objType, name); 
   }

   bool Acl::authorise(std::string id, acl::Action action, acl::ObjectType objType, std::string ExchangeName, std::string /*RoutingKey*/)
   {
      if (aclValues.noEnforce) return true;
      boost::shared_ptr<AclData> dataLocal = data;  //rcu copy
      
      // only use dataLocal here...
   
      // add real ACL check here... 
      AclResult aclreslt = ALLOWLOG;  // hack to test, set based on real decision.
	  
	  
	  return result(aclreslt, id, action, objType, ExchangeName); 
   }

   
   bool Acl::result(AclResult aclreslt, std::string id, acl::Action action, acl::ObjectType objType, std::string name)
   {
	  switch (aclreslt)
	  {
	  case ALLOWLOG:
          QPID_LOG(info, "ACL Allow id:" << id <<" action:" << printAction(action) << " ObjectType:" << printObjType(objType) << " Name:" << name );  
	  case ALLOW:
	      return true;
	  case DENYNOLOG:
	      return false;
	  case DENY:
	  default:
	      QPID_LOG(info, "ACL Deny id:" << id << " action:" << printAction(action) << " ObjectType:" << printObjType(objType) << " Name:" << name);  
	      return false;
	  }
      return false;  
   }
      
   bool Acl::readAclFile()
   {
      // only set transferAcl = true if a rule implies the use of ACL on transfer, else keep false for permormance reasons.
      return readAclFile(aclValues.aclFile);
   }

   bool Acl::readAclFile(std::string aclFile) {
      boost::shared_ptr<AclData> d(new AclData);
      if (AclReader::read(aclFile, d))
          return false;
 
      data = d;
      return true;
   }

   Acl::~Acl(){}


    
}} // namespace qpid::acl
