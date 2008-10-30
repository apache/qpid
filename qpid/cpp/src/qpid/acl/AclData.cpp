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

#include "qpid/acl/AclData.h"
#include "qpid/log/Statement.h"


namespace qpid {
namespace acl {

AclData::AclData():decisionMode(qpid::acl::DENY),transferAcl(false)
{
	for (unsigned int cnt=0; cnt< qpid::acl::ACTIONSIZE; cnt++){
	    actionList[cnt]=0;
	}

}

void AclData::clear ()
{
    for (unsigned int cnt=0; cnt< qpid::acl::ACTIONSIZE; cnt++){
	    if (actionList[cnt]){
		    for (unsigned int cnt1=0; cnt1< qpid::acl::OBJECTSIZE; cnt1++)
			    delete actionList[cnt][cnt1]; 
		}
		delete[] actionList[cnt];
	}
	
}

bool AclData::matchProp(const std::string & src, const std::string& src1)
{
    // allow wildcard on the end of strings...
	if (src.data()[src.size()-1]=='*') {
	    return (src.compare(0, src.size()-1, src1, 0,src.size()-1  ) == 0);
	} else {
		return (src.compare(src1)==0) ;
	}
}
 
AclResult AclData::lookup(const std::string& id, const Action& action, const ObjectType& objType, const std::string& name, std::map<Property, std::string>* params)
{
     AclResult aclresult = decisionMode;
	
	 if (actionList[action] && actionList[action][objType]){
	      AclData::actObjItr itrRule = actionList[action][objType]->find(id);
		  if (itrRule == actionList[action][objType]->end())
		       itrRule = actionList[action][objType]->find("*");
		  if (itrRule != actionList[action][objType]->end() ) {
			   
			   //loop the vector
    		   for (ruleSetItr i=itrRule->second.begin(); i<itrRule->second.end(); i++) {
                    
					// loop the names looking for match
					bool match =true;
					for (propertyMapItr pMItr = i->props.begin(); (pMItr != i->props.end()) && match; pMItr++)
					{
                        //match name is exists first
						if (pMItr->first == acl::PROP_NAME){
						     if (!matchProp(pMItr->second, name)){  
							     match= false;
							 }
						}else if (params){ //match pMItr against params
							propertyMapItr paramItr = params->find (pMItr->first);
							if (paramItr == params->end()){
						    	match = false;
							}else if (!matchProp(paramItr->second, pMItr->second)){  
							    	match = false;
							}
						}
					}
					if (match) return getACLResult(i->logOnly, i->log);
    		   }
		  }
	 }
     return aclresult;
}

AclResult AclData::lookup(const std::string& id, const Action& action, const ObjectType& objType, const std::string& /*Exchange*/ name, const std::string& RoutingKey)
{
     AclResult aclresult = decisionMode;
	
	 if (actionList[action] && actionList[action][objType]){
	      AclData::actObjItr itrRule = actionList[action][objType]->find(id);
		  if (itrRule == actionList[action][objType]->end())
		       itrRule = actionList[action][objType]->find("*");
		  if (itrRule != actionList[action][objType]->end() ) {
			   
			   //loop the vector
    		   for (ruleSetItr i=itrRule->second.begin(); i<itrRule->second.end(); i++) {
                    
					// loop the names looking for match
					bool match =true;
					for (propertyMapItr pMItr = i->props.begin(); (pMItr != i->props.end()) && match; pMItr++)
					{
                        //match name is exists first
						if (pMItr->first == acl::PROP_NAME){
						     if (!matchProp(pMItr->second, name)){  
							     match= false;
							 }
						}else if (pMItr->first == acl::PROP_ROUTINGKEY){
						     if (!matchProp(pMItr->second, RoutingKey)){  
							     match= false;
							 }
						}
					}
					if (match) return getACLResult(i->logOnly, i->log);
    		   }
		  }
	 }
     return aclresult;

}


AclResult AclData::getACLResult(bool logOnly, bool log)
{
	switch (decisionMode)
	{
	case qpid::acl::ALLOWLOG:
	case qpid::acl::ALLOW:
    	 if (logOnly) return qpid::acl::ALLOWLOG;
		 if (log)
	         return qpid::acl::DENYLOG;
		 else
	         return qpid::acl::DENY;
	
	
	case qpid::acl::DENYLOG:
	case qpid::acl::DENY:
	     if (logOnly) return qpid::acl::DENYLOG;
		 if (log)
    	     return qpid::acl::ALLOWLOG;
		 else
    	     return qpid::acl::ALLOW;
	}
	
    QPID_LOG(error, "ACL Decision Failed, setting DENY");
	return qpid::acl::DENY;
}

AclData::~AclData()
{
    clear();
}

}} 
