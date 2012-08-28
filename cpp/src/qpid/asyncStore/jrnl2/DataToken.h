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

/**
 * \file DataToken.h
 */

#ifndef qpid_jrnl2_asyncStore_DataToken_h_
#define qpid_jrnl2_asyncStore_DataToken_h_

#include "qpid/asyncStore/jrnl2/DataOpState.h"
#include "qpid/asyncStore/jrnl2/DataWrComplState.h"
#include "qpid/asyncStore/jrnl2/RecordIdCounter.h"

#include <string>
#include <stdint.h> // uint64_t

namespace qpid {
namespace asyncStore {
namespace jrnl2 {

/**
 * \brief Contains the state and metadata for each record handled by the async store.
 *
 * The DataToken keeps the state of the data record through the AIO write and recovery process. It may also
 * be used to set certain properties of the data record.
 */
class DataToken : public Streamable
{
public:
    /**
     * \brief Default constructor, which internally assigns a unique record Id (RID).
     */
    DataToken();

    /**
     * \brief Constructor which accepts an external record id (RID). No check is made of the uniqueness of this RID.
     *
     * \param rid The externally assigned record Id, which must be unique across this journal instance within the
     * window presented by the journal files.
     */
    DataToken(const recordId_t rid);

    /**
     * \brief Virtual destructor
     */
    virtual ~DataToken();

    // Get functions

    /**
     * \brief Get the const DataOpState state manager object for this data token.
     *
     * \return \c const state of the data record associated with this DataToken instance.
     */
    const DataOpState& getDataOpState() const;

    /**
     * \brief Get the DataOpState state manager object for this data token.
     *
     * \return Non -\c const (modifiable) state of the data record associated with this DataToken instance.
     */
    DataOpState& getDataOpState();

    /**
     * \brief Get the const DataWrComplState state manager object for this data token.
     *
     * \return \c const state of the data record associated with this DataToken instance.
     */
    const DataWrComplState& getDataWrComplState() const;

    /**
     * \brief Get the DataWrComplState state manager object for this data token.
     *
     * \return Non -\c const (modifiable) state of the data record associated with this DataToken instance.
     */
    DataWrComplState& getDataWrComplState();

    /**
     * \brief Check if this data is marked as transient (will not be recovered).
     *
     * \return \c true if the data is transient, \c false otherwise.
     */
    bool isTransient() const;

    /**
     * \brief Check if this data is marked as external (i.e. has its content stored outside the store journal).
     *
     * \return \c true if the data is external (has its content stored outside the journal), \c false otherwise.
     */
    bool isExternal() const;

    /**
     * \brief Return the external storage location of the data if it is marked as external. This may be a path
     * or URL.
     *
     * \return Location or URI pointing to the location of the storage for this data record if it is marked external.
     */
    const std::string& getExternalLocation() const;

    /**
     * \brief Return the Record Id (RID) for this data record.
     *
     * \return Record Id (RID) for this data record.
     */
    recordId_t getRecordId() const;

    /**
     * \brief Checks if the Record Id assigned to this data record was supplied externally.
     *
     * \return \c true if the data record had its Record Id (RID) assigned externally from the store, \c false
     * otherwise.
     */
    bool isRecordIdExternal() const;

    /**
     * \brief Returns the recordId of the dequeuing record (if dequeued)
     *
     * \return The Record Id of the dequeue record which dequeues this record, or 0x0 if it has not been dequeued
     * (or cannot be dequeued).
     */
    recordId_t getDequeueRecordId() const;

    // Set functions

    /**
     * \brief Set the record Id (RID) for this record. No check is made of the uniqueness of this RID.
     */
    void setRecordId(const recordId_t rid);

    /**
     * \brief Set the record Id (RID) of the record which dequeues this record.
     */
    void setDequeueRecordId(const recordId_t drid);

    // Debug aid(s)

    /**
     * \brief Stream a formatted summary of the state of this DataToken object.
     */
    virtual void toStream(std::ostream& os) const;

private:
    DataOpState m_dataOpState;                  ///< Data operational state (none, enqueued, dequeued)
    DataWrComplState m_dataWrComplState;        ///< Data write completion state (none, part, complete)
    bool m_transientFlag;                       ///< True if the data record is transient (eg for Flow-To-Disk)
    bool m_externalFlag;                        ///< True if the data record is stored externally from the store
    std::string m_externalLocation;             ///< Location of data record when stored externally
    recordId_t m_recordId;                      ///< Record Id which is unique to this store instance
    bool m_externalRecordIdFlag;                ///< True if the record id was set through this token
    recordId_t m_dequeueRecordId;               ///< Record Id of the dequeue record for this data record

    static RecordIdCounter_t s_recordIdCounter; ///< Static instance keeps record Ids unique across system

};

}}} // namespace qpid::asyncStore::jrnl2

#endif // qpid_jrnl2_asyncStore_DataToken_h_
