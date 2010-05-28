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

#include <windows.h>
#include <msclr\lock.h>
#include <oletx2xa.h>
#include <string>
#include <limits>

#include "qpid/messaging/Connection.h"
#include "qpid/messaging/Session.h"
#include "qpid/messaging/exceptions.h"

#include "QpidMarshal.h"
#include "Connection.h"
#include "Session.h"
#include "QpidException.h"
#include "TypeTranslator.h"

namespace org {
namespace apache {
namespace qpid {
namespace messaging {

    /// <summary>
    /// Connection is a managed wrapper for a qpid::messaging::Connection
    /// </summary>

    // constructors
    Connection::Connection(System::String ^ url) :
        connectionp(new ::qpid::messaging::Connection(QpidMarshal::ToNative(url)))
    {
    }


    Connection::Connection(System::String ^ url,
                           System::Collections::Generic::Dictionary<
                               System::String ^, System::Object ^> ^ options) :
        connectionp(new ::qpid::messaging::Connection(QpidMarshal::ToNative(url)))
    {
        for each (System::Collections::Generic::KeyValuePair<System::String^, System::Object^> kvp in options)
        {
            setOption(kvp.Key, kvp.Value);
        }
    }


    Connection::Connection(System::String ^ url, System::String ^ options) :
        connectionp(new ::qpid::messaging::Connection(QpidMarshal::ToNative(url),
                    QpidMarshal::ToNative(options)))
    {
    }


    // Destructor
    Connection::~Connection()
    {
        Cleanup();
    }


    // Finalizer
    Connection::!Connection()
    {
        Cleanup();
    }


    // Destroys kept object
    // TODO: add lock
    void Connection::Cleanup()
    {
        if (NULL != connectionp)
        {
            delete connectionp;
            connectionp = NULL;
        }
    }


    void Connection::setOption(System::String ^ name, System::Object ^ value)
    {
        ::qpid::types::Variant entryValue;
        TypeTranslator::ManagedToNativeObject(value, entryValue);
        std::string entryName = QpidMarshal::ToNative(name);
        connectionp->::qpid::messaging::Connection::setOption(entryName, entryValue);
    }

    void Connection::open()
    {
        connectionp->open();
    }

    System::Boolean Connection::isOpen()
    {
        return connectionp->isOpen();
    }

    void Connection::close()
    {
        connectionp->close();
    }

    //
    // createTransactionalSession()
    //
    Session ^ Connection::createTransactionalSession()
    {
        return createTransactionalSession("");
    }


    Session ^ Connection::createTransactionalSession(System::String ^ name)
    {
        System::Exception          ^ newException = nullptr;
        ::qpid::messaging::Session * sessionp     = NULL;
        Session                    ^ newSession   = nullptr;

        try
        {
            // allocate native session
            sessionp = new ::qpid::messaging::Session ;

            // create native session
            *sessionp = connectionp->createTransactionalSession(QpidMarshal::ToNative(name));

            // create managed session
            newSession = gcnew Session(sessionp, this);
        } 
        catch (const ::qpid::types::Exception & error) 
        {
            String ^ errmsg = gcnew String(error.what());
            newException    = gcnew QpidException(errmsg);
        }
        catch (const std::exception & error) 
        {
            String ^ errmsg = gcnew String(error.what());
            newException    = gcnew QpidException(errmsg);
        } 
        catch ( ... )
        {
            newException = gcnew QpidException("Connection::createTransactionalSession unknown error");
        }
        finally
        {
            // Clean up and throw on caught exceptions
            if (newException != nullptr)
            {
                if (sessionp != NULL)
                {
                    delete sessionp;
                }
                throw newException;
            }
        }

        return newSession;
    }


    //
    // createSession()
    //
    Session ^ Connection::createSession()
    {
        return createSession("");
    }


    Session ^ Connection::createSession(System::String ^ name)
    {
        System::Exception          ^ newException = nullptr;
        ::qpid::messaging::Session * sessionp     = NULL;
        Session                    ^ newSession   = nullptr;

        try
        {
            // allocate native session
            sessionp = new ::qpid::messaging::Session ;

            // create native session
            *sessionp = connectionp->createSession(QpidMarshal::ToNative(name));

            // create managed session
            newSession = gcnew Session(sessionp, this);
        } 
        catch (const ::qpid::types::Exception & error) 
        {
            String ^ errmsg = gcnew String(error.what());
            newException    = gcnew QpidException(errmsg);
        }
        catch (const std::exception & error) 
        {
            String ^ errmsg = gcnew String(error.what());
            newException    = gcnew QpidException(errmsg);
        } 
        catch ( ... )
        {
            newException = gcnew QpidException("Connection::createSession unknown error");
        }
        finally
        {
            // Clean up and throw on caught exceptions
            if (newException != nullptr)
            {
                if (sessionp != NULL)
                {
                    delete sessionp;
                }
                throw newException;
            }
        }

        return newSession;
    }


    Session ^ Connection::getSession(System::String ^ name)
    {
        System::Exception          ^ newException = nullptr;
        ::qpid::messaging::Session * sess         = NULL;
        Session                    ^ newSession   = nullptr;
      
        try
        {
            const std::string n = QpidMarshal::ToNative(name);

            *sess = connectionp->::qpid::messaging::Connection::getSession(n);
            
            newSession = gcnew Session(sess, this);
        }
        catch (const ::qpid::types::Exception & error) 
        {
            String ^ errmsg = gcnew String(error.what());
            newException    = gcnew QpidException(errmsg);
        }
        catch (const std::exception & error) 
        {
            String ^ errmsg = gcnew String(error.what());
            newException    = gcnew QpidException(errmsg);
        } 
        catch ( ... )
        {
            newException = gcnew QpidException("Connection::getSession unknown error");
        }
        finally
        {
            // Clean up and throw on caught exceptions
            if (newException != nullptr)
            {
                if (sess != NULL)
                {
                    delete sess;
                }
                throw newException;
            }
        }

        return newSession;
    }
}}}}
