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

require 'spec_helper'

module Qpid

  module Messaging

    describe Duration do

      before(:each) do
        @duration = Qpid::Messaging::Duration::SECOND
      end

      it "returns the underlying implementation" do
        impl = @duration.duration_impl

        impl.should_not be_nil
      end

      it "can create a duration with a millisecond value" do
        duration = Qpid::Messaging::Duration.new 500

        milliseconds = duration.milliseconds

        milliseconds.should == 500
      end

      it "returns the time in milliseconds" do
        milliseconds = @duration.milliseconds

        milliseconds.should == 1000
      end

      it "raises an error when multiplied by a negative" do
        expect {
          twomin = Qpid::Messaging::Duration::MINUTE * -2
        }.to raise_error
      end

      it "returns IMMEDIATE if the factor is zero" do
        result = Qpid::Messaging::Duration::MINUTE * 0
        result.should be(Qpid::Messaging::Duration::IMMEDIATE)
      end

      it "fractional factors return a reduced duration" do
        factor = rand(1)
        first = Qpid::Messaging::Duration::MINUTE
        second = first * factor

        second.milliseconds.should == ((first.milliseconds * factor).floor)
      end

      it "can return a multiple of its duration" do
        factor = rand(10).floor
        first = Qpid::Messaging::Duration.new(rand(10).floor * 10000)
        second = first * factor

        second.milliseconds.should == first.milliseconds * factor
      end

    end

  end

end
