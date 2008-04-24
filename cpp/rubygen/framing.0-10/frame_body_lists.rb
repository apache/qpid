$: << ".."                      # Include .. in load path
require 'cppgen'

class FrameBodyListsGen < CppGen
    
  def initialize(outdir, amqp) 
    super(outdir, amqp); 
  end

  def generate
    h_file("qpid/framing/frame_body_lists.h") {
      gen <<EOS
/**@file
 * Macro lists of frame body classes, used to generate Visitors
 */
EOS
      gen "#define METHOD_BODIES() "
      @amqp.methods_.each { |m| gen "\\\n    (#{m.body_name}) " }
      gen <<EOS


#define OTHER_BODIES() (AMQContentBody)(AMQHeaderBody)(AMQHeartbeatBody))

EOS
    }
  end
end

FrameBodyListsGen.new(ARGV[0], $amqp).generate;

    
