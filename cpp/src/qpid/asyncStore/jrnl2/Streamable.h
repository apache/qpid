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
 * \file Streamable.h
 */

#ifndef qpid_asyncStore_jrnl2_Streamable_h_
#define qpid_asyncStore_jrnl2_Streamable_h_

#include <iostream>
#include <string>

namespace qpid {
namespace asyncStore {
namespace jrnl2 {

/**
 * \brief Abstract class which provides the mechanisms to stream
 *
 * An abstract class which provides stream functions. The toStream() function must be implemented by subclasses,
 * and is used by the remaining functions. For convenience, toString() returns a std::string object.
 */
class Streamable
{
public:
    /**
     * \brief Virtual destructor
     */
    virtual ~Streamable();

    /**
     * \brief Stream some representation of the object to an output stream.
     *
     * \param os Output stream to which the class data is to be streamed.
     */
    virtual void toStream(std::ostream& os = std::cout) const = 0;

    /**
     * \brief Creates a string representation of the test parameters.
     *
     * Convenience feature which creates and returns a std::string object containing the content of toStream().
     *
     * \returns Content of toStream()
     */
     std::string toString() const;

    /**
     * \brief Stream the object to an output stream
     *
     * \param os Stream to which output is to be sent.
     * \param s Object instance to be streamed.
     * \returns Original stream passed into parameter \a os.
     */
    friend std::ostream& operator<<(std::ostream& os, const Streamable& s);

    /**
     * \brief Stream the object to an output stream through an object pointer
     *
     * \param os Stream to which output is to be sent.
     * \param sPtr Pointer to object instance to be streamed.
     * \returns Original stream passed into parameter \a os.
     */
    friend std::ostream& operator<<(std::ostream& os, const Streamable* sPtr);

};

}}} // namespace qpid::asyncStore::jrnl2

#endif // qpid_asyncStore_jrnl2_Streamable_h_
