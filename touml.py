#!/usr/bin/env python3
# Parse distill's output and produce a "UML" file that is understood by plantuml.
# Usage: touml.py -i trace.txt -o trace.puml
# 
# To visualize the uml. download the jar from plantuml.com, then
#   java -jar plantuml<version>.jar -gui, and load trace.uml in the gui

import sys
import re
import argparse
from typing import TextIO

parser = argparse.ArgumentParser(description="Process some files.")

# Add the arguments
parser.add_argument('-i', '--input', type=str, help='Input file path (default: stdin)')
parser.add_argument('-o', '--output', type=str, help='Output file path (default: stdout)')

# Parse the arguments
args = parser.parse_args()

input_file: TextIO
output_file: TextIO
# Handle input file
if args.input:
    input_file = open(args.input, 'r')
else:
    input_file = sys.stdin

# Handle output file
if args.output:
    output_file = open(args.output, 'w')
else:
    output_file = sys.stdout


with input_file:
    lines = [line for line in input_file.readlines() if not re.match("^.*(distill|testrunner)",line)]

participants = set()
for line in lines:
    m = re.findall(r"(\w+) -> ([^ ]+)", line)
    if m:
        src, target = m[0]
        participants.add(src)
        participants.add(target)

participants.discard("distill")
participants.discard("???")

plist = sorted(list(participants))

def touml(src, target, cmd, rest):
    arrow = "o-->" if "RESP" in cmd else "o->"
    if "errmsg" in rest:
        arrow += "x"
    rest = rest.replace("cp_client_conc_", "cl").replace("last_accepted_value", "aval")
    rest = rest.replace("last_accepted_ver", "aver").replace("reqid", "rid").replace('"', '')
    emit(f"{src} {arrow} {target}: {cmd} {rest}")

with output_file:
    def emit(l):
        print(l, file=output_file)

    emit("@startuml")
    for part in plist:
        if "client" in part:
            emit(f"actor {part}")
        else:
            emit(f"participant {part}")
    for line in lines:
        if not line:
            continue
        m = re.findall(r"(\w+) -> ([^ ]+) \[([A-Z_]+)\] (.*)", line)
        if m:
            src, target, cmd, rest = m[0]            
            if target in participants:
                touml(src, target, cmd, rest)
            else: # target is a regex that matches multiple participants. Need one per 
                first_time = True
                for part in plist:
                    m = re.findall(target, part)                        
                    if m:
                        if first_time:
                            first_time = False
                        else:
                            src = '&' + src
                        touml(src, m[0], cmd, rest)
        else:
            print("Ignoring ", line.strip(), file=sys.stderr)

    emit("@enduml")
    
