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
 * \file DataWrComplState.h
 */

#ifndef qpid_asyncStore_jrnl2_DataWrComplState_h_
#define qpid_asyncStore_jrnl2_DataWrComplState_h_

#include "State.h"

namespace qpid {
namespace asyncStore {
namespace jrnl2 {

/**
 * \brief Enumeration of valid data record write completion states.
 */
typedef enum {
    WR_NONE = 0,    ///< Data record has not been written into async store buffer
    WR_PART,        ///< Data record has been partly written to store buffer
    WR_COMPLETE     ///< Data record write to store buffer is complete
} wrComplState_t;

/**
 * \brief State machine for the data record write completion state - ie whether the data record has been written
 * to the store buffer.
 *
 * The following state diagram shows valid write buffer states and transitions:
 * \dot
 * digraph wrComplState_t {
 *   node [fontname=Helvetica, fontsize=8];
 *   WR_NONE [URL="\ref WR_NONE"]
 *   WR_PART [URL="\ref WR_PART"]
 *   WR_COMPLETE [URL="\ref WR_COMPLETE"]
 *
 *   edge [fontname=Helvetica, fontsize=8]
 *   WR_NONE->WR_PART [label=" partComplete()" URL="\ref qpid::jrnl2::DataWrComplState::partComplete()"]
 *   WR_NONE->WR_COMPLETE [label=" complete()" URL="\ref qpid::jrnl2::DataWrComplState::complete()"]
 *   WR_PART->WR_COMPLETE [label=" complete()" URL="\ref qpid::jrnl2::DataWrComplState::complete()"]
 * }
 * \enddot
 */
class DataWrComplState: public State<wrComplState_t> {
public:
    /**
     * \brief Default constructor, setting internal state to WR_NONE.
     */
    DataWrComplState();

    /**
     * \brief Constructor allowing the internal state to be preset to any valid value of wrComplState_t.
     *
     * \param s State value to which the internal state of this new instance should be initialized.
     */
    DataWrComplState(const wrComplState_t s);

    /**
     * \brief Copy constructor.
     *
     * \param s Instance from which this new instance state should be copied.
     */
    DataWrComplState(const DataWrComplState& s);

    /**
     * \brief Virtual destructor
     */
    virtual ~DataWrComplState();

    /**
     * \brief Changes the data record operational state from WR_NONE or WR_PART to WR_COMPLETE.
     *
     * \throws JournalException with JERR_ILLEGALDTOKOPSTATECHANGE if current state makes it illegal to change
     * to WR_COMPLETE according to the state machine semantics.
     */
    void complete();

    /**
     * \brief Changes the data record operational state from WR_NONE to WR_PART.
     *
     * \throws JournalException with JERR_ILLEGALDTOKOPSTATECHANGE if current state makes it illegal to change
     * to WR_PART according to the state machine semantics.
     */
    void partComplete();

    /**
     * \brief Get the current state in a string form.
     *
     * \returns String representation of internal state m_state as returned by static function s_toStr().
     */
    const char* getAsStr() const;

    /**
     * \brief Static helper function to convert a numeric wrComplState_t type to a string representation.
     */
    static const char* s_toStr(const wrComplState_t s);

protected:
    /**
     * \brief Set (or change) the value of m_state. This function implements the state machine checks for
     * legal state change transitions, and throw an exception if an illegal state transition is requested.
     *
     * \param s State to which this machine should be changed.
     * \throws JournalException with JERR_ILLEGALDTOKOPSTATECHANGE if current state makes it illegal to change
     * to the requested state \a s.
     */
    void set(const wrComplState_t s);

};

}}} // namespace qpid::asyncStore::jrnl2

#endif // qpid_asyncStore_jrnl2_DataWrComplState_h_
