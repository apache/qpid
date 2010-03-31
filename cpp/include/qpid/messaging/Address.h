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
#include "qpid/types/Variant.h"
#include "qpid/messaging/ImportExport.h"
#include <ostream>

namespace qpid {
namespace messaging {

/**
 * Thrown when a syntactically correct address cannot be resolved or
 * used.
 */
struct InvalidAddress : public qpid::Exception 
{
    InvalidAddress(const std::string& msg);
};

/**
 * Thrown when an address string with inalid sytanx is used.
 */
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
 * &lt;address&gt; [ / &lt;subject&gt; ] ; [ { &lt;key&gt; : &lt;value&gt; , ... } ]
 * 
 * Here the &lt;address&gt; is a simple name for the addressed
 * entity and &lt;subject&gt; is a subject or subject pattern for
 * messages sent to or received from this address. The options are
 * specified as a series of key value pairs enclosed in curly brackets
 * (denoting a map). Values can be nested maps, or lists (which are
 * denoted as a comma separated list of values inside square brackets,
 * e.g. [a, b, c]).
 * 
 * The currently supported options are as follows:
 *
 * <table border=0> 
 * 
 * <tr valign=top>
 *   <td>create</td>
 *   <td>Indicate whether the address should be automatically created
 *       or not. Can be one of <i>always</i>, <i>never</i>,
 *       <i>sender</i> or <i>receiver</i>. The properties of the node
 *       to be created can be specified via the node options (see
 *       below).
 *   </td>
 * </tr>
 * 
 * <tr valign=top>
 *   <td>assert</td>
 *   <td>Indicate whether or not to assert any specified node
 *       properties(see below) match the address. Can be one of
 *       <i>always</i>, <i>never</i>, <i>sender</i> or
 *       <i>receiver</i>.
 *   </td>
 * </tr>
 * 
 * <tr valign=top>
 *   <td>delete</td>
 *   <td>Indicate whether or not to delete the addressed node when a
 *       sender or receiver is cancelled. Can be one of <i>always</i>,
 *       <i>never</i>, <i>sender</i> or <i>receiver</i>.
 *   </td>
 * </tr>
 *
 * <tr valign=top>
 *   <td>node</td>
 *   <td>A nested map describing properties of the addressed
 *       node. Current properties supported are type (topic or queue),
 *       durable (boolean), x-declare and x-bindings.
 *   </td>
 * </tr>
 *
 * <tr valign=top>
 *   <td>link</td>
 *   <td>A nested map through which properties of the 'link' from
 *       sender/receiver to node can be configured. Current propeties
 *       are name, durable, realiability, x-declare, x-subscribe and
 *       x-bindings.
 *   </td>
 * </tr>
 * 
 * For receivers there is one other option of interest:
 * 
 * <table border=0 valign=top>
 * <tr valign=top><td>mode</td><td>(only relevant for queues)
 * indicates whether the subscribe should consume (the default) or
 * merely browse the messages. Valid values are 'consume' and
 * 'browse'</td></tr>
 * </table>
 * 
 * An address has value semantics.
 */
class Address
{
  public:
    QPID_CLIENT_EXTERN Address();
    QPID_CLIENT_EXTERN Address(const std::string& address);
    QPID_CLIENT_EXTERN Address(const std::string& name, const std::string& subject,
                               const qpid::types::Variant::Map& options, const std::string& type = "");
    QPID_CLIENT_EXTERN Address(const Address& address);
    QPID_CLIENT_EXTERN ~Address();
    QPID_CLIENT_EXTERN Address& operator=(const Address&);
    QPID_CLIENT_EXTERN const std::string& getName() const;
    QPID_CLIENT_EXTERN void setName(const std::string&);
    QPID_CLIENT_EXTERN const std::string& getSubject() const;
    QPID_CLIENT_EXTERN void setSubject(const std::string&);
    QPID_CLIENT_EXTERN const qpid::types::Variant::Map& getOptions() const;
    QPID_CLIENT_EXTERN qpid::types::Variant::Map& getOptions();
    QPID_CLIENT_EXTERN void setOptions(const qpid::types::Variant::Map&);

    QPID_CLIENT_EXTERN std::string getType() const;
    /**
     * The type of and addressed node influences how receivers and
     * senders are constructed for it. It also affects how a reply-to
     * address is encoded. If the type is not specified in the address
     * itself, it will be automatically determined by querying the
     * broker. The type can be explicitly set to prevent this if
     * needed.
     */
    QPID_CLIENT_EXTERN void setType(const std::string&);

    QPID_CLIENT_EXTERN std::string str() const;
    QPID_CLIENT_EXTERN operator bool() const;
    QPID_CLIENT_EXTERN bool operator !() const;
  private:
    AddressImpl* impl;
};

QPID_CLIENT_EXTERN std::ostream& operator<<(std::ostream& out, const Address& address);

}} // namespace qpid::messaging

#endif  /*!QPID_MESSAGING_ADDRESS_H*/
