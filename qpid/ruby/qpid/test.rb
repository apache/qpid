require "qpid/spec"
require "qpid/client"

module Qpid

  module Test

    def connect()
      spec = Spec.load("../specs/amqp.0-8.xml")
      c = Client.new("0.0.0.0", 5672, spec)
      c.start({"LOGIN" => "guest", "PASSWORD" => "guest"})
      return c
    end

  end

end
