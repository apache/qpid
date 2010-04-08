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

#include "qpid/acl/AclValidator.h"
#include "qpid/acl/AclData.h"
#include "qpid/Exception.h"
#include "qpid/log/Statement.h"
#include "qpid/sys/IntegerTypes.h"
#include <boost/lexical_cast.hpp>
#include <numeric>
#include <sstream>

namespace qpid {
namespace acl {

AclValidator::AclIntProperty::AclIntProperty(int64_t i,int64_t j) : min(i), max(j){    
}

bool AclValidator::AclIntProperty::validate(const std::string& val) {
  int64_t v;
  try
  {
    v = boost::lexical_cast<int64_t>(val);
  }catch(const boost::bad_lexical_cast& e){
    return 0;
  }

  if (v < min || v >= max){
    return 0;
  }else{
    return 1;
  }
}

std::string AclValidator::AclIntProperty::allowedValues() {
   return "values should be between " + 
          boost::lexical_cast<std::string>(min) + " and " +
          boost::lexical_cast<std::string>(max);
}

AclValidator::AclEnumProperty::AclEnumProperty(std::vector<std::string>& allowed): values(allowed){      
}

bool AclValidator::AclEnumProperty::validate(const std::string& val) {
  for (std::vector<std::string>::iterator itr = values.begin(); itr != values.end(); ++itr ){
     if (val.compare(*itr) == 0){
        return 1;
     }
  }

  return 0;
}

std::string AclValidator::AclEnumProperty::allowedValues() {
   std::ostringstream oss;
   oss << "possible values are one of { ";
   for (std::vector<std::string>::iterator itr = values.begin(); itr != values.end(); itr++ ){
        oss << "'" << *itr << "' ";
   }
   oss << "}";
   return oss.str();
}

AclValidator::AclValidator(){
  validators.insert(Validator(acl::PROP_MAXQUEUESIZE,
                              boost::shared_ptr<AclProperty>(
                                new AclIntProperty(0,std::numeric_limits<int64_t>::max()))
                             )
                   );

  validators.insert(Validator(acl::PROP_MAXQUEUECOUNT,
                              boost::shared_ptr<AclProperty>(
                                new AclIntProperty(0,std::numeric_limits<int64_t>::max()))
                             )
                   );

  std::string policyTypes[] = {"ring", "ring_strict", "flow_to_disk", "reject"};
  std::vector<std::string> v(policyTypes, policyTypes + sizeof(policyTypes) / sizeof(std::string));
  validators.insert(Validator(acl::PROP_POLICYTYPE,
                              boost::shared_ptr<AclProperty>(new AclEnumProperty(v))
                             )
                   );
  
}

AclValidator::~AclValidator(){
}

/* Iterate through the data model and validate the parameters. */
void AclValidator::validate(boost::shared_ptr<AclData> d) {
  
    for (unsigned int cnt=0; cnt< qpid::acl::ACTIONSIZE; cnt++){

        if (d->actionList[cnt]){

	        for (unsigned int cnt1=0; cnt1< qpid::acl::OBJECTSIZE; cnt1++){

		        if (d->actionList[cnt][cnt1]){

                    for (AclData::actObjItr actionMapItr = d->actionList[cnt][cnt1]->begin(); 
                      actionMapItr != d->actionList[cnt][cnt1]->end(); actionMapItr++) {

                        for (AclData::ruleSetItr i = actionMapItr->second.begin(); i < actionMapItr->second.end(); i++) {
                            
                            for (AclData::propertyMapItr pMItr = i->props.begin(); pMItr != i->props.end(); pMItr++) {

                               ValidatorItr itr = validators.find(pMItr->first);
                               if (itr != validators.end()){
                                 QPID_LOG(debug,"Found validator for property " << itr->second->allowedValues());

                                 if (!itr->second->validate(pMItr->second)){
                                    throw Exception( pMItr->second + " is not a valid value for '" + 
                                                     AclHelper::getPropertyStr(pMItr->first) + "', " +
                                                     itr->second->allowedValues());
                                 }//if
                               }//if
                            }//for
                        }//for 
                    }//for
                }//if 
            }//for
	    }//if
	}//for
}

}}
