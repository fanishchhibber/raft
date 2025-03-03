#!/usr/bin/env python3
import sys
import json
from util.msg import send, recv, node_id, Msg


me = node_id()

while True:
    msg: Msg = recv()
    msg["to"] = msg["from"]
    msg["from"] = me
    msg["type"] = "pong"
    msg["msg"] = "ECHO ===== " + msg["msg"]
    send(msg)   