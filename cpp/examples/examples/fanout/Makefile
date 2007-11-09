CXX=g++
CXXFLAGS= 

PROGRAMS=fanout_config_queues listener fanout_publisher
all: $(PROGRAMS)

fanout_config_queues: fanout_config_queues.cpp
	$(CXX) $(CXXFLAGS) -lqpidclient -o $@ $^

listener: listener.cpp
	$(CXX) $(CXXFLAGS) -lqpidclient -o $@ $^

fanout_publisher: fanout_publisher.cpp
	$(CXX) $(CXXFLAGS) -lqpidclient -o $@ $^

clean:
	rm -f $(PROGRAMS) 
