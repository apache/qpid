#!/usr/bin/env ruby
$: << ".."                      # Include .. in load path
require 'cppgen'

class MethodBodyDefaultVisitorGen < CppGen
  
  def initialize(outdir, amqp)
    super(outdir, amqp)
    set_classname("qpid::framing::MethodBodyDefaultVisitor")
  end

  def generate()
    h_file(@filename) {
      include "qpid/framing/MethodBodyConstVisitor"
      namespace(@namespace) { 
        genl
        cpp_class(@classname, "public MethodBodyConstVisitor") {
          genl "public:"
          genl "virtual void defaultVisit() = 0;"
          @amqp.methods_.each { |m|
            genl "virtual void visit(const #{m.body_name}&);" }
        }}}

    cpp_file(@filename) {
      include(@filename)
      namespace(@namespace) {
        @amqp.methods_.each { |m|
          genl "void #{@classname}::visit(const #{m.body_name}&) { defaultVisit(); }"
        }}}
  end
end

MethodBodyDefaultVisitorGen.new($outdir, $amqp).generate();

