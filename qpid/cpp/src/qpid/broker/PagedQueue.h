#ifndef QPID_BROKER_PAGEDQUEUE_H
#define QPID_BROKER_PAGEDQUEUE_H

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
#include "qpid/broker/Messages.h"
#include "qpid/broker/Message.h"
#include "qpid/framing/SequenceSet.h"
#include "qpid/sys/MemoryMappedFile.h"
#include <deque>
#include <list>
#include <map>

namespace qpid {
namespace broker {
class ProtocolRegistry;


/**
 *
 */
class PagedQueue : public Messages {
  public:
    PagedQueue(const std::string& name, const std::string& directory, uint maxLoaded, uint pageFactor, ProtocolRegistry& protocols);
    ~PagedQueue();
    size_t size();
    bool deleted(const QueueCursor&);
    void publish(const Message& added);
    Message* next(QueueCursor& cursor);
    Message* release(const QueueCursor& cursor);
    Message* find(const framing::SequenceNumber&, QueueCursor*);
    Message* find(const QueueCursor&);
    void foreach(Functor);
    void check(const Message& added);
  private:
    class Page {
      public:
        Page(size_t size, size_t offset);
        void read();//decode messages from memory mapped file
        void write();//encode messages into memory mapped file
        bool isLoaded() const;
        bool empty() const;
        void deleted(qpid::framing::SequenceNumber);
        Message* release(qpid::framing::SequenceNumber);
        bool add(const Message&);
        Message* next(uint32_t version, QueueCursor&);
        Message* find(qpid::framing::SequenceNumber);
        void load(qpid::sys::MemoryMappedFile&,ProtocolRegistry&);
        void unload(qpid::sys::MemoryMappedFile&);
        void clear(qpid::sys::MemoryMappedFile&);
        size_t available() const;
      private:
        size_t size;
        size_t offset;

        char* region;//0 implies not mapped
        qpid::framing::SequenceSet contents;
        qpid::framing::SequenceSet acquired;
        std::deque<Message> messages;//decoded representation
        size_t used;//amount of data used to encode current set of messages held
    };

    qpid::sys::MemoryMappedFile file;
    std::string name;
    const size_t pageSize;
    const uint maxLoaded;
    ProtocolRegistry& protocols;
    size_t offset;
    typedef std::map<qpid::framing::SequenceNumber, Page> Used;
    Used used;
    std::list<Page> free;
    uint loaded;
    uint32_t version;

    void addPages(size_t count);
    Page& newPage(qpid::framing::SequenceNumber);
    Used::iterator findPage(const QueueCursor& cursor);
    Used::iterator findPage(qpid::framing::SequenceNumber n, bool loadIfRequired);
    void load(Page&);
    void unload(Page&);
    bool deleted(qpid::framing::SequenceNumber);
};
}} // namespace qpid::broker

#endif  /*!QPID_BROKER_PAGEDQUEUE_H*/
