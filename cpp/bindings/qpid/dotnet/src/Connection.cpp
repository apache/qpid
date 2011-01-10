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

namespace Org {
namespace Apache {
namespace Qpid {
namespace Messaging {

    /// <summary>
    /// Connection is a managed wrapper for a qpid::messaging::Connection
    /// </summary>

    // constructors
    Connection::Connection(System::String ^ url)
    {
        System::Exception ^ newException = nullptr;

        try 
		{
            connectionp = new ::qpid::messaging::Connection(QpidMarshal::ToNative(url));
        } 
        catch (const ::qpid::types::Exception & error) 
		{
            String ^ errmsg = gcnew String(error.what());
            newException    = gcnew QpidException(errmsg);
        }

		if (newException != nullptr) 
		{
	        throw newException;
		}
    }


    Connection::Connection(System::String ^ url,
                           System::Collections::Generic::Dictionary<
                               System::String ^, System::Object ^> ^ options)
    {
        System::Exception ^ newException = nullptr;

        try 
		{
            connectionp = new ::qpid::messaging::Connection(QpidMarshal::ToNative(url));

            for each (System::Collections::Generic::KeyValuePair<System::String^, System::Object^> kvp in options)
            {
                SetOption(kvp.Key, kvp.Value);
            }
        } 
        catch (const ::qpid::types::Exception & error) 
		{
            String ^ errmsg = gcnew String(error.what());
            newException    = gcnew QpidException(errmsg);
        }

		if (newException != nullptr) 
		{
	        throw newException;
		}
    }


    Connection::Connection(System::String ^ url, System::String ^ options)
    {
        System::Exception ^ newException = nullptr;

        try 
		{
            connectionp = new ::qpid::messaging::Connection(QpidMarshal::ToNative(url),
                    QpidMarshal::ToNative(options));
        } 
        catch (const ::qpid::types::Exception & error) 
		{
            String ^ errmsg = gcnew String(error.what());
            newException    = gcnew QpidException(errmsg);
        }

		if (newException != nullptr) 
		{
	        throw newException;
		}
    }


    // Copy constructor
    Connection::Connection(const Connection ^ connection)
    {
        System::Exception ^ newException = nullptr;

        try 
		{
            connectionp = new ::qpid::messaging::Connection(
                        *(const_cast<Connection ^>(connection)->NativeConnection));
        } 
        catch (const ::qpid::types::Exception & error) 
		{
            String ^ errmsg = gcnew String(error.what());
            newException    = gcnew QpidException(errmsg);
        }

		if (newException != nullptr) 
		{
	        throw newException;
		}
    }


    // Destructor
    Connection::~Connection()
    {
        this->!Connection();
    }


    // Finalizer
    Connection::!Connection()
    {
        msclr::lock lk(this);

        if (NULL != connectionp)
        {
            delete connectionp;
            connectionp = NULL;
        }
    }


    void Connection::SetOption(System::String ^ name, System::Object ^ value)
    {
        System::Exception ^ newException = nullptr;

        try 
		{
            ::qpid::types::Variant entryValue;
            TypeTranslator::ManagedToNativeObject(value, entryValue);
            std::string entryName = QpidMarshal::ToNative(name);
            connectionp->::qpid::messaging::Connection::setOption(entryName, entryValue);
        } 
        catch (const ::qpid::types::Exception & error) 
		{
            String ^ errmsg = gcnew String(error.what());
            newException    = gcnew QpidException(errmsg);
        }

		if (newException != nullptr) 
		{
	        throw newException;
		}
    }

    void Connection::Open()
    {
        System::Exception ^ newException = nullptr;

        try 
		{
            connectionp->open();
        } 
        catch (const ::qpid::types::Exception & error) 
		{
            String ^ errmsg = gcnew String(error.what());
            newException    = gcnew QpidException(errmsg);
        }

		if (newException != nullptr) 
		{
	        throw newException;
		}
    }

    void Connection::Close()
    {
        System::Exception ^ newException = nullptr;

        try 
		{
            connectionp->close();
        } 
        catch (const ::qpid::types::Exception & error) 
		{
            String ^ errmsg = gcnew String(error.what());
            newException    = gcnew QpidException(errmsg);
        }

		if (newException != nullptr) 
		{
	        throw newException;
		}
    }

    //
    // CreateTransactionalSession()
    //
    Session ^ Connection::CreateTransactionalSession()
    {
        return CreateTransactionalSession("");
    }


    Session ^ Connection::CreateTransactionalSession(System::String ^ name)
    {
        System::Exception          ^ newException = nullptr;
        Session                    ^ newSession   = nullptr;

        try
        {
            // create native session
            ::qpid::messaging::Session sessionp = 
				connectionp->createTransactionalSession(QpidMarshal::ToNative(name));

            // create managed session
            newSession = gcnew Session(sessionp, this);
        } 
        catch (const ::qpid::types::Exception & error) 
        {
            String ^ errmsg = gcnew String(error.what());
            newException    = gcnew QpidException(errmsg);
        }
        finally
        {
            // Clean up and throw on caught exceptions
            if (newException != nullptr)
            {
                if (newSession != nullptr)
				{
					delete newSession;
				}
            }
        }

        if (newException != nullptr)
        {
            throw newException;
        }

		return newSession;
    }


    //
    // CreateSession()
    //
    Session ^ Connection::CreateSession()
    {
        return CreateSession("");
    }


    Session ^ Connection::CreateSession(System::String ^ name)
    {
        System::Exception          ^ newException = nullptr;
        Session                    ^ newSession   = nullptr;

        try
        {
            // create native session
            ::qpid::messaging::Session sessionp = 
				connectionp->createSession(QpidMarshal::ToNative(name));

            // create managed session
            newSession = gcnew Session(sessionp, this);
        } 
        catch (const ::qpid::types::Exception & error) 
        {
            String ^ errmsg = gcnew String(error.what());
            newException    = gcnew QpidException(errmsg);
        }
        finally
        {
            // Clean up and throw on caught exceptions
            if (newException != nullptr)
            {
				if (newSession != nullptr)
				{
					delete newSession;
				}
            }
        }

		if (nullptr != newException) 
		{
			throw newException;
		}

        return newSession;
    }


    Session ^ Connection::GetSession(System::String ^ name)
    {
        System::Exception          ^ newException = nullptr;
        Session                    ^ newSession   = nullptr;
      
        try
        {
            const std::string n = QpidMarshal::ToNative(name);

            ::qpid::messaging::Session sess = 
				connectionp->::qpid::messaging::Connection::getSession(n);
            
            newSession = gcnew Session(sess, this);
        }
        catch (const ::qpid::types::Exception & error) 
        {
            String ^ errmsg = gcnew String(error.what());
            newException    = gcnew QpidException(errmsg);
        }
        finally
        {
            // Clean up and throw on caught exceptions
            if (newException != nullptr)
            {
				if (newSession != nullptr)
				{
					delete newSession;
				}
            }
        }

		if (nullptr != newException) 
		{
			throw newException;
		}

        return newSession;
    }
}}}}
