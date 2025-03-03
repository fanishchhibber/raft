#!/usr/bin/env python3

from typing import Dict, Tuple
from util.msg import send, recv, node_id

kvstore : Dict[str, Tuple[str, int]]= {}

me = node_id()

while True:
    msg = recv()
    msgtype: str = msg["type"]
    if msgtype == "W":
        key: str = msg["key"]
        value: str = msg["value"]
        version = 1
        if key in kvstore:
            (_, old_version) = kvstore[key]
            version = old_version + 1
        kvstore[key] = (value, version)            
        send({"to": msg["from"], "from": me, "reqid": msg["reqid"], "type": "ok", "version": version})
    elif msgtype == "R":
        key = msg["key"]
        val = kvstore.get(key, None)
        if val:
            value, version = val
            send({"to": msg["from"], "from": me, "reqid": msg["reqid"], "type": "ok", "version": version, "value": value})
        else:
            send({"to": msg["from"], "from": me, "reqid": msg["reqid"], "type": "err"})
    else:
        raise Exception(f"msg type {msgtype} not found")


