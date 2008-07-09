#ifndef QPID_HANDLERCHAIN_H
#define QPID_HANDLERCHAIN_H

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

#include <qpid/Plugin.h>
#include <boost/ptr_container/ptr_vector.hpp>
#include <memory>

namespace qpid {

/**
 * Chain-of-responsibility design pattern.
 * 
 * Construct a chain of objects deriving from Base. Each implements
 * Base::f by doing its own logic and then calling Base::f on the next
 * handler (or not if it chooses not to.)
 *
 * HandlerChain acts as a smart pointer to the first object in the chain.
 */
template <class Base> 
class HandlerChain {
  public:
    /** Base class for chainable handlers */
    class Handler : public Base {
      public:
        Handler() : next() {}
        virtual ~Handler() {}
        virtual void setNext(Base* next_) { next = next_; }

      protected:
        Base* next;
    };

    typedef std::auto_ptr<Handler> HandlerAutoPtr;
    
    /**@param target is the object at the end of the chain. */
    HandlerChain(Base& target) : first(&target) {}

    /** HandlerChain owns the ChainableHandler. */
    void push(HandlerAutoPtr h) {
      handlers.push_back(h.release());
        h->setNext(first);
        first = h.get();
    }

    // Smart pointer functions
    Base* operator*() { return first; }
    const Base* operator*() const { return first; }
    Base* operator->() { return first; }
    const Base* operator->() const { return first; }
    operator bool() const { return first; }
    
  private:
    boost::ptr_vector<Base> handlers;
    Base* first;
};

/**
 * A PluginHandlerChain calls Plugin::initAll(*this) on construction,
 * allowing plugins to add handlers.
 *
 * @param Tag can be any class, use to distinguish different plugin
 * chains with the same Base type.
 */
template <class Base, class Tag=void>
struct PluginHandlerChain : public HandlerChain<Base>,
                            public Plugin::Target
{
    PluginHandlerChain(Base& target) : HandlerChain<Base>(target) {
        Plugin::initAll(*this);
    }
};


} // namespace qpid

#endif  /*!QPID_HANDLERCHAIN_H*/
