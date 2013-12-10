#ifndef QPID_MESSAGING_ADDRESSPARSER_H
#define QPID_MESSAGING_ADDRESSPARSER_H

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
#include "qpid/messaging/ImportExport.h"
#include "qpid/messaging/Address.h"

namespace qpid {
namespace messaging {

class AddressParser
{
  public:
    QPID_MESSAGING_EXTERN AddressParser(const std::string&);
    bool parse(Address& address);
    QPID_MESSAGING_EXTERN bool parseMap(qpid::types::Variant::Map& map);
    QPID_MESSAGING_EXTERN bool parseList(qpid::types::Variant::List& list);
  private:
    const std::string& input;
    std::string::size_type current;
    static const std::string RESERVED;

    bool readChar(char c);
    bool readQuotedString(std::string& s);
    bool readQuotedValue(qpid::types::Variant& value);
    bool readString(std::string& value, char delimiter);
    bool readWord(std::string& word, const std::string& delims = RESERVED);
    bool readSimpleValue(qpid::types::Variant& word);
    bool readKey(std::string& key);
    bool readValue(qpid::types::Variant& value);
    bool readValueIfExists(qpid::types::Variant& value);
    bool readKeyValuePair(qpid::types::Variant::Map& map);
    bool readMap(qpid::types::Variant& value);
    bool readList(qpid::types::Variant& value);
    bool readName(std::string& name);
    bool readSubject(std::string& subject);
    bool error(const std::string& message);
    bool eos();
    bool iswhitespace();
    bool in(const std::string& delims);
    bool isreserved();
    void readListItems(qpid::types::Variant::List& list);
    void readMapEntries(qpid::types::Variant::Map& map);
};

}} // namespace qpid::messaging

#endif  /*!QPID_MESSAGING_ADDRESSPARSER_H*/
