#ifndef QPID_BROKER_PREVIEWSESSIONMANAGER_H
#define QPID_BROKER_PREVIEWSESSIONMANAGER_H

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

#include <qpid/framing/Uuid.h>
#include <qpid/sys/Time.h>
#include <qpid/sys/Mutex.h>
#include <qpid/RefCounted.h>

#include <boost/noncopyable.hpp>
#include <boost/ptr_container/ptr_vector.hpp>

#include <set>
#include <vector>
#include <memory>

namespace qpid {
namespace broker {

class PreviewSessionState;
class PreviewSessionHandler;

/**
 * Create and manage PreviewSessionState objects.
 */
class PreviewSessionManager : private boost::noncopyable {
  public:
    /**
     * Observer notified of PreviewSessionManager events.
     */
    struct Observer : public RefCounted {
        virtual void opened(PreviewSessionState&) {}
    };
    
    PreviewSessionManager(uint32_t ack);
    
    ~PreviewSessionManager();
    
    /** Open a new active session, caller takes ownership */
    std::auto_ptr<PreviewSessionState> open(PreviewSessionHandler& c, uint32_t timeout_);
    
    /** Suspend a session, start it's timeout counter.
     * The factory takes ownership.
     */
    void suspend(std::auto_ptr<PreviewSessionState> session);
        
    /** Resume a suspended session.
     *@throw Exception if timed out or non-existant.
     */
    std::auto_ptr<PreviewSessionState> resume(const framing::Uuid&);

    /** Add an Observer. */
    void add(const intrusive_ptr<Observer>&);
    
  private:
    typedef boost::ptr_vector<PreviewSessionState> Suspended;
    typedef std::set<framing::Uuid> Active;
    typedef std::vector<intrusive_ptr<Observer> > Observers;

    void erase(const framing::Uuid&);             
    void eraseExpired();             

    sys::Mutex lock;
    Suspended suspended;
    Active active;
    uint32_t ack;
    Observers observers;
    
  friend class PreviewSessionState; // removes deleted sessions from active set.
};



}} // namespace qpid::broker





#endif  /*!QPID_BROKER_PREVIEWSESSIONMANAGER_H*/
