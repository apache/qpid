#ifndef _sys_windows_SslAsynchIO
#define _sys_windows_SslAsynchIO

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

#include "qpid/sys/AsynchIO.h"
#include "qpid/sys/IntegerTypes.h"
#include "qpid/sys/Poller.h"
#include "qpid/CommonImportExport.h"
#include "qpid/sys/Mutex.h"
#include <boost/function.hpp>
#include <boost/shared_ptr.hpp>
#include <windows.h>
// security.h needs to see this to distinguish from kernel use.
#define SECURITY_WIN32
#include <security.h>
#include <Schnlsp.h>
#undef SECURITY_WIN32

namespace qpid {
namespace sys {
namespace windows {

/*
 * SSL/Schannel shim between the frame-handling and AsynchIO layers.
 * SslAsynchIO creates a regular AsynchIO object to handle I/O and this class
 * gets involved for SSL negotiations and encrypt/decrypt. The details of
 * how this all works are invisible to the layers on either side. The only
 * change from normal AsynchIO usage is that there's an extra callback
 * from SslAsynchIO to indicate that the initial session negotiation is
 * complete.
 *
 * The details of session negotiation are different for client and server
 * SSL roles. These differences are handled by deriving separate client
 * and server role classes.
 */
class SslAsynchIO : public qpid::sys::AsynchIO {
public:
    typedef boost::function1<void, SECURITY_STATUS> NegotiateDoneCallback;

    SslAsynchIO(const qpid::sys::Socket& s,
                CredHandle hCred,
                ReadCallback rCb,
                EofCallback eofCb,
                DisconnectCallback disCb,
                ClosedCallback cCb = 0,
                BuffersEmptyCallback eCb = 0,
                IdleCallback iCb = 0,
                NegotiateDoneCallback nCb = 0);
    ~SslAsynchIO();

    virtual void queueForDeletion();

    virtual void start(qpid::sys::Poller::shared_ptr poller);
    virtual void createBuffers(uint32_t size);
    virtual void queueReadBuffer(BufferBase* buff);
    virtual void unread(BufferBase* buff);
    virtual void queueWrite(BufferBase* buff);
    virtual void notifyPendingWrite();
    virtual void queueWriteClose();
    virtual bool writeQueueEmpty();
    virtual void requestCallback(RequestCallback);
    virtual BufferBase* getQueuedBuffer();
    virtual SecuritySettings getSecuritySettings(void);

protected:
    CredHandle credHandle;

    // AsynchIO layer below that's actually doing the I/O
    qpid::sys::AsynchIO *aio;
    Mutex lock;

    // Track what the state of the SSL session is. Have to know when it's
    // time to notify the upper layer that the session is up, and also to
    // know when it's not legit to pass data through to either side.
    enum { Negotiating, Running, Redo, ShuttingDown } state;
    CtxtHandle ctxtHandle;
    TimeStamp credExpiry;

    // Client- and server-side SSL subclasses implement these to do the
    // proper negotiation steps. negotiateStep() is called with a buffer
    // just received from the peer.
    virtual void startNegotiate() = 0;
    virtual void negotiateStep(BufferBase *buff) = 0;

    // The negotiating steps call one of these when it's finalized:
    void negotiationDone();
    void negotiationFailed(SECURITY_STATUS status);

private:
    // These are callbacks from AsynchIO to here.
    void sslDataIn(qpid::sys::AsynchIO& a, BufferBase *buff);
    void idle(qpid::sys::AsynchIO&);
    void reapCheck();

    // These callbacks are to the layer above.
    ReadCallback readCallback;
    IdleCallback idleCallback;
    NegotiateDoneCallback negotiateDoneCallback;

    volatile bool queuedDelete;
    volatile bool queuedClose;
    volatile bool reapCheckPending;
    bool started;

    // Address of peer, in case it's needed for logging.
    std::string peerAddress;

    // Partial buffer of decrypted plaintext given back by the layer above.
    AsynchIO::BufferBase *leftoverPlaintext;

    SecPkgContext_StreamSizes schSizes;
};

/*
 * SSL/Schannel client-side shim between the frame-handling and AsynchIO
 * layers.
 */
class ClientSslAsynchIO : public SslAsynchIO {
public:
    // Args same as for SslIoShim, with the addition of brokerHost which is
    // the expected SSL name of the server.
    QPID_COMMON_EXTERN ClientSslAsynchIO(const std::string& brokerHost,
                                         const qpid::sys::Socket& s,
                                         CredHandle hCred,
                                         ReadCallback rCb,
                                         EofCallback eofCb,
                                         DisconnectCallback disCb,
                                         ClosedCallback cCb = 0,
                                         BuffersEmptyCallback eCb = 0,
                                         IdleCallback iCb = 0,
                                         NegotiateDoneCallback nCb = 0);

private:
    std::string serverHost;
    bool clientCertRequested;

    // Client- and server-side SSL subclasses implement these to do the
    // proper negotiation steps. negotiateStep() is called with a buffer
    // just received from the peer.
    void startNegotiate();
    void negotiateStep(BufferBase *buff);
};
/*
 * SSL/Schannel server-side shim between the frame-handling and AsynchIO
 * layers.
 */
class ServerSslAsynchIO : public SslAsynchIO {
public:
    QPID_COMMON_EXTERN ServerSslAsynchIO(bool clientMustAuthenticate,
                                         const qpid::sys::Socket& s,
                                         CredHandle hCred,
                                         ReadCallback rCb,
                                         EofCallback eofCb,
                                         DisconnectCallback disCb,
                                         ClosedCallback cCb = 0,
                                         BuffersEmptyCallback eCb = 0,
                                         IdleCallback iCb = 0,
                                         NegotiateDoneCallback nCb = 0);

private:
    bool clientAuth;

    // Client- and server-side SSL subclasses implement these to do the
    // proper negotiation steps. negotiateStep() is called with a buffer
    // just received from the peer.
    void startNegotiate();
    void negotiateStep(BufferBase *buff);
};

}}}   // namespace qpid::sys::windows

#endif // _sys_windows_SslAsynchIO
