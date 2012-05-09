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
 * \file State.h
 */

#ifndef qpid_asyncStore_jrnl2_State_h_
#define qpid_asyncStore_jrnl2_State_h_

#include "Streamable.h"

namespace qpid {
namespace asyncStore {
namespace jrnl2 {

/**
 * \brief Utility class to manage a simple state machine defined by an enumeration \a E.
 *
 * It is necessary that enumeration E has its default / initial value coded as value 0. This enumeration would
 * be typically coded as follows:
 * \code
 * typedef enum {STATE1=0, STATE2, STATE3} myStates_t;
 * class myStateMachine : State<myStates_t> {
 * protected:
 *     void set(myStates_t s) { ... } // Implement state machine logic and transition checking here
 * public:
 *     // State transition verbs
 *     void action1() { set(STATE2); }
 *     void action2() { set(STATE3); }
 * };
 * \endcode
 *
 * For child classes, the state machine transition logic is implemented in set(const E), and the state change
 * verbs are implemented by calling set() with values from E. A call to reset() will return the state to the
 * state numerically equal to 0, irrespective of the current state or state transition logic.
 *
 * Function getAsStr() returns the current state as a string, and is usually implemented by calling a local static
 * function which translates the values of E into c-string literals.
 */
template <class E> class State : public Streamable {
public:
    /**
     * \brief Default constructor, which sets the internal state m_state to the state in E which is numerically
     * equal to 0.
     */
    State<E>() :
            Streamable(),
            m_state(E(0))
    {}

    /**
     * \brief Constructor which sets the internal state m_state to the value \a s.
     */
    State<E>(const E s) :
            Streamable(),
            m_state(s)
    {}

    /**
     * \brief Copy constructor, which sets the internal state m_state to the same value as that of the copied
     * instance \a s.
     */
    State<E>(const State<E>& s) :
            Streamable(),
            m_state(s.m_state)
    {}

    /**
     * \brief Virtual destructor
     */
    virtual ~State()
    {}

    /**
     * \brief Get the internal state m_state as an enumeration of type E.
     *
     * \returns The internal state m_state.
     */
    virtual E get() const
    {
        return m_state;
    }

    /**
     * \brief Get the internal state m_state as a c-string.
     *
     * \returns Internal state m_state as a c-string.
     */
    virtual const char* getAsStr() const = 0;

    /**
     * \brief Reset the internal state m_state to the value of \a s or, if not supplied, to the state in E which is
     * numerically equal to 0.
     *
     * \param s The state to which this instance should be reset. The default value is the member of E with a
     * numerical value of 0.
     *
     * \note This call ignores the state transition logic in set() and forces the state to 0. This is not intended
     * as a normal part of the state machine operation, but rather for reusing a state machine instance or in
     * conditions where you need to force a change over the state machine logic. For cases where the normal state
     * machine logic returns the state to its initial point, a verb should be created which uses set() and is
     * subjected to the transition logic and checking of set().
     */
    virtual void reset(const E s = E(0))
    {
        m_state = s;
    }

    /**
     * \brief Stream the string representation of the internal state m_state.
     *
     * \param os Stream to which the string representation of the internal state m_state will be sent.
     */
    virtual void toStream(std::ostream& os = std::cout) const
    {
        os << getAsStr();
    }

protected:
    E m_state;  ///< Local state of this state machine instance.

    /**
     * \brief Set (or change) the value of m_state. This function must implement the state machine checks for
     * legal state change transitions, and throw an exception if an illegal state transition is requested.
     *
     * \param s State to which this machine should be changed.
     */
    virtual void set(const E s) = 0;

    /**
     * \brief Compare the state \a s with the current state.
     *
     * \returns \b true if \a s is the same as internal state m_state, \b false otherwise.
     */
    virtual bool is(const E s) const
    {
        return m_state == s;
    }

};

}}} // namespace qpid::asyncStore::jrnl2

#endif // qpid_asyncStore_jrnl2_State_h_
