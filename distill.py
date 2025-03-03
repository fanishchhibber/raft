#! /usr/bin/env python3
import asyncio
from asyncio.subprocess import Process
import atexit
import json
import sys
import time
import re
import traceback
import argparse
from pathlib import Path
import os
from typing import Dict, Any, Callable, Optional, Awaitable

Msg = Dict[str, Any]

def fatal_error(err):
    print(err, file=sys.stderr)
    asyncio.get_event_loop().stop()
    sys.exit(1)

class Node:
    def __init__(self, node_id:str, 
                 on_msg_cb: Callable[[str], Awaitable[None]], # async callbacks of the form async foo(str)
                 on_err_cb: Callable[[str], Awaitable[None]], 
                 on_exit_cb: Callable[[str, Any], None]):
        """After creating Node, you must call await node.launch to actually spawn"""
        self.process: Optional[Process]= None
        self.node_id: str = node_id
        self.on_msg_cb = on_msg_cb
        self.on_err_cb = on_err_cb
        self.on_exit_cb = on_exit_cb
        self.write_q: asyncio.Queue = asyncio.Queue()

    def kill(self):
        try:
            # process.kill doesn't terminate processes whose main thread is
            # blocked on recv() or msgq.get() By writing EOF, we trigger the
            # stdin thread to trigger the main thread to quit. 
            self.process.stdin.write_eof()
        except:
            pass

    async def launch(self, executable:str, id:str, config_file:str):
        """Launch the executable as a subprocess and handle its streams."""    
        # Check if executable exists and is indeed executable
        if not (Path(executable).exists() and os.access(executable, os.X_OK)):
            print(f"Either executable '{executable} does not exist, or is not marked executable", file=sys.stderr)
            sys.exit(1)
    
        process = await asyncio.create_subprocess_exec(
            executable,
            "--id", id, "--config", config_file,
            stdin=asyncio.subprocess.PIPE,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE
        )
        self.process = process

        # Task to read subprocess's stdout
        async def read_stdout():
            error = None
            assert process.stdout
            try:
                while True:
                    line = await process.stdout.readline()
                    if not line:
                        break
                    msg = line.decode().strip()
                    
                    await self.on_msg_cb(msg)
            except Exception as e:
                error = e

            self.on_exit_cb(self.node_id, error)
            # Process finished executing.

        async def read_stderr() -> None:
            error: Any = None
            assert process.stderr
            try:
                while True:
                    line = await process.stderr.readline()
                    if not line:
                        break
                    error = line.decode().strip()
                    if error.startswith("INFO "):
                        error = error[5:]
                    else:
                        error = "***" + error
                    await self.on_err_cb(f'{self.node_id}: {error}')
            except Exception as e:
                error = e
            self.on_exit_cb(self.node_id, error)
        
        async def write_stdin() -> None:
            assert process.stdin # to get rid of mypy error.
            error: Any = None
            try:            
                q = self.write_q
                while True:
                    msg = await q.get()
                    assert isinstance(msg, dict)
                    textmsg = json.dumps(msg) + "\n"
                    bytestring = textmsg.encode()
                    process.stdin.write(bytestring)
                    await process.stdin.drain()
            except Exception as e:
                error = e

            self.on_exit_cb(self.node_id, error)
                    

        for coro in [read_stdout(), read_stderr(), write_stdin()]:
            asyncio.create_task(coro)

    async def enq_message(self, message: Msg) -> None:
        """Enque this message for the appropriate process. Because it is enqueued, ensure message
        is not aliased or reused. The queue is drained by the write_stdin() task
        """
        assert isinstance(message, dict)
        assert message.get("to") # This is really the only requirement of a message. It must have a dest address
        try:
            #start = time.time()
            await self.write_q.put(message)
            #print (f"msg put {message} elapsed time for put: {time.time() - start}", file=sys.stderr)
        except asyncio.QueueFull:
            self.on_err_cb(f'{{"node_id": "{self.node_id}", "error": "Queue full. message dumped": "{message}"}}')


class Distill():
    def __init__(self, config_file: str="config.json", trace_file:str="-", pretty_print:bool = True):
        self.nodes : Dict[str, Node]= {}
        self.first_log_ts = 0 # for timestamp used for logging

        try:
            self.config_file = config_file
            self.pretty_print = pretty_print
            if trace_file == "-":
                self.trace = sys.stdout
            else:
                self.trace = open(trace_file, 'w') # overwrite. TODO: create a trace_file with a new version to avoid overwriting
            with open(config_file) as f:
                self.config = json.load(f)

        except Exception as e:
            fatal_error(f"Error initializing: {traceback.format_exc()} ")

    def _log_offset(self):
        offset = 0
        ts = time.time() 
        if not self.first_log_ts:
            self.first_log_ts = time.time()
        offset = int((ts - self.first_log_ts) * 1000)
        return offset
        

    def write_err_trace(self, msg: str):
        if not self.trace:
            return
        offset = self._log_offset()
        self.trace.write(f"{offset:<8} {msg}\n" )
        self.trace.flush()


    def write_trace(self, message: Msg):
        """Log messages to the trace file with a timestamp offset in millis. The first write is at offset 0"""
        if not self.trace:
            return
        offset = self._log_offset()
        if not self.pretty_print:
            self.trace.write(f"{offset:<8} {message}\n")
            self.trace.flush()
            return

        msg = message.copy() # shallow copy
        def pop(d: dict, k, default=None):
            if k in d:
                ret = d[k]
                del d[k]
            else:
                ret = default
            return ret

        offset = self._log_offset()
        src =   pop(msg, 'from', '???')
        target =    pop(msg, 'to')
        out = f"{offset:<8} {src} -> {target} "
        msgtype =  pop(msg, 'type', '')
        if msgtype:
            out += f"[{msgtype}] "
        reqid = pop(msg, 'reqid', '')
        if reqid:
            out += f'reqid:"{reqid}" '
        sep = ""
        for k in sorted(msg):
            out += f'{sep}{k}: {msg[k]} '
            sep = ", "
        self.trace.write(out)
        self.trace.write('\n')
        self.trace.flush()

    async def handle_exec(self, message: Msg):
        """
        Handle incoming messages to launch one or more nodes. 
        """
        pat = str(message["id"]) # id can be a specific id or a pattern.
        found = False
        for id in self.config.keys():
            if re.findall(pat, id):
                found = True 
                if id in self.nodes:
                    self.write_err_trace(f"{id} already launched. Ignored exec")
                    continue
                executable = self.config[id]
                found = True
                n = self.nodes[id] = Node(id, self.handle_message, self.handle_error, self.handle_exit)
                await n.launch(executable, id, self.config_file)
        if not found:
            fatal_error(f"None of the ids in config file match '{pat}'")

    async def handle_message(self, msg: str):
        """
        Handle incoming messages from this process's stdin as well as the stdout of all launched processes.
        """
        try:
            try:
                message: Msg = json.loads(msg)
            except json.JSONDecodeError as jde:
                msg = msg.strip()
                if msg.startswith("{"):
                    fatal_error(f"Error decoding '{msg}': {jde}")
                else:
                    self.write_err_trace(msg)
                return
            self.write_trace(message)
            msg_type = message.get("type")
            if msg_type == "exec":
                await self.handle_exec(message)
            else:
                to_node_id = message["to"]
                if to_node_id == "distill":
                    return
                if to_node_id in self.nodes:
                    node = self.nodes[to_node_id]
                    if msg_type == "kill":
                        node.kill() # on exit callbacks will be triggered when process dies.
                    else:
                        await node.enq_message(message)
                else: 
                    # maybe it is a pattern
                    target_regex = to_node_id
                    found = False
                    for node_id in self.nodes.keys():
                        if re.match(target_regex, node_id):
                            found = True
                            node = self.nodes[node_id]
                            if msg_type == "kill":
                                node.kill() # on exit callbacks will be triggered when process dies.
                            else:
                                dup_message = message.copy() # shallow copy the object before enqueuing.
                                dup_message["to"] = node_id 
                                await node.enq_message(dup_message)
                    if not found:
                        self.write_err_trace(f"Dest not found. Message dropped: {msg}")
        except Exception as e:
            fatal_error(f"Exception in handle_message: while handling message '{msg}\n {traceback.format_exc()}")

    def handle_exit(self, node_id, err):
        if node_id in self.nodes:
            # Could get a notification from multiple strams
            node = self.nodes[node_id]
            #node.kill() # May be superfluous 
            del self.nodes[node_id]
            self.write_err_trace(f"INFO Exit. Reason : {err}")


    async def handle_error(self, err_msg: str):
        self.write_err_trace(err_msg)

    def quit(self):
        for n in self.nodes.values():
            n.kill()


async def main():
    """Main function to read messages and handle processes."""

    parser = argparse.ArgumentParser(description="Distill. A simple tool for teaching distributed systems")
    parser.add_argument('--config', type=str, default = "config.json", required=False, help='Distill configuration file')
    parser.add_argument('--nopp', action="store_true", required=False, help='Disable pretty print of log. Pretty print is on by default')
    parser.add_argument('--trace', type=str,  default = "-", required=False, help='File to record incoming messages | - (for stdout, default)')
    parser.add_argument('--start', type=str,  required=False, help='regex pattern. Launch at start all nodes whose ids (given in the config file) match this pattern')
    args = parser.parse_args()

    pretty_print = not args.nopp
    d = Distill(args.config, args.trace, pretty_print)

    if args.start:
        await d.handle_exec({"type" : "exec", "id" : f"{args.start}"})
            
    while True:
        line = await asyncio.get_event_loop().run_in_executor(None, sys.stdin.readline)
        if not line:
            break
        message = line.strip()
        if message:
            if message == ".q": # quit
                d.quit()
                sys.exit(0)


            await d.handle_message(message)

if __name__ == "__main__":
    asyncio.run(main())
