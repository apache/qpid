#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#

$:.unshift File.join(File.dirname(__FILE__), "..", "lib")

require 'test/unit'
require 'flexmock/test_unit'

require 'cqpid'
require 'qpid/connection'

class TestConnection < Test::Unit::TestCase

  def setup
    @connection_impl       = flexmock("connection_impl")
    @other_connection      = flexmock("other_connection")
    @other_connection_impl = flexmock("other_connection_impl")
    @cqpid_connection      = flexmock(Cqpid::Connection)

    @session      = flexmock("session")
    @session_name = "test-session"

    @url     = "localhost"
    @options = {}

    @connection = Qpid::Messaging::Connection.new(@url, @options, @connection_impl)
  end

  def test_create_with_username_and_password
    @cqpid_connection.
      should_receive(:new).
      once.with("localhost",
                {"username" => "username",
                  "password" => "password"}).
      and_return(@connection_impl)
    @connection_impl.
      should_receive(:open).
      once

    result = Qpid::Messaging::Connection.new("localhost",
                                             :username => "username",
                                             :password => "password")
    result.open

    assert_same @connection_impl, result.connection_impl
  end

  def test_create_with_hostname
    result = Qpid::Messaging::Connection.new("localhost")

    assert_not_nil result
  end

  def test_open
    @cqpid_connection.
      should_receive(:new).
      once.
      with(@url, {}).
      and_return(@connection_impl)
    @connection_impl.
      should_receive(:open).
      once

    @connection.open

    assert_same @connection_impl, @connection.connection_impl
  end

  def test_check_open_when_open
    @connection_impl.
      should_receive(:isOpen).
      once.
      and_return(true)

    assert @connection.open?
  end

  def test_check_open_before_connection
    result = Qpid::Messaging::Connection.new("hostname")

    assert !result.open?
  end

  def test_check_open_when_closed
    @connection_impl.
      should_receive(:isOpen).
      once.
      and_return(false)

    assert !@connection.open?
  end

  def test_close_an_unopened_session
    @connection_impl.
      should_receive(:isOpen).
      once.
      and_return(false)

    @connection.close
  end

  def test_close
    @connection_impl.
      should_receive(:isOpen).
      once.
      and_return(true).
      should_receive(:close).
      once

    @connection.close
  end

  def test_create_session_without_name
    @connection_impl.
      should_receive(:isOpen).
      once.
      and_return(true).
      should_receive(:createSession).
      once.
      with("").
      and_return(@session)

    result = @connection.create_session

    assert_not_nil result
    assert_same @session, result.session_impl
  end

  def test_create_session
    @connection_impl.
      should_receive(:isOpen).
      once.
      and_return(true).
      should_receive(:createSession).
      once.
      with(@session_name).
      and_return(@session)

    result = @connection.create_session :name => @session_name

    assert_not_nil result
    assert_same @session, result.session_impl
  end

  def test_create_session_raises_exception_when_closed
    @connection_impl.
      should_receive(:isOpen).
      once.
      and_return(false)

    assert_raise(RuntimeError) {@connection.create_session @session_name}
  end

  def test_create_transactional_session
    @connection_impl.
      should_receive(:isOpen).
      once.
      and_return(true).
      should_receive(:createTransactionalSession).
      once.
      with("").
      and_return(@session)

    result = @connection.create_session :transactional => true

    assert_not_nil result
    assert_same @session, result.session_impl
  end

  def test_authenticated_username_when_not_connected
    @connection_impl.
      should_receive(:isOpen).
      once.
      and_return(false)

    result = @connection.authenticated_username

    assert_nil result
  end

  def test_authenticated_username
    @connection_impl.
      should_receive(:isOpen).
      once.
      and_return(true).
      should_receive(:getAuthenticatedUsername).
      once.
      and_return("farkle")

    result = @connection.authenticated_username

    assert_equal "farkle", result
  end

  def test_get_session_with_invalid_name
    @connection_impl.
      should_receive(:getSession).
      once.
      with(@session_name).
      and_return(nil)

    result = @connection.session @session_name

    assert_nil result
  end

  # APIs inherited from Handle

  def test_is_valid
    @connection_impl.
      should_receive(:isValid).
      once.
      and_return(true)

    assert @connection.valid?
  end

  def test_is_null
    @connection_impl.
      should_receive(:isNull).
      once.
      and_return(false)

    assert !@connection.null?
  end

  def test_swap
    @other_connection.
      should_receive(:connection_impl).
      once.
      and_return(@other_connection_impl)
    @connection_impl.
      should_receive(:swap).
      once.
      with(@other_connection_impl)

    @connection.swap @other_connection
  end

end

