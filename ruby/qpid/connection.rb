#
# Copyright (c) 2006 The Apache Software Foundation
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

require "socket"
require "qpid/codec"

include Codec

module Qpid

  class Connection

    def initialize(host, port, spec)
      @host = host
      @port = port
      @spec = spec
    end

    attr_reader(:host, :port, :spec)

    def connect()
      @sock = TCPSocket.open(@host, @port)
      @out = Encoder.new(@sock)
      @in = Decoder.new(@sock)
    end

    def init()
      @out.write("AMQP")
      [1, 1, @spec.major, @spec.minor].each {|o|
        @out.octet(o)
      }
    end

    def write(frame)
      @out.octet(@spec.constants[frame.payload.type].id)
      @out.short(frame.channel)
      frame.payload.encode(@out)
      @out.octet(frame_end)
    end

    def read()
      type = @spec.constants[@in.octet()].name
      channel = @in.short()
      payload = Payload.decode(type, @spec, @in)
      oct = @in.octet()
      if oct != frame_end
        raise Exception.new("framing error: expected #{frame_end}, got #{oct}")
      end
      Frame.new(channel, payload)
    end

    private

    def frame_end
      @spec.constants[:"frame end"].id
    end

  end

  class Frame

    def initialize(channel, payload)
      @channel = channel
      @payload = payload
    end

    attr_reader(:channel, :payload)

  end

  class Payload

    TYPES = {}

    def Payload.singleton_method_added(name)
      if name == :type
        TYPES[type] = self
      end
    end

    def Payload.decode(type, spec, dec)
      klass = TYPES[type]
      klass.decode(spec, dec)
    end

  end

  class Method < Payload

    def initialize(method, args)
      if args.size != method.fields.size
        raise ArgumentError.new("argument mismatch #{method} #{args}")
      end
      @method = method
      @args = args
    end

    attr_reader(:method, :args)

    def Method.type
      :"frame method"
    end

    def type; Method.type end

    def encode(encoder)
      buf = StringWriter.new()
      enc = Encoder.new(buf)
      enc.short(@method.parent.id)
      enc.short(@method.id)
      @method.fields.zip(self.args).each {|f, a|
        enc.encode(f.type, a)
      }
      enc.flush()
      encoder.longstr(buf.to_s)
    end

    def Method.decode(spec, decoder)
      buf = decoder.longstr()
      dec = Decoder.new(StringReader.new(buf))
      klass = spec.classes[dec.short()]
      meth = klass.methods[dec.short()]
      args = meth.fields.map {|f| dec.decode(f.type)}
      return Method.new(meth, args)
    end

  end

end
