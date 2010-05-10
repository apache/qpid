/*
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
*/

#include <windows.h>
#include <msclr\lock.h>
#include <oletx2xa.h>
#include <string>
#include <limits>

#include "qpid/messaging/Session.h"

#include "QpidMarshal.h"
#include "Session.h"
#include "Connection.h"
#include "Duration.h"
#include "Receiver.h"
#include "Sender.h"

namespace org {
namespace apache {
namespace qpid {
namespace messaging {

    /// <summary>
    /// Session is a managed wrapper for a ::qpid::messaging::Session
    /// </summary>

    // constructor
    Session::Session(::qpid::messaging::Session * sp, Connection ^ connRef) :
        sessionp(sp),
        parentConnectionp(connRef)
    {
    }


    // Destructor
    Session::~Session()
    {
        Cleanup();
    }


    // Finalizer
    Session::!Session()
    {
        Cleanup();
    }

    // copy constructor
    Session::Session(const Session % rhs)
    {
        sessionp          = rhs.sessionp;
        parentConnectionp = rhs.parentConnectionp;
    }


    // Destroys kept object
    // TODO: add lock
    void Session::Cleanup()
    {
        if (NULL != sessionp)
        {
            delete sessionp;
            sessionp = NULL;
        }
    }

    void Session::close()
    {
        sessionp->close();
    }

    void Session::commit()
    {
        sessionp->commit();
    }

    void Session::rollback()
    {
        sessionp->rollback();
    }

    void Session::acknowledge()
    {
        sessionp->acknowledge();
    }

    void Session::acknowledge(bool sync)
    {
        sessionp->acknowledge(sync);
    }

    void Session::sync()
    {
        sessionp->sync();
    }

    void Session::sync(bool block)
    {
        sessionp->sync(block);
    }

    System::UInt32 Session::getReceivable()
    {
        return sessionp->getReceivable();
    }

    System::UInt32 Session::getUnsettledAcks()
    {
        return sessionp->getUnsettledAcks();
    }

    //bool Session::nextReceiver(Receiver)
    //{
    //    sessionp->nextReceiver(Receiver)
    //}

    //bool Session::nextReceiver(Receiver, Duration timeout)
    //{
    //    sessionp->nextReceiver();
    //}

    //Receiver Session::nextReceiver(Duration timeout)
    //{
    //}


    Sender ^ Session::createSender  (System::String ^ address)
    {
        // allocate a native sender
        ::qpid::messaging::Sender * senderp = new ::qpid::messaging::Sender;

        // create the sender
        *senderp = sessionp->::qpid::messaging::Session::createSender(QpidMarshal::ToNative(address));

        // create a managed sender
        Sender ^ newSender = gcnew Sender(senderp, this);

        return newSender;
    }

    Receiver ^ Session::createReceiver(System::String ^ address)
    {
        // allocate a native receiver
        ::qpid::messaging::Receiver * receiverp = new ::qpid::messaging::Receiver;

        // create the receiver
        *receiverp = sessionp->createReceiver(QpidMarshal::ToNative(address));

        // create a managed receiver
        Receiver ^ newReceiver = gcnew Receiver(receiverp, this);

        return newReceiver;
    }

    Connection ^ Session::getConnection()
    {
        return parentConnectionp;
    }

    bool Session::hasError()
    {
        return sessionp->hasError();
    }

    void Session::checkError()
    {
        sessionp->checkError();
    }
}}}}
