/**
 * This file allows the initial set of Console Connections to be configured and delivered to the client.
 * The format is as a JSON array of JSON objects containing a url property and optional name, connectionOptions and
 * disableEvents properties. The connectionOptions property has a value that is itself a JSON object containing
 * the connectionOptions supported by the qpid::messaging API.
 */
/*
// Example Console Connection Configuration.
qmfui.Console.consoleConnections = [
    {name: "default", url: ""},
    {name: "localhost", url: "localhost:5672"},
    {name: "wildcard", url: "anonymous/@0.0.0.0:5672", connectionOptions: {sasl_mechs:"ANONYMOUS"}, disableEvents: true}
];
*/

