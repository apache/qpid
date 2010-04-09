#ifndef QPID_ACL_ACLVALIDATOR_H
#define QPID_ACL_ACLVALIDATOR_H


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
#include "qpid/acl/AclData.h"
#include "qpid/sys/IntegerTypes.h"
#include <boost/shared_ptr.hpp>
#include <vector>
#include <sstream>

namespace qpid {
namespace acl {

class AclValidator {

    /* Base Property */
   class AclProperty{        
        public:
            enum PropertyType { INT, STRING, ENUM };

        public:
            virtual ~AclProperty(){};
            virtual int getType()=0;
            virtual bool validate(const std::string& val)=0;
            virtual std::string allowedValues()=0;
   };

   class AclIntProperty : public AclProperty{        
            int64_t min;
            int64_t max;
        
        public:
            AclIntProperty(int64_t min,int64_t max);
            virtual ~AclIntProperty (){};
            int getType(){ return AclProperty::INT; }
            virtual bool validate(const std::string& val);
            virtual std::string allowedValues();
   };

   class AclEnumProperty : public AclProperty{
            std::vector<std::string> values;               

        public:
            AclEnumProperty(std::vector<std::string>& allowed);
            virtual ~AclEnumProperty (){};
            int getType(){ return AclProperty::ENUM; }
            virtual bool validate(const std::string& val);
            virtual std::string allowedValues();
   };
   
   typedef std::pair<acl::Property,boost::shared_ptr<AclProperty> > Validator;
   typedef std::map<acl::Property,boost::shared_ptr<AclProperty> > ValidatorMap;
   typedef ValidatorMap::iterator ValidatorItr;
 
   ValidatorMap validators;

public:
     
   void validate(boost::shared_ptr<AclData> d);
   AclValidator();
   ~AclValidator();
};
    
}} // namespace qpid::acl

#endif // QPID_ACL_ACLVALIDATOR_H
