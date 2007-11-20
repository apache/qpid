#ifndef _sys_EventChannel_h
#define _sys_EventChannel_h

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

#include <SharedObject.h>
#include <ExceptionHolder.h>
#include <boost/function.hpp>
#include <memory>

namespace qpid {
namespace sys {

class Event;
class EventHandler;
class EventChannel;

/**
 * Base class for all Events.
 */
class Event
{
  public:
    /** Type for callback when event is dispatched */
    typedef boost::function0<void> Callback;

    /**
     * Create an event with optional callback.
     * Instances of Event are sent directly through the channel.
     * Derived classes define additional waiting behaviour.
     *@param cb A callback functor that is invoked when dispatch() is called.
     */
    Event(Callback cb = 0) : callback(cb) {}

    virtual ~Event();

    /** Call the callback provided to the constructor, if any. */
    void dispatch();

    /** True if there was an error processing this event */
    bool hasError() const;

    /** If hasError() throw the corresponding exception. */
    void throwIfError() throw(Exception);

  protected:
    virtual void prepare(EventHandler&);
    virtual Event* complete(EventHandler&);
    void setError(const ExceptionHolder& e);

    Callback callback;
    ExceptionHolder error;

  friend class EventChannel;
  friend class EventHandler;
};

template <class BufT>
class IOEvent : public Event {
  public:
    void getDescriptor() const { return descriptor; }
    size_t getSize() const { return size; }
    BufT getBuffer() const { return buffer; }
  
  protected:
    IOEvent(int fd, Callback cb, size_t sz, BufT buf) :
        Event(cb), descriptor(fd), buffer(buf), size(sz) {}

    int descriptor;
    BufT buffer;
    size_t size;
};

/** Asynchronous read event */
class ReadEvent : public IOEvent<void*>
{
  public:
    explicit ReadEvent(int fd=-1, void* buf=0, size_t sz=0, Callback cb=0) :
        IOEvent<void*>(fd, cb, sz, buf), received(0) {}

  private:
    void prepare(EventHandler&);
    Event* complete(EventHandler&);
    ssize_t doRead();

    size_t received;
};

/** Asynchronous write event */
class WriteEvent : public IOEvent<const void*>
{
  public:
    explicit WriteEvent(int fd=-1, const void* buf=0, size_t sz=0,
                        Callback cb=0) :
        IOEvent<const void*>(fd, cb, sz, buf), written(0) {}

  protected:
    void prepare(EventHandler&);
    Event* complete(EventHandler&);

  private:
    ssize_t doWrite();
    size_t written;
};

/** Asynchronous socket accept event */
class AcceptEvent : public Event
{
  public:
    /** Accept a connection on fd. */
    explicit AcceptEvent(int fd=-1, Callback cb=0) :
        Event(cb), descriptor(fd), accepted(0) {}

    /** Get descriptor for server socket */
    int getAcceptedDesscriptor() const { return accepted; }

  private:
    void prepare(EventHandler&);
    Event* complete(EventHandler&);

    int descriptor;
    int accepted;
};


class QueueSet;

/**
 * Channel to post and wait for events.
 */
class EventChannel : public qpid::SharedObject<EventChannel>
{
  public:
    static shared_ptr create();
    
    ~EventChannel();
    
    /** Post an event to the channel. */
    void postEvent(Event& event);

    /** Post an event to the channel. Must not be 0. */
    void postEvent(Event* event) { postEvent(*event); }
        
    /**
     * Wait for the next complete event.
     *@return Pointer to event. Will never return 0.
     */
    Event* getEvent();

  private:
    EventChannel();
    boost::shared_ptr<EventHandler> handler;
};


}}



#endif  /*!_sys_EventChannel_h*/
