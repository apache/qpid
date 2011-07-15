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

require 'qpid/errors'
require 'qpid/duration'
require 'qpid/session'

class TestSession < Test::Unit::TestCase

  def setup
    @session_impl = flexmock("session_impl")
    @other_session = flexmock("other_session")
    @other_session_impl = flexmock("other_session_impl")
    @sender = flexmock("sender")

    @Connection_class = flexmock(Qpid::Messaging::Connection)
    @connection_impl = flexmock("connection_impl")
    @connection = flexmock("connection")

    @Receiver_class = flexmock(Qpid::Messaging::Receiver)
    @receiver = flexmock("receiver")
    @receiver_impl = flexmock("receiver_impl")

    @address = flexmock("address")
    @address_impl = flexmock("address_impl")

    @Sender_class = flexmock(Qpid::Messaging::Sender)
    @sender = flexmock("sender")
    @sender_impl = flexmock("sender_impl")

    @message = flexmock("message")
    @message_impl = flexmock("message_impl")

    @duration = flexmock("duration")
    @duration_impl = flexmock("duration_impl")

    @session = Qpid::Messaging::Session.new(@session_impl)
  end

  def test_create_sender_with_Address
    @address.
      should_receive(:class).
      once.
      and_return(Qpid::Messaging::Address).
      should_receive(:address_impl).
      once.
      and_return(@address_impl)
    @session_impl.
      should_receive(:createSender).
      once.
      with(@address_impl).
      and_return(@sender_impl)

    result = @session.create_sender @address

    assert_not_nil result
  end

  def test_create_sender
    @session_impl.
      should_receive(:createSender).
      once.
      with_any_args.
      and_return(@sender_impl)

    result = @session.create_sender("my-queue")

    assert_not_nil result
  end

  def test_create_sender_with_address_string
    @session_impl.
      should_receive(:createSender).
      once.
      with("my-queue;{create:always}").
      and_return(@sender_impl)

    result = @session.create_sender "my-queue;{create:always}"

    assert_same @sender_impl, result.sender_impl
  end

  def test_create_receiver
    @address.
      should_receive(:class).
      once.
      and_return(Qpid::Messaging::Address).
      should_receive(:address_impl).
      once.
      and_return(@address_impl)
    @session_impl.
      should_receive(:createReceiver).
      once.
      with(@address_impl).
      and_return(@receiver_impl)

    result = @session.create_receiver(@address)

    assert_equal @receiver_impl, result.receiver_impl
  end

  def test_create_receiver_with_address_string
    @session_impl.
      should_receive(:createReceiver).
      once.
      with("my-queue").
      and_return(@receiver_impl)

    result = @session.create_receiver("my-queue")

    assert_same @receiver_impl, result.receiver_impl
  end

  def test_close
    @session_impl.
      should_receive(:close).
      once

    @session.close
  end

  def test_commit
    @session_impl.
      should_receive(:commit).
      once

    @session.commit
  end

  def test_rollback
    @session_impl.
      should_receive(:rollback).
      once

    @session.rollback
  end

  def test_acknowledge_with_no_args
    @session_impl.
      should_receive(:acknowledge).
      once.
      with(false)

    @session.acknowledge
  end

  def test_acknowledge_and_sync
    @session_impl.
      should_receive(:acknowledge).
      once.
      with(true)

    @session.acknowledge :sync => true
  end

  def test_acknowledge_and_dont_sync
    @session_impl.
      should_receive(:acknowledge).
      once.
      with(false)

    @session.acknowledge :sync => false
  end

  def test_acknowledge_message_without_sync
    @message.
      should_receive(:message_impl).
      once.
      and_return(@message_impl)
    @session_impl.
      should_receive(:acknowledge).
      once.
      with(@message_impl, false)

    @session.acknowledge :message => @message
  end

  def test_acknowledge_message_and_sync
    @message.
      should_receive(:message_impl).
      once.
      and_return(@message_impl)
    @session_impl.
      should_receive(:acknowledge).
      once.
      with(@message_impl, true)

    @session.acknowledge :message => @message, :sync => true
  end

  def test_acknowledge_message_and_dont_sync
    @message.
      should_receive(:message_impl).
      once.
      and_return(@message_impl)
    @session_impl.
      should_receive(:acknowledge).
      once.
      with(@message_impl, false)

    @session.acknowledge :message => @message, :sync => false
  end

  def test_reject_message
    @message.
      should_receive(:message_impl).
      once.
      and_return(@message_impl)
    @session_impl.
      should_receive(:reject).
      once.
      with(@message_impl)

    @session.reject @message
  end

  def test_release_message
    @message.
      should_receive(:message_impl).
      once.
      and_return(@message_impl)
    @session_impl.
      should_receive(:release).
      once.
      with(@message_impl)

    @session.release @message
  end

  def test_sync_without_block
    @session_impl.
      should_receive(:sync).
      once

    @session.sync
  end

  def test_sync_and_block
    @session_impl.
      should_receive(:sync).
      once.
      with(true)

    @session.sync :block => true
  end

  def test_sync_and_dont_block
    @session_impl.
      should_receive(:sync).
      once.
      with(false)

    @session.sync :block => false
  end

  def test_receivable
    @session_impl.
      should_receive(:getReceivable).
      once.
      and_return(5)

    assert_equal 5, @session.receivable
  end

  def test_unsettled_acks
    @session_impl.
      should_receive(:getUnsettledAcks).
      once.
      and_return(17)

    assert_equal 17, @session.unsettled_acks
  end

  def test_next_receiver_with_no_duration
    @session_impl.
      should_receive(:nextReceiver).
      once.
      with(Qpid::Messaging::Duration::FOREVER.duration_impl).
      and_return(@receiver_impl)

    result = @session.next_receiver

    assert_same @receiver_impl, result.receiver_impl
  end

  def test_next_receiver_with_duration
    @duration.
      should_receive(:duration_impl).
      once.
      and_return(@duration_impl)
    @session_impl.
      should_receive(:nextReceiver).
      once.
      with(@duration_impl).
      and_return(@receiver_impl)

    result = @session.next_receiver @duration

    assert_same @receiver_impl, result.receiver_impl
  end

  def test_sender
    @session_impl.
      should_receive(:getSender).
      once.
      with("farkle").
      and_return(@sender_impl)
    @Sender_class.
      should_receive(:for_impl).
      once.
      with(@sender_impl).
      and_return(@sender)

    result = @session.sender "farkle"

    assert_same @sender, result
  end

  def test_sender_with_invalid_name
    @session_impl.
      should_receive(:getSender).
      once.
      with("farkle").
      and_throw(RuntimeError)

    assert_raise(Qpid::Messaging::KeyError) {@session.sender "farkle"}
  end

  def test_receiver
    @session_impl.
      should_receive(:getReceiver).
      once.
      with("farkle").
      and_return(@receiver_impl)
    @Receiver_class.
      should_receive(:for_impl).
      once.
      with(@receiver_impl).
      and_return(@receiver)

    result = @session.receiver "farkle"

    assert_same @receiver, result
  end

  def test_receiver_with_invalid_name
    @session_impl.
      should_receive(:getReceiver).
      once.
      with("farkle").
      and_throw(RuntimeError)

    assert_raise(Qpid::Messaging::KeyError) {@session.receiver "farkle"}
  end

  def test_connection
    @session_impl.
      should_receive(:getConnection).
      once.
      and_return(@connection_impl)

    result = @session.connection

    assert_same @connection_impl, result.connection_impl
  end

  def test_error_with_none
    @session_impl.
      should_receive(:hasError).
      once.
      and_return(false)

    assert !@session.error?
  end

  def test_error
    @session_impl.
      should_receive(:hasError).
      once.
      and_return(true)

    assert @session.error?
  end

  def test_check_error
    @session_impl.
      should_receive(:checkError).
      once

    @session.check_error
  end

  def test_is_valid
    @session_impl.
      should_receive(:isValid).
      once.
      and_return(false)

    assert !@session.valid?
  end

  def test_is_null
    @session_impl.
      should_receive(:isNull).
      once.
      and_return(false)

    assert !@session.null?
  end

  def test_swap
    @other_session.
      should_receive(:session_impl).
      once.
      and_return(@other_session_impl)
    @session_impl.
      should_receive(:swap).
      once.
      with(@other_session_impl)

    @session.swap @other_session
  end

end
