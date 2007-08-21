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
      if (m.amqp_parent.name == "message" && (m.name == "transfer" || m.name == "append"))
        "body"
      else
        m.param_names.collect {|p| "body.get" + p.caps + "()" }.join(",\n")
      end
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
    AMQP_ServerOperations::#{m.amqp_parent.cppname}Handler* ptr(0); 
    if (invocable) {
        ptr = dynamic_cast<AMQP_ServerOperations::#{m.amqp_parent.cppname}Handler*>(invocable);
    } else {
        ptr = ops->get#{m.amqp_parent.cppname}Handler();
    }

    if (ptr) {
EOS
    if (m.has_result?)      
      indent(2) { genl "encode<#{m.result_struct.cppname.caps}>(ptr->#{m.cppname}(#{invocation_args(m)}), result);" }
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
            genl "InvocationVisitor(AMQP_ServerOperations* _ops) : ops(_ops), invocable(0) {}" 
            genl "InvocationVisitor(Invocable* _invocable) : ops(0), invocable(_invocable) {}" 
            genl "const std::string& getResult() const { return result; }"
            genl "const bool hasResult() const { return !result.empty(); }"
            genl "bool wasHandled() const { return succeeded; }"
            genl "void clear();"
            genl "virtual ~InvocationVisitor() {}" 
          }
          @amqp.amqp_methods.each { |m| genl "void visit(const #{m.body_name}&);" }
        }
      }
    }

    cpp_file("#{@filename}") {
      include "InvocationVisitor.h"
      @amqp.amqp_methods.each { |m| include m.body_name }
      namespace(@namespace) { 
        genl "void InvocationVisitor::clear() {"
        indent { 
          genl "succeeded = false;"
          genl "result.clear();"
        }
        genl "}"
        genl
        @amqp.amqp_methods.each { |m| m.is_server_method? ? define_visit(m) : null_visit(m) }
      }
    }
  end
end

InvocationVisitor.new(Outdir, Amqp).generate();

