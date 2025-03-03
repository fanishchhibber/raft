# Distill


## Introduction

A distributed system has nodes that communicate with other nodes, via a shared network, to achieve a common objective. 

`distill` is a simple framework for learning distributed systems. 

1. In this framework, a node is simply an OS process that receives text messages on stdin and sends text messages to other nodes on its stdout. This way, one can choose a language of your choice for creating nodes and there is no fussing with HTTP/TCP or sockets. But because they are independent processes, they can be independently killed or stopped to test out various scenarios. 

2. There is a central orchestrator and router -- distill.py -- that launches nodes as child processes and captures their stdin and stdout. When a node writes to its stdout, distill receives it, looks for a destination node id in the message and forwards that message to the appropriate node's stdin. All messages pass through distill, and are printed on distill's console.

3. Messages are required to be formatted in json.  The only requirement is that they contain a "to" attribute (e.g. `{"to": "server_1"}`), so distill knows the node to which to send. By convention, we use the attribute `from` as the sender's node id, so that responses can be sent accordingly. 

4. Node ids are fixed in a simple configuration file, that maps ids to executables. 

For example, in the file `config.json`

```json 
   { "client-1": "./client.sh",
     "client-2": "./client.sh",
     "client-3": "./client.sh",
     "client-4": "./client.sh",
     "server-1": "./mem-node.py",
     "server-2": "./mem-node.py",
     "conductor": "bin/conductor"
   }
```

In this sample configuration, there can be at most seven nodes executing. The node "client-1" refers to a process executing a shell script. Java programs have to be wrapped in a executable shell script since the configuration does not allow for arguments to a program. 

This concludes the overview. Details follow.

## Running the python examples 


     $ export PYTHONPATH=$PWD/examples/python
     $ export PATH="$PWD:$PATH"
     $ cd examples/python/echo
     $ distill.py --start echo_client

## Running the Java examples
The java examples need the org.json package in the classpath. 

     $ cd examples/java
     $ export CLASSPATH=$PWD/examples/java/target/classes:$HOME/.m2/repository/org/json/json/20231013/json-20231013.jar
     $ mvn compile 
     $ cd echo
     $ distill.py --start '.*'

   The --start pattern matches all ids, so it starts both client and server at the same time.  


## Command line arguments to distill

     usage: distill.py [-h] [--config <config file>] [--start START] [--nopp] [--trace TRACE] 

The `--config` argument is the name of a json file structured as shown earlier. Default: `config.json`

The `-trace` file is used to record all messages passing through distill.  This is used to later check for inconsistent behaviors.

The `--start` option is to specify a regular expression pattern; all node id's that match that pattern are launched at startup. For example, the following might start all the clients and also server-1.

```
    # Launch all clients and server-1 at start up.
    distill.py --start "cli.*|server-1"
```

The `--nopp` is to avoid pretty-print messages shown on the console or in the trace file. When --nopp has been requested, the messages are shown in their verbose JSON format. 

## Messages

Messages between nodes are *single* lines (terminating in newline) containing json. The only field that is required is "to", so distill knows whom to send it to, as with postal letters. There is no constraint on the rest of the fields or structure of fields.

By writing to stdout, a node sends a message to the distill router, which parses the "to" field and enqueues the message to the appropriate node. If the "to" field does not match a specific id, the value of that field is treated as a regular expression pattern, and tested against all node-ids. All matching node ids get the same message, with just the "to" message tailored. 

e.g. The following message will be broadcast to all nodes containing `ser` and ending in a 0 or 1.
```json
   {"from": "client-2", "to": "ser.*[01]$", _other fields_}
```

# Launching a node

By default no nodes are launched. They have to be explicitly brought up, and only you know which ones you want to bring up in which order. 

There are two ways to launch a node

1. At startup time with the `--start` command-line argument. Recall that you can specify a regular expression to identify the nodes to be launched.

2. In the program, say 'client', send a message to the special node 'distill' as follows:

```json
   {"type": "exec", "id" : "echo_server_1"}
```

The second approach is used in a testing or demo scenario, where the client is launched first which in turn asks distill to launch one or more servers.

In both cases, distill launches the executable(s) specified in `config.json` corresponding to the supplied id (or pattern) (from config.json) as follows
```
   echo_server.py --id echo_server_1 --config config.json
```


### Killing a node. 

distill can be told to kill one or more nodes. 

```json
   {"type": "kill", "to" : "server.*"}
```

The "to" address is either a node id or a pattern, and all matching nodes are silently killed by distill. It is up to the user to arrange a special message to tell a node to cleanup before it gets killed. In this example, all "server" nodes are killed.

## Utility libraries. 

Although writing a node from scratch is quite simple, there is a small convenience library provided for both Python and Java (utils/msg.py and Utils.java respectively).  Please read the documentation for the functions exported in those files.

These are the functions specifically to look for, in the two libraries. (Note: python naming convention shown here; the equivalent Java methods are in camelCase)

1. node_id().  Parses command line argument --id _node\_id_ passed to the node from distill.
2. sibling_nodes().  All nodes having the same pattern as this node's id, except for the number at the end.
3. get_nodes(pattern). All nodes matching a regex pattern.
4. recv() : Decodes the line from stdin and returns a Msg object in python, or a JSONObject in Java
5. set_timeout(duration_sec, name):  After the prescribed duration, recv() receives a timeout message. 

recv() thus multiplexes timeout and incoming messages. 

## TODO

Distill will always remain a router and launcher of nodes. However, support will be added for introducing network errors such as probabilistic drops, slowdowns, delayed messages, partitioning and so on.
