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

#include "SslAsynchIO.h"
#include "qpid/sys/Mutex.h"
#include "qpid/sys/Socket.h"
#include "qpid/sys/Poller.h"
#include "qpid/sys/SecuritySettings.h"
#include "qpid/sys/Thread.h"
#include "qpid/sys/Time.h"
#include "qpid/log/Statement.h"

#include "qpid/sys/windows/check.h"

// security.h needs to see this to distinguish from kernel use.
#define SECURITY_WIN32
#include <security.h>
#include <Schnlsp.h>
#undef SECURITY_WIN32

#include <queue>
#include <boost/bind.hpp>
#include "AsynchIO.h"

namespace qpid {
namespace sys {
namespace windows {

namespace {

    /*
     * To make the SSL encryption more efficient, set up a new BufferBase
     * that leaves room for the SSL header to be prepended and the SSL
     * trailer to be appended.
     *
     * This works by accepting a properly formed BufferBase, remembering it,
     * and resetting the members of this struct to reflect the reserved
     * header and trailer areas. It's only needed for giving buffers up to
     * the frame layer for writing into.
     */
    struct SslIoBuff : public qpid::sys::AsynchIO::BufferBase {
        qpid::sys::AsynchIO::BufferBase* aioBuff;

        SslIoBuff (qpid::sys::AsynchIO::BufferBase *base,
                   const SecPkgContext_StreamSizes &sizes)
          : qpid::sys::AsynchIO::BufferBase(&base->bytes[sizes.cbHeader],
                                            std::min(base->byteCount - sizes.cbHeader - sizes.cbTrailer,
                                                     sizes.cbMaximumMessage)),
            aioBuff(base)
        {}

        ~SslIoBuff() {}
    };
}

SslAsynchIO::SslAsynchIO(const qpid::sys::Socket& s,
                         CredHandle hCred,
                         ReadCallback rCb,
                         EofCallback eofCb,
                         DisconnectCallback disCb,
                         ClosedCallback cCb,
                         BuffersEmptyCallback eCb,
                         IdleCallback iCb,
                         NegotiateDoneCallback nCb) :
    credHandle(hCred),
    aio(0),
    state(Negotiating),
    readCallback(rCb),
    idleCallback(iCb),
    negotiateDoneCallback(nCb),
    queuedDelete(false),
    queuedClose(false),
    reapCheckPending(false),
    started(false),
    leftoverPlaintext(0)
{
    SecInvalidateHandle(&ctxtHandle);
    peerAddress = s.getPeerAddress();
    aio = qpid::sys::AsynchIO::create(s,
                                      boost::bind(&SslAsynchIO::sslDataIn, this, _1, _2),
                                      eofCb,
                                      disCb,
                                      cCb,
                                      eCb,
                                      boost::bind(&SslAsynchIO::idle, this, _1));
}

SslAsynchIO::~SslAsynchIO() {
    leftoverPlaintext = 0;
}

void SslAsynchIO::queueForDeletion() {
    // Called exactly once, always on the IO completion thread.
    bool authenticated = (state != Negotiating);
    state = ShuttingDown;
    if (authenticated) {
        // Tell SChannel we are done.
        DWORD shutdown = SCHANNEL_SHUTDOWN;
        SecBuffer shutBuff;
        shutBuff.cbBuffer = sizeof(DWORD);
        shutBuff.BufferType = SECBUFFER_TOKEN;
        shutBuff.pvBuffer = &shutdown;
        SecBufferDesc desc;
        desc.ulVersion = SECBUFFER_VERSION;
        desc.cBuffers = 1;
        desc.pBuffers = &shutBuff;
        ::ApplyControlToken(&ctxtHandle, &desc);
        negotiateStep(0);
    }

    queueWriteClose();
    queuedDelete = true;

    // This method effectively disconnects the layer above; pass it on the
    // AsynchIO and delete.
    aio->queueForDeletion();

    if (!reapCheckPending)
        delete(this);
}

void SslAsynchIO::start(qpid::sys::Poller::shared_ptr poller) {
    aio->start(poller);
    started = true;
    startNegotiate();
}

void SslAsynchIO::createBuffers(uint32_t size) {
    // Reserve an extra buffer to hold unread plaintext or trailing encrypted input.
    windows::AsynchIO *waio = dynamic_cast<windows::AsynchIO*>(aio);
    waio->setBufferCount(waio->getBufferCount() + 1);
    aio->createBuffers(size);
}

void SslAsynchIO::queueReadBuffer(AsynchIO::BufferBase* buff) {
    aio->queueReadBuffer(buff);
}

void SslAsynchIO::unread(AsynchIO::BufferBase* buff) {
    // This is plaintext data being given back for more. Since it's already
    // decrypted, don't give it back to the aio layer; keep it to append
    // any new data for the upper layer.
    assert(buff);
    buff->squish();
    assert(leftoverPlaintext == 0);
    leftoverPlaintext = buff;
}

void SslAsynchIO::queueWrite(AsynchIO::BufferBase* buff) {
    // @@TODO: Need to delay the write if the session is renegotiating.

    // Should not have gotten here without an SslIoBuff. This assert is
    // primarily to catch any stray cases where write is called with a buffer
    // not obtained via getQueuedBuffer.
    SslIoBuff *sslBuff = dynamic_cast<SslIoBuff*>(buff);
    assert(sslBuff != 0);

    // Encrypt and hand off to the io layer. Remember that the upper layer's
    // encoding was working on, and adjusting counts for, the SslIoBuff.
    // Update the count of the original BufferBase before handing off to
    // the I/O layer.
    buff = sslBuff->aioBuff;
    SecBuffer buffs[4];
    buffs[0].cbBuffer = schSizes.cbHeader;
    buffs[0].BufferType = SECBUFFER_STREAM_HEADER;
    buffs[0].pvBuffer = buff->bytes;        // This space was left by SslIoBuff
    buffs[1].cbBuffer = sslBuff->dataCount;
    buffs[1].BufferType = SECBUFFER_DATA;
    buffs[1].pvBuffer = sslBuff->bytes;
    buffs[2].cbBuffer = schSizes.cbTrailer;
    buffs[2].BufferType = SECBUFFER_STREAM_TRAILER;
    buffs[2].pvBuffer = &sslBuff->bytes[sslBuff->dataCount];
    buffs[3].cbBuffer = 0;
    buffs[3].BufferType = SECBUFFER_EMPTY;
    buffs[3].pvBuffer = 0;
    SecBufferDesc buffDesc;
    buffDesc.ulVersion = SECBUFFER_VERSION;
    buffDesc.cBuffers = 4;
    buffDesc.pBuffers = buffs;
    SECURITY_STATUS status = ::EncryptMessage(&ctxtHandle, 0, &buffDesc, 0);

    // EncryptMessage encrypts the data in place. The header and trailer
    // areas were left previously and must now be included in the updated
    // count of bytes to write to the peer.
    delete sslBuff;
    buff->dataCount = buffs[0].cbBuffer + buffs[1].cbBuffer + buffs[2].cbBuffer;
    aio->queueWrite(buff);
}

void SslAsynchIO::notifyPendingWrite() {
    aio->notifyPendingWrite();
}

void SslAsynchIO::queueWriteClose() {
    qpid::sys::Mutex::ScopedLock l(lock);
    if (queuedClose)
        return;
    queuedClose = true;
    if (started) {
        reapCheckPending = true;
        // Move tear down logic to an IO thread.
        aio->requestCallback(boost::bind(&SslAsynchIO::reapCheck, this));
    }
    aio->queueWriteClose();
}

void SslAsynchIO::reapCheck() {
    // Serialized check in the IO thread whether to self-delete.
    reapCheckPending = false;
    if (queuedDelete)
        delete(this);
}

bool SslAsynchIO::writeQueueEmpty() {
    return aio->writeQueueEmpty();
}

// Queue the specified callback for invocation from an I/O thread.
void SslAsynchIO::requestCallback(RequestCallback callback) {
    aio->requestCallback(callback);
}

/**
 * Return a queued buffer read to put new data in for writing.
 * This method ALWAYS returns a SslIoBuff reflecting a BufferBase from
 * the aio layer that has header and trailer space reserved.
 */
AsynchIO::BufferBase* SslAsynchIO::getQueuedBuffer() {
    SslIoBuff *sslBuff = 0;
    BufferBase* buff = aio->getQueuedBuffer();
    if (buff == 0)
        return 0;

    sslBuff = new SslIoBuff(buff, schSizes);
    return sslBuff;
}

SecuritySettings SslAsynchIO::getSecuritySettings() {
    SecPkgContext_KeyInfo info;
    memset(&info, 0, sizeof(info));
    ::QueryContextAttributes(&ctxtHandle, SECPKG_ATTR_KEY_INFO, &info);

    SecuritySettings settings;
    settings.ssf = info.KeySize;
    settings.authid = std::string();
    return settings;
}

void SslAsynchIO::negotiationDone() {
    switch(state) {
    case Negotiating:
        ::QueryContextAttributes(&ctxtHandle,
                                 SECPKG_ATTR_STREAM_SIZES,
                                 &schSizes);
        state = Running;
        if (negotiateDoneCallback)
            negotiateDoneCallback(SEC_E_OK);
        break;
    case Redo:
        state = Running;
        break;
    case ShuttingDown:
        break;
    default:
        assert(0);
    }
}

void SslAsynchIO::negotiationFailed(SECURITY_STATUS status) {
    QPID_LOG(notice, "SSL negotiation failed to " << peerAddress << ": " <<
                     qpid::sys::strError(status));
    if (negotiateDoneCallback)
        negotiateDoneCallback(status);
    else
        queueWriteClose();
}

void SslAsynchIO::sslDataIn(qpid::sys::AsynchIO& a, BufferBase *buff) {
    if (state == ShuttingDown) {
        return;
    }
    if (state != Running) {
        negotiateStep(buff);
        return;
    }

    // Decrypt one block; if there's legit data, pass it on through.
    // However, it's also possible that the peer hasn't supplied enough
    // data yet, or the session needs to be renegotiated, or the session
    // is ending.
    SecBuffer recvBuffs[4];
    recvBuffs[0].cbBuffer = buff->dataCount;
    recvBuffs[0].BufferType = SECBUFFER_DATA;
    recvBuffs[0].pvBuffer = &buff->bytes[buff->dataStart];
    recvBuffs[1].BufferType = SECBUFFER_EMPTY;
    recvBuffs[2].BufferType = SECBUFFER_EMPTY;
    recvBuffs[3].BufferType = SECBUFFER_EMPTY;
    SecBufferDesc buffDesc;
    buffDesc.ulVersion = SECBUFFER_VERSION;
    buffDesc.cBuffers = 4;
    buffDesc.pBuffers = recvBuffs;
    SECURITY_STATUS status = ::DecryptMessage(&ctxtHandle, &buffDesc, 0, NULL);
    if (status != SEC_E_OK) {
        if (status == SEC_E_INCOMPLETE_MESSAGE) {
            // Give the partially filled buffer back and get more data
            a.unread(buff);
        }
        else {
            // Don't need this any more...
            a.queueReadBuffer(buff);

            if (status == SEC_I_RENEGOTIATE) {
                state = Redo;
                negotiateStep(0);
            }
            else if (status == SEC_I_CONTEXT_EXPIRED) {
                queueWriteClose();
            }
            else {
                throw QPID_WINDOWS_ERROR(status);
            }
        }
        return;
    }

    // All decrypted and verified... continue with AMQP. The recvBuffs have
    // been set up by DecryptMessage to demarcate the SSL header, data, and
    // trailer, as well as any extra data left over. Walk through and find
    // that info, adjusting the buff data accordingly to reflect only the
    // actual decrypted data.
    // If there's extra data, copy that out to a new buffer and run through
    // this method again.
    char *extraBytes = 0;
    int32_t extraLength = 0;
    BufferBase *extraBuff = 0;
    for (int i = 0; i < 4; i++) {
        switch (recvBuffs[i].BufferType) {
        case SECBUFFER_STREAM_HEADER:
            buff->dataStart += recvBuffs[i].cbBuffer;
            // Fall through - also don't count these bytes as data
        case SECBUFFER_STREAM_TRAILER:
            buff->dataCount -= recvBuffs[i].cbBuffer;
            break;
        case SECBUFFER_EXTRA:
            extraBytes = (char *) recvBuffs[i].pvBuffer;
            extraLength = recvBuffs[i].cbBuffer;
            break;
        default:
            break;
        }
    }

    // Since we've already taken (possibly) all the available bytes from the
    // aio layer, need to be sure that everything that's processable is
    // processed before returning back to aio. It could be that any
    // leftoverPlaintext data plus new buff data won't fit in one buffer, so
    // need to keep going around the input processing loop until either
    // all the bytes are gone, or there's less than a full frame remaining
    // (so we can count on more bytes being on the way via aio).
    do {
        BufferBase *temp = 0;
        // See if there was partial data left over from last time. If so, append this new
        // data to that and release the current buff back to aio. Assume that
        // leftoverPlaintext was squished so the data starts at 0.
        if (leftoverPlaintext != 0) {
            // There is leftover data; append all the new data that will fit.
            int32_t count = buff->dataCount;
            if (count) {
                if (leftoverPlaintext->dataCount + count > leftoverPlaintext->byteCount)
                    count = (leftoverPlaintext->byteCount - leftoverPlaintext->dataCount);
                ::memmove(&leftoverPlaintext->bytes[leftoverPlaintext->dataCount],
                          &buff->bytes[buff->dataStart], count);
                leftoverPlaintext->dataCount += count;
                buff->dataCount -= count;
                buff->dataStart += count;
                // Prepare to pass the buffer up. Beware that the read callback
                // may do an unread(), so move the leftoverPlaintext pointer
                // out of the way. It also may release the buffer back to aio,
                // so in either event, the pointer passed to the callback is not
                // valid on return.
                temp = leftoverPlaintext;
                leftoverPlaintext = 0;
            }
            else {
                // All decrypted data used up, decrypt some more or get more from the aio
                if (extraLength) {
                    buff->dataStart = extraBytes - buff->bytes;
                    buff->dataCount = extraLength;
                    sslDataIn(a, buff);
                    return;
                }
                else {
                    a.queueReadBuffer(buff);
                    return;
                }
            }
        }
        else {
            // Use buff, but first offload data not yet encrypted
            if (extraLength) {
                // Very important to get this buffer from the downstream aio.
                // The ones constructed from the local getQueuedBuffer() are
                // restricted size for encrypting. However, data coming up from
                // TCP may have a bunch of SSL segments coalesced and be much
                // larger than the maximum single SSL segment.
                extraBuff = a.getQueuedBuffer();
                if (0 == extraBuff) {
                    // No leftoverPlaintext, so a spare buffer should be available
                    throw QPID_WINDOWS_ERROR(WSAENOBUFS);
                }
                memmove(extraBuff->bytes, extraBytes, extraLength);
                extraBuff->dataCount = extraLength;
                extraLength = 0;
            }
            temp = buff;
            buff = 0;
        }
        if (readCallback) {
            // The callback guard here is to prevent an upcall from deleting
            // this out from under us via queueForDeletion().
            readCallback(*this, temp);
        }
        else
            a.queueReadBuffer(temp); // What else can we do with this???
    } while (buff != 0);

    // Ok, the current decrypted data is done. If there was any extra data,
    // go back and handle that.
    if (extraBuff != 0) {
        sslDataIn(a, extraBuff);
    }
}

void SslAsynchIO::idle(qpid::sys::AsynchIO&) {
    // Don't relay idle indication to layer above until SSL session is up.
    if (state == Running) {
        state = Running;
        if (idleCallback)
            idleCallback(*this);
    }
}

/**************************************************/

namespace {

bool unsafeNegotiatedTlsVersion(CtxtHandle &ctxtHandle) {
    // See if SChannel ultimately negotiated <= SSL3, perhaps due to
    // global registry settings.
    SecPkgContext_ConnectionInfo info;
    ::QueryContextAttributes(&ctxtHandle, SECPKG_ATTR_CONNECTION_INFO, &info);
    // Ascending bit patterns denote newer SSL/TLS protocol versions
    return (info.dwProtocol < SP_PROT_TLS1_SERVER) ? true : false;
}

} // namespace

/**************************************************/

ClientSslAsynchIO::ClientSslAsynchIO(const std::string& brokerHost,
                                     const qpid::sys::Socket& s,
                                     CredHandle hCred,
                                     ReadCallback rCb,
                                     EofCallback eofCb,
                                     DisconnectCallback disCb,
                                     ClosedCallback cCb,
                                     BuffersEmptyCallback eCb,
                                     IdleCallback iCb,
                                     NegotiateDoneCallback nCb) :
    SslAsynchIO(s, hCred, rCb, eofCb, disCb, cCb, eCb, iCb, nCb),
    serverHost(brokerHost), clientCertRequested(false)
{
}

void ClientSslAsynchIO::startNegotiate() {
    // SEC_CHAR is non-const, so do all the typing here.
    SEC_CHAR *host = const_cast<SEC_CHAR *>(serverHost.c_str());

    // Need a buffer to receive the token to send to the server.
    BufferBase *buff = aio->getQueuedBuffer();
    ULONG ctxtRequested = ISC_REQ_STREAM | ISC_REQ_USE_SUPPLIED_CREDS;
    ULONG ctxtAttrs;
    // sendBuffs gets information to forward to the peer.
    SecBuffer sendBuffs[2];
    sendBuffs[0].cbBuffer = buff->byteCount;
    sendBuffs[0].BufferType = SECBUFFER_TOKEN;
    sendBuffs[0].pvBuffer = buff->bytes;
    sendBuffs[1].cbBuffer = 0;
    sendBuffs[1].BufferType = SECBUFFER_EMPTY;
    sendBuffs[1].pvBuffer = 0;
    SecBufferDesc sendBuffDesc;
    sendBuffDesc.ulVersion = SECBUFFER_VERSION;
    sendBuffDesc.cBuffers = 2;
    sendBuffDesc.pBuffers = sendBuffs;
    SECURITY_STATUS status = ::InitializeSecurityContext(&credHandle,
                                                         NULL,
                                                         host,
                                                         ctxtRequested,
                                                         0,
                                                         0,
                                                         NULL,
                                                         0,
                                                         &ctxtHandle,
                                                         &sendBuffDesc,
                                                         &ctxtAttrs,
                                                         NULL);

    if (status == SEC_I_CONTINUE_NEEDED) {
        buff->dataCount = sendBuffs[0].cbBuffer;
        aio->queueWrite(buff);
    }
}

void ClientSslAsynchIO::negotiateStep(BufferBase* buff) {
    // SEC_CHAR is non-const, so do all the typing here.
    SEC_CHAR *host = const_cast<SEC_CHAR *>(serverHost.c_str());
    ULONG ctxtRequested = ISC_REQ_STREAM | ISC_REQ_USE_SUPPLIED_CREDS;
    ULONG ctxtAttrs;

    // tokenBuffs describe the buffer that's coming in. It should have
    // a token from the SSL server.
    SecBuffer tokenBuffs[2];
    tokenBuffs[0].cbBuffer = buff ? buff->dataCount : 0;
    tokenBuffs[0].BufferType = SECBUFFER_TOKEN;
    tokenBuffs[0].pvBuffer = buff ? buff->bytes : 0;
    tokenBuffs[1].cbBuffer = 0;
    tokenBuffs[1].BufferType = SECBUFFER_EMPTY;
    tokenBuffs[1].pvBuffer = 0;
    SecBufferDesc tokenBuffDesc;
    tokenBuffDesc.ulVersion = SECBUFFER_VERSION;
    tokenBuffDesc.cBuffers = 2;
    tokenBuffDesc.pBuffers = tokenBuffs;

    // Need a buffer to receive any token to send back to the server.
    BufferBase *sendbuff = aio->getQueuedBuffer();
    // sendBuffs gets information to forward to the peer.
    SecBuffer sendBuffs[2];
    sendBuffs[0].cbBuffer = sendbuff->byteCount;
    sendBuffs[0].BufferType = SECBUFFER_TOKEN;
    sendBuffs[0].pvBuffer = sendbuff->bytes;
    sendBuffs[1].cbBuffer = 0;
    sendBuffs[1].BufferType = SECBUFFER_EMPTY;
    sendBuffs[1].pvBuffer = 0;
    SecBufferDesc sendBuffDesc;
    sendBuffDesc.ulVersion = SECBUFFER_VERSION;
    sendBuffDesc.cBuffers = 2;
    sendBuffDesc.pBuffers = sendBuffs;

    SECURITY_STATUS status = ::InitializeSecurityContext(&credHandle,
                                                         &ctxtHandle,
                                                         host,
                                                         ctxtRequested,
                                                         0,
                                                         0,
                                                         &tokenBuffDesc,
                                                         0,
                                                         NULL,
                                                         &sendBuffDesc,
                                                         &ctxtAttrs,
                                                         NULL);

    if (status == SEC_E_INCOMPLETE_MESSAGE) {
        // Not enough - get more data from the server then try again.
        aio->unread(buff);
        aio->queueReadBuffer(sendbuff);   // Don't need this one for now...
        return;
    }
    // Done with the buffer that came in...
    if (buff)
        aio->queueReadBuffer(buff);
    if (status == SEC_I_CONTINUE_NEEDED) {
        // check if server has requested a client certificate
        if (!clientCertRequested) {
            SecPkgContext_IssuerListInfoEx caList;
            memset(&caList, 0, sizeof(caList));
            ::QueryContextAttributes(&ctxtHandle, SECPKG_ATTR_ISSUER_LIST_EX, &caList);
            if (caList.cIssuers > 0)
                clientCertRequested = true;
            if (caList.aIssuers)
                ::FreeContextBuffer(caList.aIssuers);
        }

        sendbuff->dataCount = sendBuffs[0].cbBuffer;
        aio->queueWrite(sendbuff);
        return;
    }
    // Nothing to send back to the server...
    aio->queueReadBuffer(sendbuff);

    if (status == SEC_E_OK && unsafeNegotiatedTlsVersion(ctxtHandle)) {
        // Refuse a connection that negotiates to less than TLS 1.0.
        QPID_LOG(notice, "client SSL negotiation to unsafe protocol version.");
        status = SEC_E_UNSUPPORTED_FUNCTION;
    }

    // SEC_I_CONTEXT_EXPIRED means session stop complete; SEC_E_OK can be
    // either session stop or negotiation done (session up).
    if (status == SEC_E_OK || status == SEC_I_CONTEXT_EXPIRED)
        negotiationDone();
    else {
        if (clientCertRequested && status == SEC_E_CERT_UNKNOWN)
            // ISC_REQ_USE_SUPPLIED_CREDS makes us reponsible for this case
            // (no client cert).  Map it to its counterpart:
            status = SEC_E_INCOMPLETE_CREDENTIALS;
        negotiationFailed(status);
    }
}

/*************************************************/

ServerSslAsynchIO::ServerSslAsynchIO(bool clientMustAuthenticate,
                                     const qpid::sys::Socket& s,
                                     CredHandle hCred,
                                     ReadCallback rCb,
                                     EofCallback eofCb,
                                     DisconnectCallback disCb,
                                     ClosedCallback cCb,
                                     BuffersEmptyCallback eCb,
                                     IdleCallback iCb,
                                     NegotiateDoneCallback nCb) :
    SslAsynchIO(s, hCred, rCb, eofCb, disCb, cCb, eCb, iCb, nCb),
    clientAuth(clientMustAuthenticate)
{
}

void ServerSslAsynchIO::startNegotiate() {
    // Nothing... need the client to send a token first.
}

void ServerSslAsynchIO::negotiateStep(BufferBase* buff) {
    ULONG ctxtRequested = ASC_REQ_STREAM;
    if (clientAuth)
        ctxtRequested |= ASC_REQ_MUTUAL_AUTH;
    ULONG ctxtAttrs;

    // tokenBuffs describe the buffer that's coming in. It should have
    // a token from the SSL server except if shutting down or renegotiating.
    SecBuffer tokenBuffs[2];
    tokenBuffs[0].cbBuffer = buff ? buff->dataCount : 0;
    tokenBuffs[0].BufferType = SECBUFFER_TOKEN;
    tokenBuffs[0].pvBuffer = buff ? buff->bytes : 0;
    tokenBuffs[1].cbBuffer = 0;
    tokenBuffs[1].BufferType = SECBUFFER_EMPTY;
    tokenBuffs[1].pvBuffer = 0;
    SecBufferDesc tokenBuffDesc;
    tokenBuffDesc.ulVersion = SECBUFFER_VERSION;
    tokenBuffDesc.cBuffers = 2;
    tokenBuffDesc.pBuffers = tokenBuffs;

    // Need a buffer to receive any token to send back to the server.
    BufferBase *sendbuff = aio->getQueuedBuffer();
    // sendBuffs gets information to forward to the peer.
    SecBuffer sendBuffs[2];
    sendBuffs[0].cbBuffer = sendbuff->byteCount;
    sendBuffs[0].BufferType = SECBUFFER_TOKEN;
    sendBuffs[0].pvBuffer = sendbuff->bytes;
    sendBuffs[1].cbBuffer = 0;
    sendBuffs[1].BufferType = SECBUFFER_EMPTY;
    sendBuffs[1].pvBuffer = 0;
    SecBufferDesc sendBuffDesc;
    sendBuffDesc.ulVersion = SECBUFFER_VERSION;
    sendBuffDesc.cBuffers = 2;
    sendBuffDesc.pBuffers = sendBuffs;
    PCtxtHandle ctxtHandlePtr = (SecIsValidHandle(&ctxtHandle)) ? &ctxtHandle : 0;
    SECURITY_STATUS status = ::AcceptSecurityContext(&credHandle,
                                                     ctxtHandlePtr,
                                                     &tokenBuffDesc,
                                                     ctxtRequested,
                                                     0,
                                                     &ctxtHandle,
                                                     &sendBuffDesc,
                                                     &ctxtAttrs,
                                                     NULL);
    if (status == SEC_E_INCOMPLETE_MESSAGE) {
        // Not enough - get more data from the server then try again.
        if (buff)
            aio->unread(buff);
        aio->queueReadBuffer(sendbuff);   // Don't need this one for now...
        return;
    }
    // Done with the buffer that came in...
    if (buff)
        aio->queueReadBuffer(buff);
    if (status == SEC_I_CONTINUE_NEEDED) {
        sendbuff->dataCount = sendBuffs[0].cbBuffer;
        aio->queueWrite(sendbuff);
        return;
    }
    // There may have been a token generated; if so, send it to the client.
    if (sendBuffs[0].cbBuffer > 0 && state != ShuttingDown) {
        sendbuff->dataCount = sendBuffs[0].cbBuffer;
        aio->queueWrite(sendbuff);
    }
    else
        // Nothing to send back to the server...
        aio->queueReadBuffer(sendbuff);

    if (status == SEC_E_OK && unsafeNegotiatedTlsVersion(ctxtHandle)) {
        // Refuse a connection that negotiates to less than TLS 1.0.
        QPID_LOG(notice, "server SSL negotiation to unsafe protocol version.");
        status = SEC_E_UNSUPPORTED_FUNCTION;
    }

    // SEC_I_CONTEXT_EXPIRED means session stop complete; SEC_E_OK can be
    // either session stop or negotiation done (session up).
    if (status == SEC_E_OK || status == SEC_I_CONTEXT_EXPIRED) {
        if (clientAuth)
            QPID_LOG(warning, "DID WE CHECK FOR CLIENT AUTH???");

        negotiationDone();
    }
    else {
        negotiationFailed(status);
    }
}

}}}  // namespace qpid::sys::windows
