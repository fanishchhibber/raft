#!/usr/bin/env python3

import sys
import json
from util.msg import send, recv, node_id, Msg
 

me = node_id()

# launch 3 echo_server processes configured in config.json )
send({"to": "distill", "type": "exec", "id" : "echo_server.*"})

# send a ping to one of the servers
send({"to": "echo_server_2", "from": me, "type": "ping", "msg": "hello"})

msg: Msg = recv()
assert msg["type"] == "pong"
assert msg["msg"] == "ECHO ===== hello"
assert msg["from"] == "echo_server_2"
assert msg["to"] == me

# send ping to all using regex pattern
send({"from": me, "type" : "ping", "to" :  "echo_server.*", "msg" : "Testing pattern matching"})
responses = set()
for i in range(3):
    msg = recv()
    assert msg["type"] == "pong"
    assert msg["msg"] == "ECHO ===== Testing pattern matching"
    assert msg["from"].startswith("echo_server_")
    assert msg["to"] == me
    responses.add(msg["from"])
assert len(responses) == 3

        


