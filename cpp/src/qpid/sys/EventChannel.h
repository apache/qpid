#ifndef _sys_EventChannel_h
#define _sys_EventChannel_h

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

#include <qpid/SharedObject.h>
#include <qpid/Exception.h>
#include <qpid/sys/Time.h>

namespace qpid {
namespace sys {

class EventChannel;

class Event;
class ReadEvent;
class WriteEvent;
class AcceptEvent;
class NotifyEvent;

/**
   Active event channel. Events represent async IO requests or
   inter-task synchronization. Posting an Event registers interest in
   the IO or sync event. When it occurs the posted Event is 
   corresponding IO or sync event occurs they are returned to one
   of the threads waiting on the channel. For more details see
   the Event hierarchy.
*/
class EventChannel : public qpid::SharedObject<EventChannel>
{
  public:
    static EventChannel::shared_ptr create();
    
    virtual ~EventChannel() {}

    virtual void post(ReadEvent& event) = 0;
    virtual void post(WriteEvent& event) = 0;
    virtual void post(AcceptEvent& event) = 0;
    virtual void post(NotifyEvent& event) = 0;

    inline void post(Event& event);

    /**
     * Wait for the next completed event.
     * @return An Event or 0 to indicate the calling thread should shut down.
     */
    virtual Event* getEvent() = 0;

    /** Dispose of a system-allocated buffer. Called by ReadEvent */
    virtual void dispose(void* buffer, size_t size) = 0;
    
  protected:
    EventChannel() {}
};


/**
 * Base class for all events. There are two possible styles of use:
 *
 * Task style: the event is allocated as a local variable on the initiating
 * task, which blocks in wait(). Event::dispatch() resumes that task
 * with the event data available.
 *
 * Proactor style: Threads post events but do not
 * wait. Event::dispatch() processes the event in the dispatching
 * thread and then deletes itself.
 *
 * Tasks give less kernel context switching and blocking AND simpler
 * coding.  Tasks can call any number of pseudo-blocking opereations
 * that are actually event post/wait pairs. At each such point the
 * current thread can continue with the task or switch to another task
 * to minimise blocking.
 *
 * With Proactor style dispatch() is an atomic unit of work as far as
 * the EventChannel is concerned. To avoid such blocking the
 * application has to be written as a collection of non-blocking
 * dispatch() callbacks, which is more complex than tasks that can
 * call pseudo-blocking operations.
 */
class Event : private boost::noncopyable 
{
  public:
    virtual ~Event() {}

    /** Post this event to the channel */
    virtual void post(EventChannel& channel) = 0;

    /**
     * Block till the event is delivered.
     * At  most one task can wait on an event.
     */
    virtual void wait() const = 0;

    /**
     * Dispatch the event. Runs some event-specific code, may switch
     * context to resume a waiting task.
     */
    virtual void dispatch() = 0;
};

    
/**
 * Base class for asynchronous request events, provides exception
 * handling.
 */
class RequestEvent : public Event
{
  public:
    /** True if the async request failed */
    bool hasException() const { return ex.get(); }

    const qpid::Exception& getException() const { return *ex; }
    
    void setException(std::auto_ptr<qpid::Exception>& e) { ex = e; }

    /** If the event has an exception throw it, else do nothing */
    void verify() const { if (ex.get()) throw *ex; }

    void post(EventChannel& channel) { channel.post(*this); }

  private:
    qpid::HeapException ex;
};


/** An asynchronous read event. */
class ReadEvent : public RequestEvent {
  public:
    /**
     * Read data from fd.
     */
    ReadEvent(int fileDescriptor, void* buffer, size_t bytesToRead) :
        fd(fileDescriptor), data(buffer), size(bytesToRead) {}

    /** Number of bytes read. */
    size_t getBytesRead() const { verify(); return size; }

    /**
     * If the system supports direct access to DMA buffers then
     * it may provide a direct pointer to such a buffer to avoid
     * a copy into the user buffer.
     * @return true if getData() is returning a system-supplied buffer.
     */
    bool isSystemData() const { verify(); return channel != 0; }

    /**
     * Pointer to data read. Note if isSystemData() is true then this
     * is NOT the same buffer that was supplied to the constructor.
     * The user buffer is untouched. See dispose().
     */
    void* getData() const { verify(); return data; }

    /** Called by the event channel on completion. */
    void complete(EventChannel::shared_ptr ec, void* _data, size_t _size) {
        if (data != _data) channel = ec; data = _data; size = _size;
    }

    /**
     * Dispose of system-provided data buffer, if any.  This is
     * automatically called by the destructor.
     */
    void dispose() { if(channel && data) channel->dispose(data,size); data=0; }

    ~ReadEvent() { dispose(); }

    void post(EventChannel& channel) { channel.post(*this); }

  private:
    int fd;
    void* data;
    size_t size;
    EventChannel::shared_ptr channel;
};

/** Asynchronous write event */
class WriteEvent : public RequestEvent {
  public:
    WriteEvent(int fileDescriptor, void* buffer, size_t bytesToWrite) :
        fd(fileDescriptor), data(buffer), size(bytesToWrite) {}

    /** Number of bytes written */
    size_t getBytesWritten() const { verify(); return size; }

    void post(EventChannel& channel) { channel.post(*this); }

  private:
    int fd;
    void*  data;
    size_t size;
};

/** Asynchronous socket accept event */
class AcceptEvent : public RequestEvent {
  public:
    /** Accept a connection on listeningFd */
    AcceptEvent(int listeningFd) : listen(listeningFd) {}

    /** Get accepted file descriptor */
    int getAcceptedFd() const { verify(); return accepted; }

    void post(EventChannel& channel) { channel.post(*this); }

  private:
    int listen;
    int accepted;
};

/**
 * NotifyEvent is delievered immediately to be dispatched by an
 * EventChannel thread.
 */
class NotifyEvent : public RequestEvent {
  public:
    void post(EventChannel& channel) { channel.post(*this); }
};


inline void EventChannel::post(Event& event) { event.post(*this); }

}}


#endif  /*!_sys_EventChannel_h*/
