#!/usr/bin/env ruby
$: << ".."                      # Include .. in load path
require 'cppgen'

class GenAllSegmentTypes < CppGen
  def initialize(outdir, amqp)
    super(outdir, amqp)
  end

  def generate
    h_file("tests/allSegmentTypes.h") { 
      include "qpid/amqp_0_10/specification.h"
      include "qpid/amqp_0_10/Header.h"
      include "qpid/amqp_0_10/Body.h"
      genl
      genl "using namespace qpid::amqp_0_10;"
      genl
      scope("template <class Op> size_t allSegmentTypes(Op& op) {"){
        genl "op(Header());"
        genl "op(Body());"
        n = 2;
        @amqp.classes.each { |c|
          c.commands.each { |s| genl "op(CommandHolder(#{c.nsname}::#{s.classname}()));" }
          c.controls.each { |s| genl "op(ControlHolder(#{c.nsname}::#{s.classname}()));" }
          n += 2
        }
        genl "return #{n};"
      }
    }
  end
end

GenAllSegmentTypes.new($outdir, $amqp).generate();

