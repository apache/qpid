#ifndef QPID_ACL_ACLREADER_H
#define QPID_ACL_ACLREADER_H


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

#include <boost/shared_ptr.hpp>
#include <string>
#include <vector>

namespace qpid {
namespace acl {

struct AclData {
    bool lc; // Line continue flag
    AclData() : lc(false) {}
};

class AclReader {
public:
    static int read(const std::string& fn, boost::shared_ptr<AclData> d);
private:
    static void processLine(char* line, boost::shared_ptr<AclData> d);
    static int tokenizeLine(char* line, std::vector<std::string>& toks);
};
    
}} // namespace qpid::acl

#endif // QPID_ACL_ACLREADER_H
