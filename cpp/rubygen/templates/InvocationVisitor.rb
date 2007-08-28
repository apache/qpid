#!/usr/bin/env ruby
$: << ".."                      # Include .. in load path
require 'cppgen'

class InvocationVisitor < CppGen
  
  def initialize(outdir, amqp)
    super(outdir, amqp)
    @namespace="qpid::framing"
    @classname="InvocationVisitor"
    @filename="qpid/framing/InvocationVisitor"
  end

  def invocation_args(m)
    m.param_names.collect {|p| "body.get" + p.caps + "()" }.join(",\n")
  end

  def null_visit(m)
    genl "void InvocationVisitor::visit(const #{m.body_name}&){}"
  end

  def define_visit(m)
    if (m.fields.size > 0)
      body = "body"
    else
      body = "/*body*/"
    end
    gen <<EOS
void InvocationVisitor::visit(const #{m.body_name}& #{body})
{
    AMQP_ServerOperations::#{m.parent.cppname}Handler* ptr(0); 
    if (invocable) {
        ptr = dynamic_cast<AMQP_ServerOperations::#{m.parent.cppname}Handler*>(invocable);
    } else {
        ptr = ops->get#{m.parent.cppname}Handler();
    }

    if (ptr) {
EOS
    if (m.result)      
      indent(2) { genl "encode<#{m.result.struct.cpptype.name}>(ptr->#{m.cppname}(#{invocation_args(m)}), result);" }
    else
      indent(2) { genl "ptr->#{m.cppname}(#{invocation_args(m)});" }
    end

    gen <<EOS
        succeeded = true;
    } else {
        succeeded = false;
    }
}
EOS
  end

  def generate()
    h_file("#{@filename}") {
      include "AMQP_ServerOperations.h"
      include "MethodBodyConstVisitor.h"
      include "qpid/framing/StructHelper.h"
      namespace(@namespace) { 
        cpp_class("InvocationVisitor", ["public MethodBodyConstVisitor", "private StructHelper"]) {
          indent { 
            genl "AMQP_ServerOperations* const ops;" 
            genl "Invocable* const invocable;" 
            genl "std::string result;" 
            genl "bool succeeded;" 
          }
          genl "public:"
          indent { 
            genl "InvocationVisitor(AMQP_ServerOperations* _ops) : ops(_ops), invocable(0), succeeded(false) {}" 
            genl "InvocationVisitor(Invocable* _invocable) : ops(0), invocable(_invocable), succeeded(false) {}" 
            genl "const std::string& getResult() const { return result; }"
            genl "const bool hasResult() const { return !result.empty(); }"
            genl "bool wasHandled() const { return succeeded; }"
            genl "void clear();"
            genl "virtual ~InvocationVisitor() {}" 
          }
          @amqp.methods_.each { |m| genl "void visit(const #{m.body_name}&);" }
        }
      }
    }

    cpp_file("#{@filename}") {
      include "InvocationVisitor.h"
      @amqp.methods_.each { |m| include m.body_name }
      namespace(@namespace) { 
        genl "void InvocationVisitor::clear() {"
        indent { 
          genl "succeeded = false;"
          genl "result.clear();"
        }
        genl "}"
        genl
        @amqp.methods_.each { |m| m.on_server? && !m.content() ? define_visit(m) : null_visit(m) }
      }
    }
  end
end

InvocationVisitor.new(Outdir, Amqp).generate();

