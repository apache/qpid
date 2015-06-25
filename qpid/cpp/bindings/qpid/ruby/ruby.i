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

%module cqpid
/* Ruby doesn't have a != operator*/
#pragma SWIG nowarn=378
%include "std_string.i"
%include "qpid/swig_ruby_typemaps.i"

/* Define the general-purpose exception handling */
%exception {

  static VALUE eMessagingError = rb_define_class("MessagingError",
                                                 rb_eStandardError);

    try {
        $action
    }
    catch(qpid::messaging::ConnectionError& error) {
      static VALUE merror = rb_define_class("ConnectionError", eMessagingError);
      rb_raise(merror, "%s", error.what());
    }
    catch(qpid::messaging::TransportFailure& error) {
      static VALUE merror = rb_define_class("TransportFailure", eMessagingError);
      rb_raise(merror, "%s", error.what());
    }
    catch(qpid::messaging::TransactionAborted& error) {
      static VALUE merror = rb_define_class("TransactionAborted", eMessagingError);
      rb_raise(merror, "%s", error.what());
    }
    catch(qpid::messaging::TransactionUnknown& error) {
      static VALUE merror = rb_define_class("TransactionUnknown", eMessagingError);
      rb_raise(merror, "%s", error.what());
    }
    catch(qpid::messaging::TransactionError& error) {
      static VALUE merror = rb_define_class("TransactionError", eMessagingError);
      rb_raise(merror, "%s", error.what());
    }
    catch(qpid::messaging::UnauthorizedAccess& error) {
      static VALUE merror = rb_define_class("UnauthorizedAccess", eMessagingError);
      rb_raise(merror, "%s", error.what());
    }
    catch(qpid::messaging::SessionError& error) {
      static VALUE merror = rb_define_class("SessionError", eMessagingError);
      rb_raise(merror, "%s", error.what());
    }
    catch(qpid::messaging::TargetCapacityExceeded& error) {
      static VALUE merror = rb_define_class("TargetCapacityExceeded", eMessagingError);
      rb_raise(merror, "%s", error.what());
    }
    catch(qpid::messaging::SendError& error) {
      static VALUE merror = rb_define_class("SendError", eMessagingError);
      rb_raise(merror, "%s", error.what());
    }
    catch(qpid::messaging::SenderError& error) {
      static VALUE merror = rb_define_class("SenderError", eMessagingError);
      rb_raise(merror, "%s", error.what());
    }
    catch(qpid::messaging::NoMessageAvailable& error) {
      static VALUE merror = rb_define_class("NoMessageAvailable", eMessagingError);
      rb_raise(merror, "%s", error.what());
    }
    catch(qpid::messaging::FetchError& error) {
      static VALUE merror = rb_define_class("FetchError", eMessagingError);
      rb_raise(merror, "%s", error.what());
    }
    catch(qpid::messaging::ReceiverError& error) {
      static VALUE merror = rb_define_class("ReceiverError", eMessagingError);
      rb_raise(merror, "%s", error.what());
    }
    catch(qpid::messaging::InvalidOptionString& error) {
      static VALUE merror = rb_define_class("InvalidOptionString", eMessagingError);
      rb_raise(merror, "%s", error.what());
    }
    catch(qpid::messaging::KeyError& error) {
      static VALUE merror = rb_define_class("KeyError", eMessagingError);
      rb_raise(merror, "%s", error.what());
    }
    catch(qpid::messaging::AssertionFailed& error) {
      static VALUE merror = rb_define_class("AssertionFailed", eMessagingError);
      rb_raise(merror, "%s", error.what());
    }
    catch(qpid::messaging::NotFound& error) {
      static VALUE merror = rb_define_class("NotFound", eMessagingError);
      rb_raise(merror, "%s", error.what());
    }
    catch(qpid::messaging::ResolutionError& error) {
      static VALUE merror = rb_define_class("ResolutionError", eMessagingError);
      rb_raise(merror, "%s", error.what());
    }
    catch(qpid::messaging::MalformedAddress& error) {
      static VALUE merror = rb_define_class("MalformedAddress", eMessagingError);
      rb_raise(merror, "%s", error.what());
    }
    catch(qpid::messaging::AddressError& error) {
      static VALUE merror = rb_define_class("AddressError", eMessagingError);
      rb_raise(merror, "%s", error.what());
    }
    catch(qpid::messaging::LinkError& error) {
      static VALUE merror = rb_define_class("LinkError", eMessagingError);
      rb_raise(merror, "%s", error.what());
    }
    catch(qpid::messaging::MessagingException& error) {
        rb_raise(eMessagingError, "%s", error.what());
    }
}

%include "qpid/qpid.i"

