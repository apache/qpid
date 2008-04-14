#!/usr/bin/env ruby
$: << ".."                      # Include .. in load path
require 'cppgen'

class GenHandlers < CppGen
  def initialize(outdir, amqp)
    super(outdir, amqp)
    @ns="qpid::amqp_#{@amqp.version.bars}"
    @dir="qpid/amqp_#{@amqp.version.bars}"
  end

  def action_handler(type, actions)
    genl
    bases=actions.map { |a| "public #{a.fqclassname}::Handler" }
    struct("#{type}Handler", *bases) { }
  end

  def generate()
    h_file("#{@dir}/handlers.h") {
      include "specification"
      namespace("#{@ns}") { 
        action_handler "Command", @amqp.collect_all(AmqpCommand)
        action_handler "Control", @amqp.collect_all(AmqpControl)
      }
    }
  end
end

GenHandlers.new($outdir, $amqp).generate()
