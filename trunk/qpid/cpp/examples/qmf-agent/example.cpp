/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

#include <qpid/management/Manageable.h>
#include <qpid/management/ManagementObject.h>
#include <qpid/agent/ManagementAgent.h>
#include <qpid/sys/Mutex.h>
#include "Parent.h"
#include "Child.h"
#include "ArgsParentCreate_child.h"
#include "PackageQmf_example.h"

#include <unistd.h>
#include <cstdlib>
#include <iostream>

#include <sstream>

using namespace qpid::management;
using namespace qpid::sys;
using namespace std;
using qpid::management::ManagementObject;
using qpid::management::Manageable;
using qpid::management::Args;
using qpid::sys::Mutex;

class ChildClass;

//==============================================================
// CoreClass is the operational class that corresponds to the
// "Parent" class in the management schema.
//==============================================================
class CoreClass : public Manageable
{
    string           name;
    ManagementAgent* agent;
    Parent* mgmtObject;
    std::vector<ChildClass*> children;
    Mutex vectorLock;

public:

    CoreClass(ManagementAgent* agent, string _name);
    ~CoreClass() { mgmtObject->resourceDestroy(); }

    ManagementObject* GetManagementObject(void) const
    { return mgmtObject; }

    void doLoop();
    status_t ManagementMethod (uint32_t methodId, Args& args);
};

class ChildClass : public Manageable
{
    string name;
    Child* mgmtObject;

public:

    ChildClass(ManagementAgent* agent, CoreClass* parent, string name);
    ~ChildClass() { mgmtObject->resourceDestroy(); }

    ManagementObject* GetManagementObject(void) const
    { return mgmtObject; }

    void doWork()
    {
        mgmtObject->inc_count(2);
    }
};

CoreClass::CoreClass(ManagementAgent* _agent, string _name) : name(_name), agent(_agent)
{
    mgmtObject = new Parent(agent, this, name);

    agent->addObject(mgmtObject);
    mgmtObject->set_state("IDLE");
}

void CoreClass::doLoop()
{
    // Periodically bump a counter to provide a changing statistical value
    while (1) {
        sleep(1);
        mgmtObject->inc_count();
        mgmtObject->set_state("IN LOOP");

        {
            Mutex::ScopedLock _lock(vectorLock);

            for (std::vector<ChildClass*>::iterator iter = children.begin();
                 iter != children.end();
                 iter++) {
                (*iter)->doWork();
            }
        }
    }
}

Manageable::status_t CoreClass::ManagementMethod(uint32_t methodId, Args& args)
{
    Mutex::ScopedLock _lock(vectorLock);

    switch (methodId) {
    case Parent::METHOD_CREATE_CHILD:
        ArgsParentCreate_child& ioArgs = (ArgsParentCreate_child&) args;

        ChildClass *child = new ChildClass(agent, this, ioArgs.i_name);
        ioArgs.o_childRef = child->GetManagementObject()->getObjectId();

        children.push_back(child);

        return STATUS_OK;
    }

    return STATUS_NOT_IMPLEMENTED;
}

ChildClass::ChildClass(ManagementAgent* agent, CoreClass* parent, string name)
{
    mgmtObject = new Child(agent, this, parent, name);

    agent->addObject(mgmtObject);
}


//==============================================================
// Main program
//==============================================================
int main(int argc, char** argv) {
    ManagementAgent::Singleton singleton;
    const char* host = argc>1 ? argv[1] : "127.0.0.1";
    int port = argc>2 ? atoi(argv[2]) : 5672;

    // Create the qmf management agent
    ManagementAgent* agent = singleton.getInstance();

    // Register the Qmf_example schema with the agent
    PackageQmf_example packageInit(agent);

    // Start the agent.  It will attempt to make a connection to the
    // management broker
    agent->init(string(host), port);

    // Allocate some core objects
    CoreClass core1(agent, "Example Core Object #1");
    CoreClass core2(agent, "Example Core Object #2");
    CoreClass core3(agent, "Example Core Object #3");

    core1.doLoop();
}


