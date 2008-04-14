#!/usr/bin/env ruby
$: << ".."                      # Include .. in load path
require 'cppgen'

class MethodBodyDefaultVisitorGen < CppGen
  
  def initialize(outdir, amqp)
    super(outdir, amqp)
    @namespace, @classname, @filename = parse_classname("qpid::framing::MethodBodyDefaultVisitor")
  end

  def generate()
    h_file(@filename) {
      include "qpid/framing/MethodBodyConstVisitor"
      namespace(@namespace) { 
        genl "class AMQMethodBody;"
        cpp_class(@classname, "public MethodBodyConstVisitor") {
          genl "public:"
          genl "virtual void defaultVisit(const AMQMethodBody&) = 0;"
          @amqp.methods_.each { |m|
            genl "virtual void visit(const #{m.body_name}&);" }
        }}}

    cpp_file(@filename) {
      include(@filename)
      include("all_method_bodies.h")
      namespace(@namespace) {
        @amqp.methods_.each { |m|
          genl "void #{@classname}::visit(const #{m.body_name}& b) { defaultVisit(b); }"
        }}}
  end
end

MethodBodyDefaultVisitorGen.new($outdir, $amqp).generate();

