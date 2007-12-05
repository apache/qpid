CXX=g++ 
CXXFLAGS= 

PROGRAMS=topic_listener topic_publisher
all: $(PROGRAMS)

topic_listener: topic_listener.cpp
	$(CXX) $(CXXFLAGS) -lqpidclient -o $@ $^

topic_publisher: topic_publisher.cpp
	$(CXX) $(CXXFLAGS) -lqpidclient -o $@ $^

clean:
	rm -f $(PROGRAMS) 
