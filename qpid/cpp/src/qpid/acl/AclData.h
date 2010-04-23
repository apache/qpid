#ifndef QPID_ACL_ACLDATA_H
#define QPID_ACL_ACLDATA_H


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

#include "qpid/broker/AclModule.h"
#include <vector>
#include <sstream>

namespace qpid {
namespace acl {

class AclData {


public:

   typedef std::map<qpid::acl::Property, std::string> PropertyMap;
   typedef PropertyMap::const_iterator PropertyMapItr;
   struct Rule {
	  
	   bool log;
	   bool logOnly;  // this is a rule is to log only
	   
	   // key value map
      //??
      PropertyMap props;
	  
	  
	  Rule (PropertyMap& p):log(false),logOnly(false),props(p) {};

	  std::string toString () const {
	  	std::ostringstream ruleStr;
	  	ruleStr << "[log=" << log << ", logOnly=" << logOnly << " props{";
	  	for (PropertyMapItr pMItr = props.begin(); pMItr != props.end(); pMItr++) {
	  		ruleStr << " " << AclHelper::getPropertyStr((Property) pMItr-> first) << "=" << pMItr->second;
	  	}
	  	ruleStr << " }]";
	  	return ruleStr.str();
	  }      
   };

   typedef std::vector<Rule> RuleSet;
   typedef RuleSet::const_iterator RuleSetItr;
   typedef std::map<std::string, RuleSet > ActionObject; // user 
   typedef ActionObject::iterator ActObjItr;
   typedef std::vector<ActionObject> AclAction;  
   typedef std::vector<AclAction> ActionList;   

   // vector<action> -> vector<objects> -> map<user -> vector<Rule> > -> map <AclProperty -> string>

   ActionList actionList; 

   qpid::acl::AclResult decisionMode;  // determines if the rule set is a deny or allow mode. 
   bool transferAcl;
   std::string aclSource; 
   
   AclResult lookup(const std::string& id, const Action& action, const ObjectType& objType, const std::string& name, std::map<Property, std::string>* params=0);
   AclResult lookup(const std::string& id, const Action& action, const ObjectType& objType, const std::string& ExchangeName, const std::string& RoutingKey);
   AclResult getACLResult(bool logOnly, bool log);
  
   bool matchProp(const std::string & src, const std::string& src1);
   void clear ();
  
   AclData();
   virtual ~AclData();
};
    
}} // namespace qpid::acl

#endif // QPID_ACL_ACLDATA_H
