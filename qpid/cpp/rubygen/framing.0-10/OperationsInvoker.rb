#!/usr/bin/env ruby
# Usage: output_directory xml_spec_file [xml_spec_file...]
# 
$: << '..'
require 'cppgen'

class OperationsInvokerGen < CppGen
  def initialize(chassis, outdir, amqp)
    super(outdir, amqp)
    @chassis=chassis
    @ops="AMQP_#{@chassis.caps}Operations"
    @classname="#{@ops}::Invoker"
    @filename="qpid/framing/#{@chassis.caps}Invoker"
  end

  def handler(c) "#{@ops}::#{c.cppname}Handler"; end
  def getter(c) "get#{c.cppname}Handler"; end
  def invoker(c) "#{handler(c)}::Invoker"; end
  def visit_methods(c) c.methods_on(@chassis).select { |m| !m.content } end
  
  def handler_visits_cpp(c)
    visit_methods(c).each { |m|
      scope("void #{invoker(c)}::visit(const #{m.body_name}& body) {") {
        if (m.result)
          genl "this->encode(body.invoke(target), result.result);"
        else
          genl "body.invoke(target);"
        end
        genl "result.handled=true;"
      }
    }
  end

  def ops_visits_cpp()
    @amqp.classes.each { |c|
      visit_methods(c).each { |m|
        scope("void #{@classname}::visit(const #{m.body_name}& body) {") {
        genl "#{handler(c)}::Invoker invoker(*target.#{getter(c)}());"
        genl "body.accept(invoker);"
        genl "result=invoker.getResult();"
        }
      }
    }
  end

  def invoker_h(invoker, target, methods)
    return if methods.empty?
    genl
    cpp_class(invoker, "public qpid::framing::Invoker") {
      genl "#{target}& target;"
      public
      genl("Invoker(#{target}& target_) : target(target_) {}")
      genl "using MethodBodyDefaultVisitor::visit;"
      methods.each { |m| genl "void visit(const #{m.body_name}& body);" }
    }
  end
  
  def generate()
    h_file(@filename) {
      include "qpid/framing/#{@ops}"
      include "qpid/framing/Invoker.h"
      namespace("qpid::framing") {
        # AMQP_*Operations invoker.
        methods=@amqp.classes.map { |c| visit_methods(c).to_a }.flatten
        invoker_h(@classname, @ops, methods) 

        # AMQP_*Operations::*Handler invokers.
        @amqp.classes.each { |c|
          invoker_h(invoker(c), handler(c), visit_methods(c))
        }
      }
    }

    cpp_file(@filename) {
      include @filename
      @amqp.classes.each { |c|
        visit_methods(c).each { |m|
          include "qpid/framing/#{m.body_name}"
        }}
      namespace("qpid::framing") {
        ops_visits_cpp
        @amqp.classes.each { |c|
          next if visit_methods(c).empty?
          handler_visits_cpp(c)
        }
      }
    }
  end
end

OperationsInvokerGen.new("client",ARGV[0], $amqp).generate()
OperationsInvokerGen.new("server",ARGV[0], $amqp).generate()
