#ifndef _Exception_
#define _Exception_

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

#include <exception>
#include <string>
#include <memory>
#include <boost/shared_ptr.hpp>
#include <boost/function.hpp>

namespace qpid
{
/**
 * Exception base class for all Qpid exceptions.
 */
class Exception : public std::exception
{
  protected:
    std::string whatStr;

  public:
    typedef boost::shared_ptr<Exception> shared_ptr;
    typedef boost::shared_ptr<const Exception> shared_ptr_const;
    typedef std::auto_ptr<Exception> auto_ptr;

    Exception() throw();
    Exception(const std::string& str) throw();
    Exception(const char* str) throw();
    Exception(const std::exception&) throw();

    virtual ~Exception() throw();

    virtual const char* what() const throw();
    virtual std::string toString() const throw();

    virtual std::auto_ptr<Exception> clone() const throw();
    virtual void throwSelf() const;


    /** Default message: "Unknown exception" or something like it. */
    static const char* defaultMessage;

    /**
     * Log a message of the form "message: what"
     *@param what Exception's what() message.
     *@param message Prefix message.
     */
    static void log(const char* what, const char* message = defaultMessage);

    /**
     * Log an exception.
     *@param e Exception to log.

     */
    static void log(
        const std::exception& e, const char* message = defaultMessage);
    

    /**
     * Log an unknown exception - use in catch(...)
     *@param message Prefix message.
     */
    static void logUnknown(const char* message = defaultMessage);

    /**
     * Wrapper template function to call another function inside
     * try/catch and log any exception. Use boost::bind to wrap
     * member function calls or functions with arguments.
     * 
     *@param f Function to call in try block.
     *@param retrhow If true the exception is rethrown.
     *@param message Prefix message.
     */
    template <class T>
    static T tryCatchLog(boost::function0<T> f, bool rethrow=true,
                         const char* message=defaultMessage)
    {
        try {
            return f();
        }
        catch (const std::exception& e) {
            log(e, message);
            if (rethrow)
                throw;
        }
        catch (...) {
            logUnknown(message);
            if (rethrow)
                throw;
        }
    }
    
};
    
} // namespace qpid

#endif  /*!_Exception_*/
