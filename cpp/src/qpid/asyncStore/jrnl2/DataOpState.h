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
 * \file DataOpState.h
 */

#ifndef qpid_asyncStore_jrnl2_DataOpState_h_
#define qpid_asyncStore_jrnl2_DataOpState_h_

#include "State.h"

namespace qpid {
namespace asyncStore {
namespace jrnl2 {

/**
 * \brief Enumeration of valid data record enqueue/dequeue states.
 */
typedef enum {
    OP_NONE = 0,    ///< Data record has not been processed by async store
    OP_ENQUEUE,     ///< Data record has been enqueued
    OP_DEQUEUE      ///< Data record has been dequeued
} opState_t;

/**
 * \brief State machine to control the data record operational state (ie none -> enqueued -> dequeued).
 *
 * The following state diagram shows valid data record operational state transitions:
 * \dot
 * digraph DataOpState {
 *   node [fontname=Helvetica, fontsize=8];
 *   OP_NONE [URL="\ref OP_NONE"]
 *   OP_ENQUEUE [URL="\ref OP_ENQUEUE"]
 *   OP_DEQUEUE [URL="\ref OP_DEQUEUE"]
 *
 *   edge [fontname=Helvetica, fontsize=8]
 *   OP_NONE->OP_ENQUEUE [label=" enqueue()" URL="\ref qpid::jrnl2::DataOpState::enqueue()"]
 *   OP_ENQUEUE->OP_DEQUEUE [label=" dequeue()" URL="\ref qpid::jrnl2::DataOpState::enqueue()"]
 * }
 * \enddot
 */
class DataOpState: public State<opState_t>
{
public:
    /**
     * \brief Default constructor, setting internal state to OP_NONE.
     */
    DataOpState();

    /**
     * \brief Constructor allowing the internal state to be preset to any valid value of opState_t.
     *
     * \param s State value to which the internal state of this new instance should be initialized.
     */
    DataOpState(const opState_t s);

    /**
     * \brief Copy constructor.
     *
     * \param s Instance from which this new instance state should be copied.
     */
    DataOpState(const DataOpState& s);

    /**
     * \brief Virtual destructor
     */
    virtual ~DataOpState();

    /**
     * \brief Changes the data record operational state from OP_NONE to OP_ENQUEUE.
     *
     * \throws JournalException with JERR_ILLEGALDTOKOPSTATECHANGE if current state makes it illegal to change
     * to OP_ENQUEUE according to the state machine semantics.
     */
    void enqueue();

    /**
     * \brief Changes the data record operational state from OP_ENQUEUE to OP_DEQUEUE.
     *
     * \throws JournalException with JERR_ILLEGALDTOKOPSTATECHANGE if current state makes it illegal to change
     * to OP_DEQUEUE according to the state machine semantics.
     */
    void dequeue();

    /**
     * \brief Get the current state in a string form.
     *
     * \returns String representation of internal state m_state as returned by static function s_toStr().
     */
    const char* getAsStr() const;

    /**
     * \brief Static helper function to convert a numeric opState_t type to a string representation.
     */
    static const char* s_toStr(const opState_t s);

private:
    /**
     * \brief Set (or change) the value of m_state. This function implements the state machine checks for
     * legal state change transitions, and throw an exception if an illegal state transition is requested.
     *
     * \param s State to which this machine should be changed.
     * \throws JournalException with JERR_ILLEGALDTOKOPSTATECHANGE if current state makes it illegal to change
     * to the requested state \a s.
     */
    void set(const opState_t s);

};

}}} // namespace qpid::asyncStore::jrnl2

#endif // qpid_asyncStore_jrnl2_DataOpState_h_
