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
 * \file JournalRunState.h
 */

#ifndef qpid_asyncStore_jrnl2_JournalRunState_h_
#define qpid_asyncStore_jrnl2_JournalRunState_h_

#include "qpid/asyncStore/jrnl2/State.h"

namespace qpid {
namespace asyncStore {
namespace jrnl2 {

/**
 * \brief Enumeration of valid AsyncJournal states.
 */
typedef enum  {
    JS_NONE = 0,            ///< Not initialized, not capable of running (This is the default/reset state).
    JS_RECOVERING_PHASE_1,  ///< Recovery phase 1 (analyzing and restoring AsyncJournal state from journal files).
    JS_RECOVERING_PHASE_2,  ///< Recovery phase 2 (recovering message content and restoring messages in the broker).
    JS_INITIALIZING,        ///< Initialization of a new journal, including preparing journal files.
    JS_RUNNING,             ///< Initialization or recovery complete, journal running and accepting content.
    JS_STOPPING,            ///< Journal stopping. No new requests for service accepted, waiting for internal state to become consistent.
    JS_STOPPED              ///< Journal stopped. No new requests for service accepted, store in a consistent state.
} journalState_t;

/**
 * \brief State manager for the state of an AsyncJournal instance.
 *
 * The AsyncJournal has the following states and transitions:
 * \dot
 * digraph journalState_t {
 *   node [fontname=Helvetica, fontsize=8];
 *   JS_NONE [URL="\ref JS_NONE"]
 *   JS_RECOVERING_PHASE_1 [URL="\ref JS_RECOVERING_PHASE_1"]
 *   JS_RECOVERING_PHASE_2 [URL="\ref JS_RECOVERING_PHASE_2"]
 *   JS_INITIALIZING [URL="\ref JS_INITIALIZING"]
 *   JS_RUNNING [URL="\ref JS_RUNNING"]
 *   JS_STOPPING [URL="\ref JS_STOPPING"]
 *   JS_STOPPED [URL="\ref JS_STOPPED"]
 *
 *   edge [fontname=Helvetica, fontsize=8]
 *   JS_NONE->JS_INITIALIZING [label="initialize()" URL="\ref qpid::jrnl2::JournalRunState::initialize()"]
 *   JS_INITIALIZING->JS_RUNNING [label="run()" URL="\ref qpid::jrnl2::JournalRunState::run()"]
 *   JS_NONE->JS_RECOVERING_PHASE_1 [label="recoverPhase1()" URL="\ref qpid::jrnl2::JournalRunState::recoverPhase1()"]
 *   JS_RECOVERING_PHASE_1->JS_RECOVERING_PHASE_2 [label="recoverPhase2()" URL="\ref qpid::jrnl2::JournalRunState::recoverPhase2()"]
 *   JS_RECOVERING_PHASE_2->JS_RUNNING [label="run()" URL="\ref qpid::jrnl2::JournalRunState::run()"]
 *   JS_RUNNING->JS_STOPPING [label="stop()" URL="\ref qpid::jrnl2::JournalRunState::stop()"]
 *   JS_STOPPING->JS_STOPPED [label="stopped()" URL="\ref qpid::jrnl2::JournalRunState::stopped()"]
 * }
 * \enddot
 */
class JournalRunState : public State<journalState_t>
{
public:
    /**
     * \brief Default constructor, setting internal state to JS_NONE.
     */
    JournalRunState();

    /**
     * \brief Constructor allowing the internal state to be preset to any valid value of journalState_t.
     *
     * \param s State value to which the internal state of this new instance should be initialized.
     */
    JournalRunState(const journalState_t s);

    /**
     * \brief Copy constructor.
     * \param s Instance from which this new instance state should be copied.
     */
    JournalRunState(const JournalRunState& s);

    /**
     * \brief Virtual destructor
     */
    virtual ~JournalRunState();

    // State change functions

    /**
     * \brief Changes the journal state from JS_NONE to JS_INITIALIZING.
     *
     * \throws JournalException with JERR_ILLEGALJRNLSTATECHANGE if current state makes it illegal to change
     * to JS_INITIALIZING according to the state machine semantics.
     */
    void initialize();

    /**
     * \brief Changes the journal state from JS_NONE to JS_RECOVERING_PHASE_1.
     * \throws JournalException with JERR_ILLEGALJRNLSTATECHANGE if current state makes it illegal to change
     * to JS_RECOVERING_PHASE_1 according to the state machine semantics.
     */
    void recoverPhase1();

    /**
     * \brief Changes the journal state from JS_RECOVERING_PHASE_1 to JS_RECOVERING_PHASE_2.
     *
     * \throws JournalException with JERR_ILLEGALJRNLSTATECHANGE if current state makes it illegal to change
     * to JS_RECOVERING_PHASE_2 according to the state machine semantics.
     */
    void recoverPhase2();

    /**
     * \brief Changes the journal state from JS_INITIALIZING or JS_RECOVERING_PHASE_2 to JS_RUNNING.
     *
     * \throws JournalException with JERR_ILLEGALJRNLSTATECHANGE if current state makes it illegal to change
     * to JS_RUNNING according to the state machine semantics.
     */
    void run();

    /**
     * \brief Changes the journal state from JS_RUNNING to JS_STOPPING.
     *
     * \throws JournalException with JERR_ILLEGALJRNLSTATECHANGE if current state makes it illegal to change
     * to JS_STOPPING according to the state machine semantics.
     */
    void stop();

    /**
     * \brief Changes the journal state from JS_STOPPING to JS_STOPPED.
     *
     * \throws JournalException with JERR_ILLEGALJRNLSTATECHANGE if current state makes it illegal to change
     * to JS_STOPPED according to the state machine semantics.
     */
    void stopped();

    /**
     * \brief Get the current state in a string form.
     *
     * \returns String representation of internal state m_state as returned by static function s_toStr().
     */
    const char* getAsStr() const;

    /**
     * \brief Static helper function to convert a numeric journalState_t type to a string representation.
     */
    static const char* s_toStr(const journalState_t s);

private:
    /**
     * \brief Set (or change) the value of m_state. This function implements the state machine checks for
     * legal state change transitions, and throw an exception if an illegal state transition is requested.
     *
     * \param s State to which this machine should be changed.
     * \throws JournalException with JERR_ILLEGALJRNLSTATECHANGE if current state makes it illegal to change
     * to the requested state \a s.
     */
    void set(const journalState_t s);

};

}}} // namespace qpid::asyncStore::jrnl2

#endif // qpid_asyncStore_jrnl2_JournalRunState_h_
