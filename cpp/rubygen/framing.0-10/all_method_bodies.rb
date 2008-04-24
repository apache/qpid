#!/usr/bin/env ruby
$: << ".."                      # Include .. in load path
require 'cppgen'

class AllMethodBodiesGen < CppGen
  
  def initialize(outdir, amqp)
    super(outdir, amqp)
    @namespace="qpid::framing"
    @filename="qpid/framing/all_method_bodies"
  end

  def generate()
    h_file(@filename) {
      @amqp.methods_.each { |m| include "qpid/framing/"+m.body_name }
    }
  end
end

AllMethodBodiesGen.new($outdir, $amqp).generate();

