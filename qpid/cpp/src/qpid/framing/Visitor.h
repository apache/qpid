#ifndef QPID_FRAMING_VISITOR_H
#define QPID_FRAMING_VISITOR_H

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
 *
 */

#include "Handler.h"

#include <boost/mpl/vector.hpp>
#include <boost/mpl/inherit.hpp>
#include <boost/mpl/inherit_linearly.hpp>
#include <boost/type_traits/add_reference.hpp>

namespace qpid {
namespace framing {

// FIXME aconway 2007-07-30: Drop linking from handlers, use
// vector<shared_ptr<Handler<T> > for chains.

/** @file Visitor pattern for Handlers. */

/**
 * Interface for a handler visitor, inherits Handler<T> for each T in Types
 *@param Types A boost::mpl type sequence, e.g. boost::mpl::vector.
 */
template <class Types>
struct HandlerVisitor : public boost::mpl::inherit_linearly<
    Types, boost::mpl::inherit<boost::mpl::_1, Handler<boost::mpl::_2> >
    >::type
{};

/** Base class for hierarchy of objects visitable by Visitor */
template <class Visitor>
struct AbstractVisitable {
    virtual ~AbstractVisitable() {}
    typedef Visitor VisitorType;
    virtual void accept(Visitor& v) = 0;
};

/** Base class for concrete visitable types, implements accept.
 * @param T parameter type for appropriate Handler, may be a reference.
 * @param Base base class to inherit from.
 */
template <class T, class Base>
struct ConcreteVisitable : public Base {
    typedef typename boost::add_reference<T>::type TRef;
    void accept(typename Base::VisitorType& v) {
        static_cast<Handler<T>& >(v).handle(static_cast<TRef>(*this));
    }
};

}} // namespace qpid::framing

#endif  /*!QPID_FRAMING_VISITOR_H*/
