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
#include "qmf/org/apache/qpid/acl/Package.h"

#include <map>

#include <boost/utility/in_place_factory.hpp>

using namespace std;
using namespace qpid::acl;
using qpid::management::ManagementAgent;
using qpid::management::ManagementObject;
using qpid::management::Manageable;
using qpid::management::Args;
namespace _qmf = qmf::org::apache::qpid::acl;

Acl::Acl (AclValues& av, broker::Broker& b): aclValues(av), broker(&b), transferAcl(false)
{
	   
    ManagementAgent* agent = ManagementAgent::Singleton::getInstance();

    if (agent != 0){
        _qmf::Package  packageInit(agent);
        mgmtObject = new _qmf::Acl (agent, this, broker);
        agent->addObject (mgmtObject);
    }

    if (!readAclFile()){
        throw Exception("Could not read ACL file");
        if (mgmtObject!=0) mgmtObject->set_enforcingAcl(0);
    }
    QPID_LOG(info, "ACL Plugin loaded");
	   if (mgmtObject!=0) mgmtObject->set_enforcingAcl(1);
}

   bool Acl::authorise(const std::string& id, const Action& action, const ObjectType& objType, const std::string& name, std::map<Property, std::string>* params)
   {
      if (!aclValues.enforce) return true;
      boost::shared_ptr<AclData> dataLocal = data;  //rcu copy
      
      // add real ACL check here... 
      AclResult aclreslt = dataLocal->lookup(id,action,objType,name,params);
	  
	  
	  return result(aclreslt, id, action, objType, name); 
   }

   bool Acl::authorise(const std::string& id, const Action& action, const ObjectType& objType, const std::string& ExchangeName, const std::string& RoutingKey)
   {
      if (!aclValues.enforce) return true;
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
	      if (mgmtObject!=0) mgmtObject->inc_aclDenyCount();
	      return false;
	  case DENYLOG:
	      if (mgmtObject!=0) mgmtObject->inc_aclDenyCount();
	  default:
	      QPID_LOG(info, "ACL Deny id:" << id << " action:" << AclHelper::getActionStr(action) << " ObjectType:" << AclHelper::getObjectTypeStr(objType) << " Name:" << name);  
		  if (mgmtObject!=0){
		      framing::FieldTable _params;
		      mgmtObject->event_aclEvent(1, id, AclHelper::getActionStr(action),AclHelper::getObjectTypeStr(objType),name, _params);
		  }
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
      if (ar.read(aclFile, d)){
		  mgmtObject->event_fileNotLoaded("","See log for file load reason failure");
          return false;
      }
	  
      data = d;
	  transferAcl = data->transferAcl; // any transfer ACL
	  if (mgmtObject!=0){
	      mgmtObject->set_transferAcl(transferAcl?1:0);
		  mgmtObject->set_policyFile(aclFile);
		  sys::AbsTime now = sys::AbsTime::now();
          int64_t ns = sys::Duration(now);
		  mgmtObject->set_lastAclLoad(ns);
		  mgmtObject->event_fileLoaded("");
	  }
      return true;
   }

   Acl::~Acl(){}

   ManagementObject* Acl::GetManagementObject(void) const
   {
       return (ManagementObject*) mgmtObject;
   }
   
   Manageable::status_t Acl::ManagementMethod (uint32_t methodId, Args& /*args*/, string&)
   {
      Manageable::status_t status = Manageable::STATUS_UNKNOWN_METHOD;
      QPID_LOG (debug, "Queue::ManagementMethod [id=" << methodId << "]");

      switch (methodId)
      {
      case _qmf::Acl::METHOD_RELOADACLFILE :
          readAclFile();
          status = Manageable::STATUS_OK;
          break;
      }

    return status;
}    
