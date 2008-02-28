#!/usr/bin/env ruby
$: << ".."                      # Include .. in load path
require 'cppgen'

class MethodBodyConstVisitorGen < CppGen
  
  def initialize(outdir, amqp)
    super(outdir, amqp)
    @namespace="qpid::framing"
    @classname="MethodBodyConstVisitor"
    @filename="qpid/framing/MethodBodyConstVisitor"
  end

  def generate()
    h_file("#{@filename}") {
      namespace(@namespace) { 
        @amqp.methods_.each { |m| genl "class #{m.body_name};" }
        cpp_class("MethodBodyConstVisitor") {
          genl "public:"
          genl "virtual ~MethodBodyConstVisitor() {}"
          @amqp.methods_.each { |m| genl "virtual void visit(const #{m.body_name}&) = 0;" }
        }}}
  end
end

MethodBodyConstVisitorGen.new($outdir, $amqp).generate();

