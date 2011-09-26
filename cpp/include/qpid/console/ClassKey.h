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
#ifndef _QPID_CONSOLE_CLASSKEY_H_
#define _QPID_CONSOLE_CLASSKEY_H_

#include <string>
#include "qpid/console/ConsoleImportExport.h"
#include "qpid/console/Package.h"
#include "qpid/framing/Buffer.h"

namespace qpid {
namespace console {

    /**
     *
     * \ingroup qmfconsoleapi
     */
    class QPID_CONSOLE_CLASS_EXTERN ClassKey {
    public:
        QPID_CONSOLE_EXTERN static const int HASH_SIZE = 16;

        QPID_CONSOLE_EXTERN ClassKey(const std::string& package, const std::string& name, const uint8_t* hash);

        const QPID_CONSOLE_EXTERN std::string& getPackageName() const { return package; }
        const QPID_CONSOLE_EXTERN std::string& getClassName() const { return name; }
        const QPID_CONSOLE_EXTERN uint8_t* getHash() const { return hash; }
        QPID_CONSOLE_EXTERN std::string getHashString() const;
        QPID_CONSOLE_EXTERN std::string str() const;
        QPID_CONSOLE_EXTERN bool operator==(const ClassKey& other) const;
        QPID_CONSOLE_EXTERN bool operator!=(const ClassKey& other) const;
        QPID_CONSOLE_EXTERN bool operator<(const ClassKey& other) const;
        QPID_CONSOLE_EXTERN bool operator>(const ClassKey& other) const;
        QPID_CONSOLE_EXTERN bool operator<=(const ClassKey& other) const;
        QPID_CONSOLE_EXTERN bool operator>=(const ClassKey& other) const;
        QPID_CONSOLE_EXTERN void encode(framing::Buffer& buffer) const;

    private:
        std::string package;
        std::string name;
        uint8_t hash[HASH_SIZE];
    };

    QPID_CONSOLE_EXTERN std::ostream& operator<<(std::ostream& o, const ClassKey& k);
}
}


#endif
