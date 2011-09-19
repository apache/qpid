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

require 'qpid/receiver'

class TestReceiver < Test::Unit::TestCase

  def setup
    @session_impl = flexmock("session")

    @Message_class = flexmock(Qpid::Messaging::Message)
    @Messaging_module = flexmock(Qpid::Messaging)
    @message_impl = flexmock("message_impl")
    @message = flexmock("message")

    @receiver_impl       = flexmock("receiver")
    @other_receiver      = flexmock("other_receiver")
    @other_receiver_impl = flexmock("other_receiver_impl")
    @receiver            = Qpid::Messaging::Receiver.new @receiver_impl
  end

  def test_receiver_impl
    assert_same @receiver_impl, @receiver.receiver_impl
  end

  def test_get
    @receiver_impl.
      should_receive(:get).
      once.
      with_any_args.
      and_return(@message_impl)

    result = @receiver.get

    assert_not_nil result
    assert_same @message_impl, result.message_impl
  end

  def test_get_with_duration
    @receiver_impl.
      should_receive(:get).
      once.
      with_any_args.
      and_return(@message_impl)

    result = @receiver.get Qpid::Messaging::Duration::MINUTE

    assert_not_nil result
    assert_same @message_impl, result.message_impl
  end

  def test_get_with_no_message_received
    @receiver_impl.
      should_receive(:get).
      once.
      with_any_args.
      and_return(nil)

    result = @receiver.get Qpid::Messaging::Duration::SECOND

    assert_nil result
  end

  def test_fetch
   @receiver_impl.
      should_receive(:fetch).
      once.
      with_any_args.
      and_return(@message_impl)

    result = @receiver.fetch

    assert_not_nil result
    assert_same @message_impl, result.message_impl
  end

  def test_fetch_with_duration
    @receiver_impl.
      should_receive(:fetch).
      once.
      with_any_args.
      and_return(@message_impl)

    result = @receiver.fetch Qpid::Messaging::Duration::MINUTE

    assert_not_nil result
    assert_same @message_impl, result.message_impl
  end

  def test_fetch_with_no_message_received
    @receiver_impl.
      should_receive(:fetch).
      once.
      with_any_args.
      and_return(nil)

    result = @receiver.fetch Qpid::Messaging::Duration::SECOND

    assert_nil result
  end

  def test_set_capacity
    @receiver_impl.
      should_receive(:setCapacity).
      once.
      with(15)

    @receiver.capacity = 15
  end

  def test_get_capacity
    @receiver_impl.
      should_receive(:getCapacity).
      once.
      and_return(17)

    assert_equal 17, @receiver.capacity
  end

  def test_get_available
    @receiver_impl.
      should_receive(:getAvailable).
      once.
      and_return(2)

    assert_equal 2, @receiver.available
  end

  def test_get_unsettled
    @receiver_impl.
      should_receive(:getUnsettled).
      once.
      and_return(12)

    assert_equal 12, @receiver.unsettled
  end

  def test_close
    @receiver_impl.
      should_receive(:close).
      once

    @receiver.close
  end

  def test_closed_when_open
    @receiver_impl.
      should_receive(:isClosed).
      once.
      and_return(false)

    assert !@receiver.closed?
  end

  def test_closed
    @receiver_impl.
      should_receive(:isClosed).
      once.
      and_return(true)

    assert @receiver.closed?
  end

  def test_get_name
    @receiver_impl.
      should_receive(:getName).
      once.
      and_return("my-queue")

    assert_equal "my-queue", @receiver.name
  end

  def test_get_session
    @receiver_impl.
      should_receive(:getSession).
      once.
      and_return(@session_impl)

    result = @receiver.session

    assert_not_nil result
    assert_same @session_impl, result.session_impl
  end

  def test_is_valid
    @receiver_impl.
      should_receive(:isValid).
      once.
      and_return(false)

    assert !@receiver.valid?
  end

  def test_is_null
    @receiver_impl.
      should_receive(:isNull).
      once.
      and_return(true)

    assert @receiver.null?
  end

  def test_swap
    @other_receiver.
      should_receive(:receiver_impl).
      once.
      and_return(@other_receiver_impl)
    @receiver_impl.
      should_receive(:swap).
      once.
      with(@other_receiver_impl)

    @receiver.swap @other_receiver
  end

end

