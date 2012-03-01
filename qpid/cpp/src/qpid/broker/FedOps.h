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

/* 
 *  Strings used to identify federated operations and operands.
 */

namespace 
{
    const std::string qpidFedOp("qpid.fed.op");          // a federation primitive
    const std::string qpidFedTags("qpid.fed.tags");      // a unique id for a broker
    const std::string qpidFedOrigin("qpid.fed.origin");  // the tag of the broker on which a propagated binding originated

    // Operands for qpidFedOp - each identifies a federation primitive

    const std::string fedOpBind("B");
    const std::string fedOpUnbind("U");
    const std::string fedOpReorigin("R");
    const std::string fedOpHello("H");
}
