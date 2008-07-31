#ifndef QPID_ACLMODULE_ACL_H
#define QPID_ACLMODULE_ACL_H


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
#include <map>
#include <string>


namespace qpid {

namespace acl{
typedef enum ObjectType {QUEUE,EXCHANGE,ROUTINGKEY};
typedef enum Action {CONSUME,PUBLISH,CREATE,ACCESS,BIND,UNBIND,DELETE,PURGE};
typedef enum AclResult {ALLOW,ALLOWLOG,DENY,DENYNOLOG};	
}

namespace broker {


class AclModule
{

public:
   
   virtual bool authorise(std::string id, acl::Action action, acl::ObjectType objType, std::string name, std::map<std::string, std::string>* params)=0;
   // create specilied authorise methods for cases that need faster matching as needed.

   virtual ~AclModule() {};
};


    
}} // namespace qpid::broker

#endif // QPID_ACLMODULE_ACL_H
