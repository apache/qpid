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
#include "qpid/client/Queue.h"

qpid::client::Queue::Queue() : name(""), autodelete(true), exclusive(true){}

qpid::client::Queue::Queue(std::string _name) : name(_name), autodelete(false), exclusive(false){}

qpid::client::Queue::Queue(std::string _name, bool temp) : name(_name), autodelete(temp), exclusive(temp){}

qpid::client::Queue::Queue(std::string _name, bool _autodelete, bool _exclusive) 
  : name(_name), autodelete(_autodelete), exclusive(_exclusive){}

const std::string& qpid::client::Queue::getName() const{
    return name;
}

void qpid::client::Queue::setName(const std::string& _name){
    name = _name;
}

bool qpid::client::Queue::isAutoDelete() const{
    return autodelete;
}

bool qpid::client::Queue::isExclusive() const{
    return exclusive;
}




