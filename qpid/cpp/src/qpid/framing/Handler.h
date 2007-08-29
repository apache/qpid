#ifndef QPID_FRAMING_HANDLER_H
#define QPID_FRAMING_HANDLER_H

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
#include "qpid/shared_ptr.h"
#include <assert.h>

namespace qpid {
namespace framing {

/** Generic handler that can be linked into chains. */
template <class T>
struct Handler {
    typedef T HandledType;

    Handler(Handler<T>* next_=0) : next(next_) {}
    virtual ~Handler() {}
    virtual void handle(T) = 0;
    
    /** Allow functor syntax for calling handle */ 
    void operator()(T t)  { handle(t); }


    /** Pointer to next handler in a linked list. */
    Handler<T>* next;

    /** A Chain is a handler that forwards to a modifiable
     * linked list of handlers.
     */
    struct Chain : public Handler<T> {
        Chain(Handler<T>* first) : Handler(first) {}
        void operator=(Handler<T>* h) { next = h; }
        void handle(T t) { (*next)(t); }
        // TODO aconway 2007-08-29: chain modifier ops here.
    };

    /** In/out pair of handler chains. */
    struct Chains {
        Chains(Handler<T>* in_=0, Handler<T>* out_=0) : in(in_), out(out_) {}
        void reset(Handler<T>* in_=0, Handler<T>* out_=0) { in = in_; out = out_; }
        Chain in;
        Chain out;
    };

    /** Adapt any void(T) functor as a Handler.
     * Functor<F>(f) will copy f.
     * Functor<F&>(f) will only take a reference to x.
     */
    template <class F> class Functor : public Handler<T> {
      public:
        Functor(F f, Handler<T>* next=0) : Handler<T>(next), functor(f) {}
        void handle(T t) { functor(t); }
      private:
        F functor;
    };

    /** Adapt a member function of X as a Handler.
     * MemFun<X, X::f> will copy x.
     * MemFun<X&, X::f> will only take a reference to x.
     */
    template <class X, void(*M)(T)>
    class MemFun : public Handler<T> {
      public:
        MemFun(X x, Handler<T>* next=0) : Handler(next), object(x) {}
        void handle(T t) { object.*M(t); }
      private:
        X object;
    };

    /** Support for implementing an in-out handler pair as a single class.
     * Public interface is Handler<T>::Chains pair, but implementation
     * overrides handleIn, handleOut functions in a single class.
     */
    class InOutHandler {
      public:
        virtual ~InOutHandler() {}

        InOutHandler() :
            in(*this, &InOutHandler::handleIn),
            out(*this, &InOutHandler::handleOut) {}

        MemFun<InOutHandler, &InOutHandler::handleIn> in;
        MemFun<InOutHandler, &InOutHandler::handleOut> out;

      protected:
        virtual void handleIn(T) = 0;
        virtual void handleOut(T) = 0;
      private:
    };

};



}}
#endif  /*!QPID_FRAMING_HANDLER_H*/
//
