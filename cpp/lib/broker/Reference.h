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

#include <string>
#include <vector>
#include <map>
#include <boost/shared_ptr.hpp>
#include <boost/range.hpp>

namespace qpid {

namespace framing {
class MessageTransferBody;
class MessageAppendBody;
}

namespace broker {

class CompletionHandler;
class ReferenceRegistry;

/**
 * A reference is an accumulation point for data in a multi-frame
 * message. A reference can be used by multiple transfer commands, so
 * the reference tracks which commands are using it. When the reference
 * is closed, all the associated transfers are completed.
 *
 * THREAD UNSAFE: per-channel resource, access to channels is
 * serialized.
 */
class Reference
{
  public:
    typedef std::string Id;
    typedef boost::shared_ptr<framing::MessageTransferBody> TransferPtr;
    typedef std::vector<TransferPtr> Transfers;
    typedef boost::shared_ptr<framing::MessageAppendBody> AppendPtr;
    typedef std::vector<AppendPtr> Appends;

    Reference(const Id& id_=Id(), ReferenceRegistry* reg=0)
        : id(id_), registry(reg) {}
    
    const std::string& getId() const { return id; }

    /** Add a transfer to be completed with this reference */
    void transfer(TransferPtr transfer) { transfers.push_back(transfer); }

    /** Append more data to the reference */
    void append(AppendPtr ptr) { appends.push_back(ptr); }

    /** Close the reference, complete each associated transfer */
    void close();

    const Appends& getAppends() const { return appends; }
    const Transfers& getTransfers() const { return transfers; }
    
  private:
    void complete(TransferPtr transfer);
    
    Id id;
    ReferenceRegistry* registry;
    Transfers transfers;
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
    ReferenceRegistry(CompletionHandler& handler_) : handler(handler_) {};
    Reference& open(const Reference::Id& id);
    Reference& get(const Reference::Id& id);

  private:
    typedef std::map<Reference::Id, Reference> ReferenceMap;
    CompletionHandler& handler;
    ReferenceMap references;

    // Reference calls references.erase() and uses handler.
  friend class Reference;
};


}} // namespace qpid::broker



#endif  /*!_broker_Reference_h*/
