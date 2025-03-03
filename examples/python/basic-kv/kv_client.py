#!/usr/bin/env python3

import sys
import json
from typing import Dict, Any
from util.msg import recv, node_id, send 

me = node_id()

reqid = 0  # global
def req(msg: Dict[str,Any]):
    global reqid
    reqid += 1
    msg["reqid"] = reqid
    send(msg)

def sendrecv(msg: Dict[str,Any]) -> Dict[str,Any]:
    req(msg)
    return recv()

# launch 3 echo_server processes configured in config.json )
req({"to": "distill", "type": "exec", "id" : "kv_server*"})

# send a ping to one of the servers
msg = sendrecv({"to": "kv_server_1", "from": me, "type": "W", "key": "aa", "value": "AA"})
assert msg["type"] == "ok"
assert msg["version"] == 1, f"Received version {msg['version']}"

msg = sendrecv({"to": "kv_server_1", "from": me, "type": "W", "key": "bb", "value": "BB"})
assert msg["type"] == "ok"
assert msg["version"] == 1, f"Received version {msg['version']}"

msg = sendrecv({"to": "kv_server_1", "from": me, "type": "W", "key": "aa", "value": "AAA"})
assert msg["type"] == "ok"
assert msg["version"] == 2, f"Received version {msg['version']}"

# Read
msg = sendrecv({"to": "kv_server_1", "from": me, "type": "R", "key": "aa"})
assert msg["value"] == "AAA"
assert msg["version"] == 2


