#ifndef QPID_FRAMING_BLOB_H
#define QPID_FRAMING_BLOB_H

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

#include <boost/aligned_storage.hpp>
#include <boost/checked_delete.hpp>
#include <boost/utility/typed_in_place_factory.hpp>

#include <new>

#include <assert.h>


namespace boost {

/**
 * Boost doesn't provide the 0 arg version since it assumes
 * in_place_factory will be used when there is no default ctor.
 */
template <class T>
class typed_in_place_factory0 : public typed_in_place_factory_base {
  public:
    typedef T value_type ; 
    void apply ( void* address ) const { new (address) T(); }
};

template<class T>
typed_in_place_factory0<T> in_place() { return typed_in_place_factory0<T>(); }

} // namespace boost

namespace qpid {
namespace framing {

using boost::in_place;

template <class T>
void destroyT(void* ptr) { static_cast<T*>(ptr)->~T(); }
void nullDestroy(void*) {}

/**
 * A "blob" is a chunk of memory which can contain a single object at
 * a time arbitrary type, provided sizeof(T)<=blob.size(). Blob
 * ensures proper construction and destruction of its contents,
 * nothing else.
 * 
 * In particular the user must ensure the blob is big enough for its
 * contents and must know the type of object in the blob to cast get().
 *
 * Objects can be allocated directly in place using
 * construct(in_place<T>(...))  or copied using operator=.
 * Constructing a new object in the blob destroys the old one.
 */
template <size_t Size>
class Blob
{
    boost::aligned_storage<Size> store;
    void (*destroy)(void*);

    template<class TypedInPlaceFactory>
    void construct (const TypedInPlaceFactory& factory,
                    const boost::typed_in_place_factory_base* )
    {
        typedef typename TypedInPlaceFactory::value_type T;
        assert(sizeof(T) <= Size);
        clear();                // Destroy old object.
        factory.apply(store.address());
        destroy=&destroyT<T>;
    }

  public:
    /** Construct an empty blob. */
    Blob() : destroy(&nullDestroy) {}

    /** @see construct() */
    template<class Expr>
    Blob( const Expr & expr ) : destroy(nullDestroy) { construct(expr,&expr); }

    ~Blob() { clear(); }

    /** Construcct an object in the blob. Destroyes the previous object.
     *@param expr an expresion of the form: in_place<T>(x,y,z)
     * will construct an object using the constructor T(x,y,z)
     */
    template<class Expr> void
    construct(const Expr& expr) { construct(expr,&expr); }

    /** Copy construct an instance of T into the Blob. */
    template<class T>
    Blob& operator=(const T& x) { construct(in_place<T>(x)); }
    
    /** Get pointer to blob contents. Caller must know how to cast it. */
    void* get() { return store.address(); }

    /** Get const pointer to blob contents */
    const void* get() const { return store.address(); }
    
    /** Destroy the object in the blob making it empty. */
    void clear() {
        void (*saveDestroy)(void*) = destroy; // Exception safety
        destroy = &nullDestroy;
        saveDestroy(store.address());
    }

    /** True if there is no object allocated in the blob */
    bool empty() { return destroy==nullDestroy; }

    static size_t size() { return Size; }
};

}} // namespace qpid::framing



#endif  /*!QPID_FRAMING_BLOB_H*/
