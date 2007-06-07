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

#include "Cpg.h"

namespace qpid {
namespace cluster {

using namespace std;

string Cpg::errorStr(cpg_error_t err, const std::string& msg) {
    switch (err) {
      case CPG_OK: return msg+": ok";
      case CPG_ERR_LIBRARY: return msg+": library";
      case CPG_ERR_TIMEOUT: return msg+": timeout";
      case CPG_ERR_TRY_AGAIN: return msg+": try again";
      case CPG_ERR_INVALID_PARAM: return msg+": invalid param";
      case CPG_ERR_NO_MEMORY: return msg+": no memory";
      case CPG_ERR_BAD_HANDLE: return msg+": bad handle";
      case CPG_ERR_ACCESS: return msg+": access";
      case CPG_ERR_NOT_EXIST: return msg+": not exist";
      case CPG_ERR_EXIST: return msg+": exist";
      case CPG_ERR_NOT_SUPPORTED: return msg+": not supported";
      case CPG_ERR_SECURITY: return msg+": security";
      case CPG_ERR_TOO_MANY_GROUPS: return msg+": too many groups";
      default:
        assert(0);
        return ": unknown";
    };
}

std::string Cpg::cantJoinMsg(const Name& group) {
    return "Cannot join CPG group "+group.str();
}

std::string Cpg::cantLeaveMsg(const Name& group) {
    return "Cannot leave CPG group "+group.str();
}

std::string Cpg::cantMcastMsg(const Name& group) {
    return "Cannot mcast to CPG group "+group.str();
}

}} // namespace qpid::cpg



