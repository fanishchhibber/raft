'''
This is a convenience library to help send to and receive messages from distill.
It multiplexes input and timers.
'''

import sys
import json
from typing import Any, Dict, TextIO, Optional, List
from threading import Timer, Thread
import time
import queue
import re


Msg = Dict[str, Any]
TIMEOUT = "TIMEOUT"

__all__ = ["set_timeout", "node_id", "recv", "send",
           "Msg", "dump", "TIMEOUT", "Timer", "info"]


# pylint: disable=global-statement invalid-name
msgq: queue.SimpleQueue[Msg] = queue.SimpleQueue()


def info(s: str) -> None:
    print("INFO " + s, file=sys.stderr)


def set_timeout(duration_sec: float, name: str) -> Timer:
    '''
    When the timer expires, the next attempt to recv() will return the Timer object that timed out.
    '''
    def _on_timeout() -> None:
        msgq.put({"type": TIMEOUT, "timer": t, "name": name})

    t = Timer(duration_sec, _on_timeout)
    t.daemon = True  # don't hold up thread on exit
    t.start()
    return t


def send(msg: Msg) -> None:
    '''
    Formats msg as json and prints out on stdout.
    '''
    assert "to" in msg
    print(json.dumps(msg))
    sys.stdout.flush()

# pylint: disable=global-statement invalid-name
_gnode_id = ""


def node_id() -> str:
    global _gnode_id
    if _gnode_id:
        return _gnode_id
    for i, arg in enumerate(sys.argv):
        if arg == "--id":
            _gnode_id = sys.argv[i+1]
    if not _gnode_id:
        print("--id <id> argument not supplied", file=sys.stderr)
        sys.exit(1)
    if _gnode_id not in get_nodes():
        print(f"id {_gnode_id} is not present in config file", file=sys.stderr)
        sys.exit(1)

    return _gnode_id

# pylint: disable=global-statement invalid-name
_g_config: Dict[str, Any] = {}


def get_nodes(pattern: str = ".*") -> List[str]:
    global _g_config
    if not _g_config:
        for i, arg in enumerate(sys.argv):
            if arg == "--config":
                config_file = sys.argv[i+1]
                with open(config_file, encoding='utf-8') as f:
                    _g_config = json.load(f)
        if not _g_config:
            raise Exception("--config <configfile> absent in command-line parameters")
    return [node for node in _g_config if re.findall(pattern, node)]


def sibling_nodes() -> List[str]:
    pat = siblings_pattern()
    return get_nodes(pat) if pat else []


# regex pattern that encapsulates all the others
_others_pat: Optional[str] = None


def siblings_pattern() -> Optional[str]:
    '''if server_ids are of the form cp_server_1, cp_server_2, cp_server_3 etc,
    and this node's id ('me') is cp_server_2, return a regex pattern
    "cp_server[^2]" that matches all ids except me.
    This pattern is used to send broadcasts to other nodes'''
    global _others_pat
    if _others_pat:
        return _others_pat
    me = node_id()
    # split (say) 'kv_server_1", into "kv_server_" and "1"
    match = re.findall(r"^(.*)(\d+)$", me)
    if match:
        prefix, digit = match[0]
        _others_pat = prefix + '[^' + digit + ']'
    return _others_pat


dumpf: Optional[TextIO] = None


def dump(msg: Any) -> None:
    global dumpf
    if not dumpf:
        dumpf = open(f"DEBUG_{node_id()}.txt", 'w')
    print(f"{time.time()} {msg}", file=dumpf)
    dumpf.flush()


EXIT_MSG: Msg = {}


def _stdin_reader() -> None:
    '''
    Returns timeout events, messages as dictionaries (converted from JSON), and
    string objects if a command starting with "."
    '''
    while True:
        # import pdb;pdb.set_trace()
        line = sys.stdin.readline()
        if not line or line.startswith(".q"):
            msgq.put(EXIT_MSG)
            break
        line = line.strip()
        if line:
            try:
                msg = json.loads(line)  # json formatted str -> dict
                msgq.put(msg)
            except Exception:
                print(
                    f"Non-json msg received: '{line}'. Exiting", file=sys.stderr)
                msgq.put(EXIT_MSG)
                break

    sys.exit(0)


reader_thread = None


def recv() -> Msg:
    global reader_thread
    if not reader_thread:
        reader_thread = Thread(target=_stdin_reader)
        reader_thread.daemon = True  # Mark as daemon thread
        reader_thread.start()

    msg: Msg = msgq.get()
    if msg is EXIT_MSG:
        sys.exit(0)
    return msg
