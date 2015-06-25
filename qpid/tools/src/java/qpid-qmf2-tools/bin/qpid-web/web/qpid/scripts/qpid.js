/**
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * This library provides a JavaScript implementation of the QMF2 API (and TODO qpid::messaging - some code is in place
 * for Connection as necessary to implement the QMF2 API the rest will follow in due course.
 *
 * This implementation of the QMF2 API relies on the Qpid REST API as a back end server and the QMF2 API methods
 * are basically proxies by the Qpid REST API to a real QMF2 Console implemented on the back end. Note that this
 * implementation uses AJAX/REST over pure HTTP which results in some inefficiencies, in particular the mechanism
 * used to retrieve QMF2 Events (getWorkItem()) uses the AJAX long-polling pattern. It should be possible to
 * provide an alternative implementation using WebSockets, the main two reasons that this hasn't been done are.
 * 1) The author lacks familiarity with WebSockets....
 * 2) WebSockets have much poorer cross-browser support, though to be fair that could be mitigated using a
 *    WebSocket JavaScript library that could fall back to using HTTP if browser/server support was unavaiable.
 *
 * This library also includes a utility package providing a number of useful classes and helper functions. These
 * aren't strictly part of qpid/qmf JavaScript but stringify() and randomUUID() are used by qpid.js so including
 * the util package in this library avoids adding yet another dependency.
 *
 * It has dependencies on the following:
 * jquery.js (> 1.5)
 *
 * author Fraser Adams
 */

//-------------------------------------------------------------------------------------------------------------------

// Create a new namespace for the util "package".
var util = {};

/**
 * This debug method lists the properties of the specified JavaScript object.
 * @param obj the object that we wish to list the properties for.
 " @return a string containing the list of the object's properties pretty printed.
 */
util.displayProperties = function(obj) {
	var result = obj + "\n\n";
	var count = 0;
	for (var i in obj) {
		result += i + ", ";
		if (count % 4 == 3) result += "\n";
		count++;
	}
    result = result.substring(0, result.lastIndexOf(","));
    return result;
};

/**
 * Stringify an Object into JSON. Uses JSON.stringify if present and if not it uses a quick and dirty serialiser.
 * @param obj the object that we wish to stringify into JSON.
 * @return the JSON representation of the specified object.
 */
util.stringify = function(obj) {
    var fromObject = function(obj) {
        if (Object.prototype.toString.apply(obj) === '[object Array]') {
            var string = "";
            var length = obj.length;
	        for (var i = 0; i < length; i++) {
                string += fromObject(obj[i]);
                if (i < length - 1) {
                    string += ",";
                }
            }

            string = "[" + string + "]";
            return string;
        } else if (typeof obj == "object") { // Check if the value part is an ObjectId and serialise appropriately
            if (!obj) {
                return "null";
            }

            var string = "";
	        for (var i in obj) {
                if (obj.hasOwnProperty(i)) {
                    if (string != "") {
                        string += ",";
                    }
                    string += '"' + i + '":' + fromObject(obj[i]);
                }
            }

            string = "{" + string + "}";
            return string;
        } else if (typeof obj == "string") {
            return '"' + obj + '"';
        } else {
            return obj.toString();
        }
    };

    if (obj == null) {
        return "";
    } if (window.JSON && JSON.stringify && typeof JSON.stringify == "function") {
        return JSON.stringify(obj);
    } else {
        var string = fromObject(obj);
        return string;
    }
};

/**
 * Compact rfc4122v4 UUID from http://stackoverflow.com/questions/105034/how-to-create-a-guid-uuid-in-javascript
 * @return an rfc4122v4 UUID.
 */
util.randomUUID = function() {
    return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, function(c) {
        var r = Math.random()*16|0, v = c == 'x' ? r : (r&0x3|0x8);
        return v.toString(16);
    });
};

/**
 * This is a JavaScript port of the "fill count" example from http://en.wikipedia.org/wiki/Circular_buffer
 * @param size maximum size of the RingBuffer that we want to construct, when items are added beyond this size
 * they will wrap around.
 */
util.RingBuffer = function(size) {
    var _size = size;
    var _start = 0;
    var _count = 0;
    var _elems = new Array(size);

    /**
     * @return the maximum size of the RingBuffer.
     */
    this.capacity = function() {
        return _size;
    };

    /**
     * @return the number of items currently stored in the RingBuffer.
     */
    this.size = function() {
        return _count;
    };

    /**
     * @return true if the buffer is full otherwise return false.
     */
    this.isFull = function() {
        return _count == _size;
    };

    /**
     * @return true if the buffer is empty otherwise return false.
     */
    this.isEmpty = function() {
        return _count == 0;
    };

    /**
     * Add an item to the end of the buffer, overwriting oldest element if buffer is full. 
     * A client can choose to avoid the overwrite by checking IsFull().
     * @param the item that we wish to add to the end of the ring buffer.
     */
    this.put = function(item) {
        var end = (_start + _count) % _size;
        _elems[end] = item;
        if (_count == _size) {
            _start = (_start + 1) % _size; // full, overwrite
        } else {
            ++_count;
        }
    };

    /**
     * Read and remove oldest item from buffer. N.B. clients must ensure !isEmpty() first.
     * @return the oldest item from the ring buffer.
     */
    this.take = function() {
        var item = _elems[_start];
        _start = (_start + 1) % _size;
        --_count;
        return item;
    };

    /**
     * Read the item at the specified (circular) index non-destructively e.g. index 0 is the first item held in
     * the ring buffer index size() - 1 is the last item (which is the equivalent of getLast()).
     * @return the specified ring buffer item.
     */
    this.get = function(index) {
        index = (_start + index) % _size;
        return _elems[index];
    };

    /**
     * @return the last item, or null if no entries are present.
     */
    this.getLast = function() {
        if (_count == 0) {
            return null;
        } else {
            return this.get(_count - 1);
        }
    };
};


//-------------------------------------------------------------------------------------------------------------------
/**
 * This package is a proxy to the Qpid REST API, which is itself a proxy to a real Qpid API.
 * It attempts to mimic the Qpid Messaging API where possible (given the asynchronous constraints of JavaScript).
 */

// Create a new namespace for the qpid "package".
var qpid = {};

/**
 * This factory class will attempt to create a Connection object and creates an opaque handle to it internally
 * which will be used in the URI of subsequent Qpid/QMF calls to the Qpid REST API.
 *
 * @param url an AMQP 0.10 URL, an extended AMQP 0-10 URL, a Broker URL or a Java Connection URL.
 * @param opts a String containing the options encoded using the same form as the C++ qpid::messaging Connection class.
 * @return a Connection object which is a proxy to the Qpid REST API, which is itself a proxy to a real Qpid API.
 */
qpid.ConnectionFactory = function(url, connectionOptions) {

    /** 
     * This class is a proxy to the Qpid REST API PUT method for creating Connections.
     * @param url an AMQP 0.10 URL, an extended AMQP 0-10 URL, a Broker URL or a Java Connection URL.
     * @param opts a String containing the options encoded using the same form as the C++ qpid::messaging Connection.
     */
    var Connection = function(url, connectionOptions) {
        var _disableEvents = false;
        var _available = false;
        var _url = url;
        var _handle = util.randomUUID();
        var _defaultCallback = function(connection) {};
        var _callback = _defaultCallback;
        var _failureCallback = _defaultCallback;

        /**
         * The success callback method for the AJAX call invoked by putConnection(). This method marks the
         * Connection as available and calls the callback method registered by the call to open().
         */
        var putConnectionSucceeded = function() {
            _available = true;
            _callback(this);
        };

        /**
         * The failure callback method for the AJAX call invoked by putConnection(). This method calls the callback 
         * method registered by the call to open().
         */
        var putConnectionFailed = function(xhr) {
            _available = false;
            _failureCallback(this);
        };

        /**
         * Create a Connection object on the REST API via an asynchronous HTTP PUT method.
         */
        var putConnection = function() {
            var data = {url: _url};
            if (connectionOptions != null && connectionOptions != "") {
                data.connectionOptions = connectionOptions;
            }

            if (_disableEvents) {
                data.disableEvents = true;
            }

            // Serialise the data Object into a JSON String.
            data = util.stringify(data);

            $.ajax({
                type: "PUT",
                url: "../qpid/connection/" + _handle,
                cache: false,
                contentType: "application/json",
                data: data,
                timeout: 10000,
                success: putConnectionSucceeded,
                error: putConnectionFailed
            });
        };

        /**
         * Remove a Connection object on the REST API via a synchronous HTTP DELETE method.
         */
        var deleteConnection = function() {
            //console.log("**** calling qpid.Connection deleteConnection() ****");
            /**
             * Explicitly retrieve the XmlHttpRequest and send the DELETE via a low-level synchronous call because
             * JQuery 1.8 has deprecated the async: false setting on $.ajax. There's some debate about this - see
             * http://bugs.jquery.com/ticket/11013 the gist is that synchronous requests can prevent the rest of
             * the $.ajax plumbing and dependencies from being fixed. In an ideal world asynchronous requests
             * are almost always preferable, however in mobile Safari (at least) asynchronous calls won't get
             * fired from onpagehide handlers, which messes up some useful garbage collection when navigating away.
             * TODO I wonder how delete onpageunload is going to work if I want to use JSONP......
             */
            var xhr = $.ajaxSettings.xhr(); // At least we can get the XMLHttpRequest in a platform neutral way. 
            xhr.open("DELETE", "../qpid/connection/" + _handle, false); // Low level synchronous DELETE.
            xhr.send(null);
        };

        /**
         * Calling this method results in a QMF2 Console being created that can only perform synchronous calls such
         * as getObjects() and can't do asynchronous things like receive Agent updates and QMF2 Events.
         *
         * This method must be called besfore addConnection() in order to take effect.
         */
        this.disableEvents = function() {
            _disableEvents = true;
        }

        /**
         * Open the Connection object for use.
         * @param successCallback a handler to be called when open() has successfully established the Qpid Connection.
         * we include the callback method because JavaScript networking is fundamentally asynchronous so open()
         * won't block as it would with other languages, the callback provides a way to defer execution of subsequent
         * code until the Connection is established.
         * @param failureCallback a handler to be called when open() has failed to established the Qpid Connection.
         * Note that this will only be called if creating the connection is impossible, that is to say an exception
         * got thrown by the REST API PUT mothod. If the broker is simply down the Connection proxy will get created
         * on the server side and the successCallback will be called.
         */
        this.open = function(successCallback, failureCallback) {
            if (successCallback != null) {
                _callback = successCallback;
            }

            if (failureCallback != null) {
                _failureCallback = failureCallback;
            }

            _available = false;
            putConnection();
        };

        /**
         * Close the Connection object.
         */
        this.close = function() {
            deleteConnection();
            _available = false;
        };

        /**
         * Identify whether the Connection is open for use.
         * @return true if the Connection is open, otherwise returns false.
         */
        this.isAvailable = function() {
            return _available;
        };

        /**
         * Retrieve the Connection's "handle" which is used as part of its URI on the REST API.
         * @return a String containing the handle UUID.
         */
        this.toString = function() {
            return _handle;
        };
    };

    /**
     * Factory method used to construct a new Connection object.
     * @return a new Connection object.
     */
    this.createConnection = function() {
        return new Connection(url, connectionOptions);
    };
};

//-------------------------------------------------------------------------------------------------------------------

// Create a new namespace for the qmf "package".
var qmf = {};
qmf.REFRESH_PERIOD = 10000;

/**
 * This class is a proxy to the QMF REST API, which is itself a proxy to a real QMF2 Console.
 * It attempts to mimic the QMF2 API where possible however there are a few deviations because of the entirely
 * asynchronous nature of JavaScript and AJAX.
 *
 * Constructor that provides defaults for name and domain and takes a Notifier/Listener.
 * @param onEvent a QMFEventListener.
 */
qmf.Console = function(onEvent) {
    var _disableEvents = false;
    var _connection = null;
    var _url = "../qpid/connection/";
    var _qmfEventListenerXHR; // Retain JQuery XHR object for QmfEventListener so we can abort request if needed.

    /**
     * Send and "AGENT_DELETED" WorkItem for qpidd to the registered Event Listener.
     */
    var sendBrokerDisconnectedEvent = function() {
        var agent = {_vendor: "apache.org", _product: "qpidd", _instance: "1234",
                     _name: "apache.org:qpidd:1234", _epoch: 1, _heartbeat_interval: 10};
        onEvent({_type: "AGENT_DELETED", _params: {agent: agent}});
    };

    /**
     * Send and "AGENT_DELETED" WorkItem for qpid.restapi to the registered Event Listener.
     */
    var sendRestApiDisconnectedEvent = function() {
        var agent = {_vendor: "apache.org", _product: "qpid.restapi", _instance: "1234",
                     _name: "apache.org:qpid.restapi:1234", _epoch: 1, _heartbeat_interval: 10};
        onEvent({_type: "AGENT_DELETED", _params: {agent: agent}});
    };

    /** 
     * Retrieve QMF2 WorkItems via the QMF2 REST API, note that this call may block (on the server).
     */
    var dispatchEvents = function() {
        if (_connection != null) {
            _qmfEventListenerXHR = $.ajax({
                url: _url + "/console/nextWorkItem",
                cache: false,
                dataType: "json",
                timeout: 3*qmf.REFRESH_PERIOD,
                success: handleDispatchEventsSuccess,
                error: handleDispatchEventsFailure
            });
        }
    };

    /**
     * Success callback method for dispatchEvents. When WorkItems are available they are delivered to the attached
     * eventListener onEvent callback and dispatchEvents is called again to wait for the next WorkItem.
     * @param data the QMF2 WorkItem data.
     */
    var handleDispatchEventsSuccess = function(data) {
        if (_connection != null) {
            onEvent(data);
            dispatchEvents();
        }
    };

    /**
     * Failure callback method for dispatchEvents. This method sends "AGENT_DELETED" WorkItems to the registered
     * Event Listener and attempts to re-establish a connection to the REST API and via the the broker.
     * @param xhr the jQuery XHR object.
     */
    var handleDispatchEventsFailure = function(xhr) {
        //console.log("handleDispatchEventsFailure " + xhr.status + " " + xhr.statusText);
        if (xhr.status == 0 || xhr.status == 12029) { // For some reason IE7 sends 12029??
            if (xhr.statusText == "timeout") { // If AJAX calls have timed out it's likely due to a failed broker.
                sendBrokerDisconnectedEvent();
            } else {
                sendRestApiDisconnectedEvent(); // If the status is 0 for another reason the server is probabbly down.
            }
        } else if (xhr.status == 404) {
            // HTTP Not Found. This is most likely to mean that the Console has timed out and been garbage collected
            // on the REST API Server so we simply attempt to re-open the Qpid Connection.
            if (_connection != null && _connection.open) {
                _connection.open();
            }
        } else if (xhr.status == 500) {
            // HTTP Internal Error. Sent by the REST API Server when it knows that the broker has disconnected.
            sendBrokerDisconnectedEvent();
        }

        // If the failure wasn't caused by an abort we retry after a timeout.
        if (xhr.statusText != "abort" && _connection != null) {
            setTimeout(dispatchEvents, qmf.REFRESH_PERIOD);
        }
    };

    /**
     * Helper method to allow us to get the data from a specified resource from the REST API in the /console/ sub-path
     * Most of the core QMF2 API mehods can make use of this.
     * @param resourceName the name of the resource on the REST Server. This is the part of the resource after
     * the connection e.g. "/console/objects/" + className for the getObjects() call.
     * @param handler the callback handler if the AJAX GET is successful.
     * @return the jQuery XHR Object.
     */
    var getResource = function(resourceName, handler) {
        return $.ajax({
            url: _url + resourceName,
            cache: false,
            dataType: "json",
            timeout: 3*qmf.REFRESH_PERIOD,
            success: handler
        });
    };

    // ******** QmfConsoleData Methods that will be attached to QmfData Objects via makeConsoleData()**********

    /**
     * Invoke the named method using the supplied inArgs, the response occurs asynchronously and triggers the
     * named handler method, the outArgs are sent as JSON to the data parameter of the handler method.
     * Note that this method is intended to be attached to a QmfData JavaScript object. It will use the ObjectId
     * of the QmfData object to determine the URL resource to POST the data to.
     */
    var invokeMethod = function(name, inArgs, handler) {
        //console.log("calling invokeMethod: " + name + ", oid: " + this._object_id);
        var defaultHandler = function(data) {};

        var postFailed = function(xhr) {
            var error = xhr.responseText;
            if (xhr.status != 500) {
                error = (xhr.status == 0) ? "POST failed to return correctly." : xhr.statusText;
            }

            handler({"error_text" : error});
        };

        inArgs = (inArgs == null || typeof inArgs == "string") ? inArgs : util.stringify(inArgs);
        handler = (handler == null) ? defaultHandler : handler;

        var data = (inArgs == null) ? '{"_method_name":"' + name + '"}' :
                                      '{"_method_name":"' + name + '","_arguments":' + inArgs + '}';

        $.ajax({
            type: "POST",
            url: _url + "/object/" + this._object_id,
            cache: false,
            headers : {"cache-control": "no-cache"}, // Curtails iOS6 overly aggressive (incorrect!) caching.
            contentType: "application/json",
            data: data,
            timeout: 10000,
            success: handler,
            error: postFailed
        });
    };

    /**
     * Request that the Agent updates the value of this object's contents.
     * @param handler a callback method to handle the asynchronously delivered object refresh.
     * One slight quirk of the JavaScript implementation is that the state update occurs asynchronously which will
     * affect code that tries to use the state immediately after a call to object.refresh(). The optional handler
     * parameter allows client code to defer its code to a callback that gets triggered after the state update.
     */
    var refresh = function(handler) {
        var self = this; // So we use the correct this in the update method....

        var update = function(data) {
            // Save these timestamps from the original object as they are not correctly populated by the ManagementAgent.
            var savedCreateTime = self._create_ts;
            var savedDeleteTime = self._delete_ts;

            if (handler == null) {
                // If no handler is supplied we update the state of the object itself.

                // Replace all of the current properties with the ones from the JSON response object.
                for (var i in data) {
                    self[i] = data[i]
                }

                // Restore the correct timestamps.
                self._create_ts = savedCreateTime;
                self._delete_ts = savedDeleteTime;
            } else {
                // If a handler is supplied we pass the JSON object returned from the query having set the correct
                // timestamps and turned back into a QmfConsoleData.
                // Restore the correct timestamps.
                data._create_ts = savedCreateTime;
                data._delete_ts = savedDeleteTime;
                data.invokeMethod = self.invokeMethod;
                data.refresh = self.refresh;
                handler(data);
            }
        };

        return getResource("/object/" + this._object_id, update);
    }

    // ******************************************** Public Methods ********************************************

    /**
     * Calling this method results in a QMF2 Console being created that can only perform synchronous calls such
     * as getObjects() and can't do asynchronous things like receive Agent updates and QMF2 Events.
     * Note that "asynchronous" here relates to the underlying QMF Console created on the Server, as this is a
     * JavaScript API things like getObjects() results still arrive asynchronously.
     *
     * This method must be called besfore addConnection() in order to take effect.
     */
    this.disableEvents = function() {
        _disableEvents = true;
    }

    /**
     * Connect the console to the AMQP cloud.
     *
     * @param connection a JavaScript qpid.Connection object. Alternatively if the handle string to the Connection 
     * object on the server is known this string can be supplied instead. This is most likely to be the case where
     * the default QMF Console on the REST API Server is used e.g. by doing _console.addConnection("default");
     * @param failureCallback a handler to be called when addConnection() has failed to established the Qpid Connection.
     * Note that this will *only* be called if creating the connection is impossible, that is to say an exception
     * got thrown by the REST API PUT mothod. If the broker is simply down the Connection proxy will get created
     * on the server side and the successCallback will be called.
     */
    this.addConnection = function(connection, failureCallback) {
        _connection = connection;
        _url = _url + _connection.toString();

        if (_connection.open) {
            if (_disableEvents) {
                _connection.disableEvents();
                _connection.open(null, failureCallback);
            } else {
                _connection.open(dispatchEvents, failureCallback);
            }
        } else { // Use this case if the connection that is passed in is just a string "handle" to the connection
            dispatchEvents();
        }
    };

    /**
     * Remove the AMQP connection from the console. Un-does the addConnection() operation. Note that because this
     * API implementation is really a proxy removeConnection() just aborts any AJAX calls for the console.
     *
     * @param connection a JavaScript qpid.Connection object
     */
    this.removeConnection = function(connection) {
        if (_connection == connection) {
            _connection = null;

            if (_qmfEventListenerXHR) {
                _qmfEventListenerXHR.abort();
            }
        }
    };

    /**
     * Release the Console's resources.
     */
    this.destroy = function() {
        //console.log("Console.destroy()");
        this.removeConnection(_connection);
    };

    /**
     * Perform a query for QmfData objects. In a difference to the specified QMF2 API rather than returning a list 
     * (possibly empty) of matching objects this JavaScript version triggers a callback, the data parameter of
     * which contains the list of matching objects. Usage example:
     * _console.getObjects("broker", function(data) {_objects.broker = data;});
     *
     * @param className the class name QMF Management Objects that we wish to retrieve.
     * TODO packageName and agentName.
     * @param handler a handler to be called when getObjects() has successfully retrieved the specified objects.
     * we include the callback method because JavaScript networking is fundamentally asynchronous so getObjects()
     * won't block as it would with other languages, the callback provides a way to defer execution of subsequent
     * code until the objects have been returned.
     * @return returns the jQuery XHR object. In particular the reson for returning this is that it is a "Deferred"
     * object, what this means is that is can be used to wait until the results from several getObjects() calls
     * have returned before executing an overall callback.
     */
    this.getObjects = function(className, handler) {
        // TODO allow options to specify Package name and Agent name.
        return getResource("/console/objects/" + className, handler);
    };

    /**
     * The items returned by getObjects are really pure QmfData (really QmfManaged) Objects, they are data Objects
     * with no methods. This is normally OK because most applications simply want to retrieve the properties/stats
     * but there are occasions where some of the QmfConsoleData methods are useful. Rather than add the methods
     * universally this method enables them to be added to specific QmfData instances. This approach is more
     * efficient as the QmfData objects are created by the browser deserialising JSON and it seems a bit wasteful
     * to iterate through turning the JSON data into full QmfConsoleData Objects when the methods are rarely used.
     * @param data the QmfData object that we want to turn into a QmfConsoleData.
     */
    this.makeConsoleData = function(data) {
        data.invokeMethod = invokeMethod;
        data.refresh = refresh;
    };

    /**
     * Get the AMQP address this Console is listening to.
     *
     * @param handler a callback method to handle the asynchronously delivered response.
     * the data passed to the handler contains the console's replyTo address. Note that there are actually two,
     * there's a synchronous one which is the return address for synchronous request/response type invocations and 
     * there's an asynchronous address with a ".async" suffix which is the return address for asynchronous invocations.
     * @return returns the jQuery XHR object. See getObjects() documentation for more details on why.
     *
     * _console.getAddress(function(data) {console.log(data)}); // Example usage.
     */
    this.getAddress = function(handler) {
        return getResource("/console/address", handler);
    };

    /**
     * Gets a list of all known Agents.
     *
     * @param handler a callback method to handle the asynchronously delivered response.
     * the data passed to the handler contains a list of all known Agents as a JSON array each item in the array is
     * a QMF Agent object in JSON form.
     * @return returns the jQuery XHR object. See getObjects() documentation for more details on why.
     *
     * _console.getAgents(function(data) {console.log(data)}); // Example usage.
     */
    this.getAgents = function(handler) {
        return getResource("/console/agents", handler);
    };

    /**
     * Gets the named Agent, if known.
     *
     * @param agentName the name of the Agent to be returned.
     * @param handler a callback method to handle the asynchronously delivered response.
     * the data passed to the handler is the QMF Agent object in JSON form.
     * @return returns the jQuery XHR object. See getObjects() documentation for more details on why.
     *
     * _console.getAgent("qpidd", function(data) {console.log(data)}); // Example usage.
     */
    this.getAgent = function(agentName, handler) {
        return getResource("/console/agent/" + agentName, handler);
    };

    /**
     * In this implementation findAgent() is a synonym for getAgent().
     */
    this.findAgent = function(agentName, handler) {
        return getResource("/console/agent/" + agentName, handler);
    };

    /**
     * Gets a list of all known Packages.
     * @param handler a callback method to handle the asynchronously delivered response.
     * the data passed to the handler is a list of all available packages as a JSON array.
     * @return returns the jQuery XHR object. See getObjects() documentation for more details on why.
     *
     * _console.getPackages(function(data) {console.log(data)}); // Example usage.
     */
    this.getPackages = function(handler) {
        // TODO handle getPackages() for specified Agent.
        return getResource("/console/packages", handler);
    };

    /**
     * Gets a List of SchemaClassId for all available Schema.
     * @param handler a callback method to handle the asynchronously delivered response.
     * the data passed to the handler is a list of all available classes as a JSON array of SchemaClassId.
     * @return returns the jQuery XHR object. See getObjects() documentation for more details on why.
     *
     * _console.getClasses(function(data) {console.log(data)}); // Example usage.
     */
    this.getClasses = function(handler) {
        // TODO handle getClasses() for specified Agent
        return getResource("/console/classes", handler);
    };

    /**
     * TODO
     * getSchema() - should be easy as the REST API supports it.
     * createSubscription() - harder as the REST API doesn't yet support it.
     * refreshSubscription() - harder as the REST API doesn't yet support it.
     * cancelSubscription() - harder as the REST API doesn't yet support it.
     */
};

