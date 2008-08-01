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

#include "qpid/acl/AclReader.h"

#include <cstring>
//#include <iostream> // debug
#include <fstream>

namespace qpid {
namespace acl {

int AclReader::read(const std::string& fn, boost::shared_ptr<AclData> d) {
//std::cout << "AclReader::read(" << fn << ")" << std::endl << std::flush;
    char buff[1024];
    std::ifstream ifs(fn.c_str(), std::ios_base::in);
    if (!ifs.good()) {
        // error/exception - file open error
        return -1;
    }
    try {
        while (ifs.good()) {
            ifs.getline(buff, 1024);
            processLine(buff, d);
        }
        ifs.close();
    } catch (...) {
        // error/exception - file read/processing error
        ifs.close();
        return -2;
    }
    return 0;
}


void AclReader::processLine(char* line, boost::shared_ptr<AclData> /*d*/) {
   std::vector<std::string> toks;
   int numToks = tokenizeLine(line, toks);
   for (int i=0; i<numToks; i++) {
// DO MAGIC STUFF HERE
//std::cout << "tok " << i << ": " << toks[i] << std::endl << std::flush;
   }
}

int  AclReader::tokenizeLine(char* line, std::vector<std::string>& toks) {
    const char* tokChars = " \t\n";
    int cnt = 0;
    char* cp = std::strtok(line, tokChars);
    while (cp != 0) {
        toks.push_back(std::string(cp));
        cnt++;
        cp = std::strtok(0, tokChars);
    }
    return cnt;
}


}} // namespace qpid::acl
