/*
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
 */

%{

#include "Agent.h"
#include <ResilientConnection.h>

%}


%include <Query.h>
%include <Message.h>
%include <Agent.h>
%include <ResilientConnection.h>
%include <Typecode.h>
%include <Schema.h>
%include <Value.h>
%include <ObjectId.h>
%include <Object.h>

%include <qpid/client/ClientImportExport.h>
%include <qpid/client/ConnectionSettings.h>


%inline {

using namespace std;
using namespace qmf;

namespace qmf {


}
}


%{

%};

