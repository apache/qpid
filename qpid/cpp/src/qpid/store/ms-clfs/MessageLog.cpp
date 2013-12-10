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

#include <windows.h>
#include <clfsw32.h>
#include <exception>
#include <malloc.h>
#include <memory.h>
#include <qpid/framing/Buffer.h>
#include <qpid/log/Statement.h>
#include <qpid/sys/IntegerTypes.h>
#include <qpid/sys/windows/check.h>

#include "MessageLog.h"
#include "Lsn.h"

namespace qpid {
namespace store {
namespace ms_clfs {

namespace {

// Structures that hold log records. Each has a type field at the start.
enum MessageEntryType {
    MessageStartEntry           = 1,
    MessageChunkEntry           = 2,
    MessageDeleteEntry          = 3,
    MessageEnqueueEntry         = 4,
    MessageDequeueEntry         = 5
};
static const uint32_t MaxMessageContentLength = 64 * 1024;

// Message-Start
struct MessageStart {
    MessageEntryType type;
    // If the complete message encoding doesn't fit, remainder is in
    // MessageChunk records to follow.
    // headerLength is the size of the message's header in content. It is
    // part of the totalLength and the segmentLength.
    uint32_t headerLength;
    uint32_t totalLength;
    uint32_t segmentLength;
    char content[MaxMessageContentLength];

    MessageStart()
      : type(MessageStartEntry),
        headerLength(0),
        totalLength(0),
        segmentLength(0) {}
};
// Message-Chunk
struct MessageChunk {
    MessageEntryType type;
    uint32_t segmentLength;
    char content[MaxMessageContentLength];

    MessageChunk() : type(MessageChunkEntry), segmentLength(0) {}
};
// Message-Delete
struct MessageDelete {
    MessageEntryType type;

    MessageDelete() : type(MessageDeleteEntry) {}
};
// Message-Enqueue
struct MessageEnqueue {
    MessageEntryType type;
    uint64_t queueId;
    uint64_t transId;

    MessageEnqueue(uint64_t qId = 0, uint64_t tId = 0)
        : type(MessageEnqueueEntry), queueId(qId), transId(tId) {}
};
// Message-Dequeue
struct MessageDequeue {
    MessageEntryType type;
    uint64_t queueId;
    uint64_t transId;

    MessageDequeue(uint64_t qId = 0, uint64_t tId = 0)
        : type(MessageDequeueEntry), queueId(qId), transId(tId) {}
};

}   // namespace

void
MessageLog::initialize()
{
    // Write something to occupy the first record, preventing a real message
    // from being lsn/id 0. Delete of a non-existant id is easily tossed
    // during recovery if no other messages have caused the tail to be moved
    // up past this dummy record by then.
    deleteMessage(0, 0);
}

uint32_t
MessageLog::marshallingBufferSize()
{
    size_t biggestNeed = std::max(sizeof(MessageStart), sizeof(MessageEnqueue));
    uint32_t defSize = static_cast<uint32_t>(biggestNeed);
    uint32_t minSize = Log::marshallingBufferSize();
    if (defSize <= minSize)
        return minSize;
    // Round up to multiple of minSize
    return (defSize + minSize) / minSize * minSize;
}

uint64_t
MessageLog::add(const boost::intrusive_ptr<qpid::broker::PersistableMessage>& msg)
{
    // The message may be too long to fit in one record; if so, write
    // Message-Chunk records to contain the rest. If it does all fit in one
    // record, though, optimize the encoding by going straight to the
    // Message-Start record rather than encoding then copying to the record.
    // In all case
    MessageStart entry;
    uint32_t encodedMessageLength = msg->encodedSize();
    entry.headerLength = msg->encodedHeaderSize();
    entry.totalLength = encodedMessageLength;
    CLFS_LSN location, lastChunkLsn;
    std::auto_ptr<char> encodeStage;
    char *encodeBuff = 0;
    bool oneRecord = encodedMessageLength <= MaxMessageContentLength;
    if (oneRecord) {
        encodeBuff = entry.content;
        entry.segmentLength = encodedMessageLength;
    }
    else {
        encodeStage.reset(new char[encodedMessageLength]);
        encodeBuff = encodeStage.get();
        entry.segmentLength = MaxMessageContentLength;
    }
    qpid::framing::Buffer buff(encodeBuff, encodedMessageLength);
    msg->encode(buff);
    if (!oneRecord)
        memcpy_s(entry.content, sizeof(entry.content),
                 encodeBuff, entry.segmentLength);
    uint32_t entryLength = static_cast<uint32_t>(sizeof(entry));
    entryLength -= (MaxMessageContentLength - entry.segmentLength);
    location = write(&entry, entryLength);
    // Write any Message-Chunk records before setting the message's id.
    uint32_t sent = entry.segmentLength;
    uint32_t remaining = encodedMessageLength - entry.segmentLength;
    while (remaining > 0) {
        MessageChunk chunk;
        chunk.segmentLength = std::max(MaxMessageContentLength, remaining);
        memcpy_s(chunk.content, sizeof(chunk.content),
                 encodeStage.get() + sent, chunk.segmentLength);
        entryLength = static_cast<uint32_t>(sizeof(chunk));
        entryLength -= (MaxMessageContentLength - chunk.segmentLength);
        lastChunkLsn = write(&chunk, entryLength, &location);
        sent += chunk.segmentLength;
        remaining -= chunk.segmentLength;
    }
    return lsnToId(location);
}

void
MessageLog::deleteMessage(uint64_t messageId, uint64_t newFirstId)
{
    MessageDelete deleteEntry;
    CLFS_LSN msgLsn = idToLsn(messageId);
    write(&deleteEntry, sizeof(deleteEntry), &msgLsn);
    if (newFirstId != 0)
        moveTail(idToLsn(newFirstId));
}

// Load part or all of a message's content from previously stored
// log record(s).
void
MessageLog::loadContent(uint64_t messageId,
                        std::string& data,
                        uint64_t offset,
                        uint32_t length)
{
}

void
MessageLog::recordEnqueue (uint64_t messageId,
                           uint64_t queueId,
                           uint64_t transactionId)
{
    MessageEnqueue entry(queueId, transactionId);
    CLFS_LSN msgLsn = idToLsn(messageId);
    write(&entry, sizeof(entry), &msgLsn);
}

void
MessageLog::recordDequeue (uint64_t messageId,
                           uint64_t queueId,
                           uint64_t transactionId)
{
    MessageDequeue entry(queueId, transactionId);
    CLFS_LSN msgLsn = idToLsn(messageId);
    write(&entry, sizeof(entry), &msgLsn);
}

void
MessageLog::recover(qpid::broker::RecoveryManager& recoverer,
                    qpid::store::MessageMap& messageMap,
                    std::map<uint64_t, std::vector<RecoveredMsgOp> >& messageOps)
{
    // If context and content needs to be saved while reassembling messages
    // split across log records, save the info and reassembly buffer.
    struct MessageBlocks {
        uint32_t totalLength;
        uint32_t soFarLength;
        boost::shared_ptr<char> content;

        MessageBlocks() : totalLength(0), soFarLength(0), content((char*)0) {}
    };
    std::map<uint64_t, MessageBlocks> reassemblies;
    std::map<uint64_t, MessageBlocks>::iterator at;

    QPID_LOG(debug, "Recovering message log");

    // Note that there may be message refs in the log which are deleted, so
    // be sure to only add msgs at message-start record, and ignore those
    // that don't have an existing message record.
    // Get the base LSN - that's how to say "start reading at the beginning"
    CLFS_INFORMATION info;
    ULONG infoLength = sizeof (info);
    BOOL ok = ::GetLogFileInformation(handle, &info, &infoLength);
    QPID_WINDOWS_CHECK_NOT(ok, 0);

    // Pointers for the various record types that can be assigned in the
    // reading loop below.
    MessageStart *start;
    MessageChunk *chunk;
    MessageEnqueue *enqueue;
    MessageDequeue *dequeue;

    qpid::store::MessageMap::iterator messageMapSpot;
    qpid::store::MessageQueueMap::iterator queueMapSpot;
    PVOID recordPointer;
    ULONG recordLength;
    CLFS_RECORD_TYPE recordType = ClfsDataRecord;
    CLFS_LSN messageLsn, current, undoNext;
    PVOID readContext;
    uint64_t msgId;
    // Note 'current' in case it's needed below; ReadNextLogRecord returns it
    // via a parameter.
    current = info.BaseLsn;
    ok = ::ReadLogRecord(marshal,
                         &info.BaseLsn,
                         ClfsContextForward,
                         &recordPointer,
                         &recordLength,
                         &recordType,
                         &undoNext,
                         &messageLsn,
                         &readContext,
                         0);
    while (ok) {
        // All the record types this class writes have a MessageEntryType in the
        // beginning. Based on that, do what's needed.
        MessageEntryType *t =
            reinterpret_cast<MessageEntryType *>(recordPointer);
        switch(*t) {
        case MessageStartEntry:
            start = reinterpret_cast<MessageStart *>(recordPointer);
            msgId = lsnToId(current);
            QPID_LOG(debug, "Message Start, id " << msgId);
            // If the message content is split across multiple log records, save
            // this content off to the side until the remaining record(s) are
            // located.
            if (start->totalLength == start->segmentLength) {  // Whole thing
                // Start by recovering the header then see if the rest of
                // the content is desired.
                qpid::framing::Buffer buff(start->content, start->headerLength);
                qpid::broker::RecoverableMessage::shared_ptr m =
                    recoverer.recoverMessage(buff);
                m->setPersistenceId(msgId);
                messageMap[msgId] = m;
                uint32_t contentLength =
                    start->totalLength - start->headerLength;
                if (m->loadContent(contentLength)) {
                    qpid::framing::Buffer content(&(start->content[start->headerLength]),
                                                  contentLength);
                    m->decodeContent(content);
                }
            }
            else {
                // Save it in a block big enough.
                MessageBlocks b;
                b.totalLength = start->totalLength;
                b.soFarLength = start->segmentLength;
                b.content.reset(new char[b.totalLength]);
                memcpy_s(b.content.get(), b.totalLength,
                         start->content, start->segmentLength);
                reassemblies[msgId] = b;
            }
            break;
        case MessageChunkEntry:
            chunk = reinterpret_cast<MessageChunk *>(recordPointer);
            // Remember, all entries chained to MessageStart via previous.
            msgId = lsnToId(messageLsn);
            QPID_LOG(debug, "Message Chunk for id " << msgId);
            at = reassemblies.find(msgId);
            if (at == reassemblies.end()) {
                QPID_LOG(debug, "Message frag for " << msgId <<
                                " but no start; discarded");
            }
            else {
                MessageBlocks *b = &(at->second);
                if (b->soFarLength + chunk->segmentLength > b->totalLength)
                    throw std::runtime_error("Invalid message chunk length");
                memcpy_s(b->content.get() + b->soFarLength,
                         b->totalLength - b->soFarLength,
                         chunk->content,
                         chunk->segmentLength);
                b->soFarLength += chunk->segmentLength;
                if (b->totalLength == b->soFarLength) {
                    qpid::framing::Buffer buff(b->content.get(),
                                               b->totalLength);
                    qpid::broker::RecoverableMessage::shared_ptr m =
                        recoverer.recoverMessage(buff);
                    m->setPersistenceId(msgId);
                    messageMap[msgId] = m;
                    reassemblies.erase(at);
                }
            }
            break;
        case MessageDeleteEntry:
            msgId = lsnToId(messageLsn);
            QPID_LOG(debug, "Message Delete, id " << msgId);
            messageMap.erase(msgId);
            messageOps.erase(msgId);
            break;
        case MessageEnqueueEntry:
            enqueue = reinterpret_cast<MessageEnqueue *>(recordPointer);
            msgId = lsnToId(messageLsn);
            QPID_LOG(debug, "Message " << msgId << " Enqueue on queue " <<
                            enqueue->queueId << ", txn " << enqueue->transId);
            if (messageMap.find(msgId) == messageMap.end()) {
                QPID_LOG(debug,
                         "Message " << msgId << " doesn't exist; discarded");
            }
            else {
                std::vector<RecoveredMsgOp>& ops = messageOps[msgId];
                RecoveredMsgOp op(RECOVERED_ENQUEUE,
                                  enqueue->queueId,
                                  enqueue->transId);
                ops.push_back(op);
            }
            break;
        case MessageDequeueEntry:
            dequeue = reinterpret_cast<MessageDequeue *>(recordPointer);
            msgId = lsnToId(messageLsn);
            QPID_LOG(debug, "Message " << msgId << " Dequeue from queue " <<
                            dequeue->queueId);
            if (messageMap.find(msgId) == messageMap.end()) {
                QPID_LOG(debug,
                         "Message " << msgId << " doesn't exist; discarded");
            }
            else {
                std::vector<RecoveredMsgOp>& ops = messageOps[msgId];
                RecoveredMsgOp op(RECOVERED_DEQUEUE,
                                  dequeue->queueId,
                                  dequeue->transId);
                ops.push_back(op);
            }
            break;
        default:
            throw std::runtime_error("Bad message log entry type");
        }

        recordType = ClfsDataRecord;
        ok = ::ReadNextLogRecord(readContext,
                                 &recordPointer,
                                 &recordLength,
                                 &recordType,
                                 0,             // No userLsn
                                 &undoNext,
                                 &messageLsn,
                                 &current,
                                 0);
    }
    DWORD status = ::GetLastError();
    ::TerminateReadLog(readContext);
    if (status == ERROR_HANDLE_EOF) { // No more records
        QPID_LOG(debug, "Message log recovered");
        return;
    }
    throw QPID_WINDOWS_ERROR(status);
}

}}}  // namespace qpid::store::ms_clfs
