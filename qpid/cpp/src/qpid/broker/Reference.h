#ifndef _broker_Reference_h
#define _broker_Reference_h

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

#include "qpid/framing/MessageAppendBody.h"

#include <string>
#include <vector>
#include <map>
#include <boost/shared_ptr.hpp>
#include <boost/range.hpp>

namespace qpid {

namespace framing {
class MessageAppendBody;
}

namespace broker {

class MessageMessage;
class ReferenceRegistry;

// FIXME aconway 2007-03-27: Merge with client::IncomingMessage
// to common reference handling code.

/**
 * A reference is an accumulation point for data in a multi-frame
 * message. A reference can be used by multiple transfer commands to
 * create multiple messages, so the reference tracks which commands
 * are using it. When the reference is closed, all the associated
 * transfers are completed.
 *
 * THREAD UNSAFE: per-channel resource, access to channels is
 * serialized.
 */
class Reference
{
  public:
    typedef std::string Id;
    typedef boost::shared_ptr<Reference> shared_ptr;
    typedef boost::shared_ptr<MessageMessage> MessagePtr;
    typedef std::vector<MessagePtr> Messages;
    typedef std::vector<framing::MessageAppendBody> Appends;

    Reference(const Id& id_=Id(), ReferenceRegistry* reg=0)
        : id(id_), size(0), registry(reg) {}
    
    const std::string& getId() const { return id; }
    uint64_t getSize() const { return size; }

    /** Add a message to be completed with this reference */
    void addMessage(MessagePtr message) { messages.push_back(message); }

    /** Append more data to the reference */
    void append(const framing::MessageAppendBody&);

    /** Close the reference, complete each associated message */
    void close();

    const Appends& getAppends() const { return appends; }
    const Messages& getMessages() const { return messages; }
    
  private:
    Id id;
    uint64_t size;
    ReferenceRegistry* registry;
    Messages messages;
    Appends appends;
};


/**
 * A registry/factory for references.
 * 
 * THREAD UNSAFE: per-channel resource, access to channels is
 * serialized.
 */
class ReferenceRegistry {
  public:
    ReferenceRegistry() {};
    Reference::shared_ptr open(const Reference::Id& id);
    Reference::shared_ptr get(const Reference::Id& id);

  private:
    typedef std::map<Reference::Id, Reference::shared_ptr> ReferenceMap;
    ReferenceMap references;

    // Reference calls references.erase().
    friend class Reference;
};


}} // namespace qpid::broker



#endif  /*!_broker_Reference_h*/
