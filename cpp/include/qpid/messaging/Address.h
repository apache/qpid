#ifndef QPID_MESSAGING_ADDRESS_H
#define QPID_MESSAGING_ADDRESS_H

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
#include <string>
#include "qpid/Exception.h"
#include "qpid/messaging/Variant.h"
#include "qpid/client/ClientImportExport.h"
#include <ostream>

namespace qpid {
namespace messaging {

struct InvalidAddress : public qpid::Exception 
{
    InvalidAddress(const std::string& msg);
};

struct MalformedAddress : public qpid::Exception 
{
    MalformedAddress(const std::string& msg);
};

class AddressImpl;

/**
 * Represents an address to which messages can be sent and from which
 * messages can be received. Often a simple name is sufficient for
 * this, however this can be augmented with a subject pattern and
 * options.
 * 
 * All parts of an address can be specified in a string of the
 * following form:
 * 
 * <address> [ / <subject> ] [ { <key> : <value> , ... } ]
 * 
 * Here the <address> is a simple name for the addressed entity and
 * <subject> is a subject or subject pattern for messages sent to or
 * received from this address. The options are specified as a series
 * of key value pairs enclosed in curly brackets (denoting a map).
 */
class Address
{
  public:
    QPID_CLIENT_EXTERN Address();
    QPID_CLIENT_EXTERN Address(const std::string& address);
    QPID_CLIENT_EXTERN Address(const std::string& name, const std::string& subject,
                               const Variant::Map& options, const std::string& type = "");
    QPID_CLIENT_EXTERN Address(const Address& address);
    QPID_CLIENT_EXTERN ~Address();
    Address& operator=(const Address&);
    QPID_CLIENT_EXTERN const std::string& getName() const;
    QPID_CLIENT_EXTERN void setName(const std::string&);
    QPID_CLIENT_EXTERN const std::string& getSubject() const;
    QPID_CLIENT_EXTERN void setSubject(const std::string&);
    QPID_CLIENT_EXTERN bool hasSubject() const;
    QPID_CLIENT_EXTERN const Variant::Map& getOptions() const;
    QPID_CLIENT_EXTERN Variant::Map& getOptions();
    QPID_CLIENT_EXTERN void setOptions(const Variant::Map&);

    QPID_CLIENT_EXTERN std::string getType() const;
    QPID_CLIENT_EXTERN void setType(const std::string&);

    QPID_CLIENT_EXTERN const Variant& getOption(const std::string& key) const;

    QPID_CLIENT_EXTERN std::string toStr() const;
    QPID_CLIENT_EXTERN operator bool() const;
    QPID_CLIENT_EXTERN bool operator !() const;
  private:
    AddressImpl* impl;
};

QPID_CLIENT_EXTERN std::ostream& operator<<(std::ostream& out, const Address& address);

}} // namespace qpid::messaging

#endif  /*!QPID_MESSAGING_ADDRESS_H*/
