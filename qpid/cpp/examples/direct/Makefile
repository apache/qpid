CXX=g++
CXXFLAGS= 

PROGRAMS=direct_config_queues listener direct_publisher direct_persistent_publisher
all: $(PROGRAMS)

direct_config_queues: direct_config_queues.cpp
	$(CXX) $(CXXFLAGS) -lqpidclient -o $@ $^

listener: listener.cpp
	$(CXX) $(CXXFLAGS) -lqpidclient -o $@ $^

direct_publisher: direct_publisher.cpp
	$(CXX) $(CXXFLAGS) -lqpidclient -o $@ $^

direct_persistent_publisher: direct_persistent_publisher.cpp
	$(CXX) $(CXXFLAGS) -lqpidclient -o $@ $^

clean:
	rm -f $(PROGRAMS) 
