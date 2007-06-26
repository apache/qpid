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
#include <vector>
#include <assert.h>

namespace qpid {
namespace framing {

/** Handler for objects of type T. */
template <class T> struct Handler {
    typedef T Type;
    typedef shared_ptr<Handler> Ptr;

    virtual ~Handler() {}
    virtual void handle(T) = 0;
    virtual void link(Ptr next_) { next=next_; }
  protected:
    void nextHandler(T data) { if (next) next->handle(data); }
  private:
    Ptr next;
};


/** Factory interface that takes a context of type C */
template <class T, class C> struct HandlerFactory {
    virtual ~HandlerFactory() {}
    typedef typename Handler<T>::Ptr Ptr;

    /** Create a handler */
    virtual Ptr create(C context) = 0;

    /** Create a handler and link it to next */
    Ptr create(C context, Ptr next) {
        Ptr h=create(context);
        h->link(next);
    }
};

/** Factory implementation template */
template <class FH, class C>
struct HandlerFactoryImpl : public HandlerFactory<typename FH::Type, C> {
    shared_ptr<Handler<typename FH::Type> > create(C context) {
        return typename FH::Ptr(new FH(context));
    }
};

/** A factory chain is a vector of handler factories used to create
 * handler chains. The chain does not own the factories.
 */
template <class T, class C>
struct HandlerFactoryChain : public std::vector<HandlerFactory<T,C>* > {
    typedef typename Handler<T>::Ptr Ptr;
    
    /** Create a handler chain, return the first handler.
     *@param context - passed to each factory.
     */
    Ptr create(C context) {
        return this->create(context, this->begin());
    }

  private:
    typedef typename std::vector<HandlerFactory<T,C>*>::iterator iterator;
    Ptr create(C context, iterator i) {
        if (i != this->end()) {
            Ptr h=(*i)->create(context);
            h->link(create(context, i+1));
            return h;
        }
        return Ptr();
    }
};

}}


#endif  /*!QPID_FRAMING_HANDLER_H*/
