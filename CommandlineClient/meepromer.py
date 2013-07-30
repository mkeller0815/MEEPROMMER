#!/usr/bin/env python
# vim:fileencoding=ISO-8859-1
# 
# MEEPROMER - Mario's EEPROMer Python script
# Scot W. Stevenson <scot.stevenson@gmail.com>
# Version 0.1 ALPHA  11. April 2013
#
# TODO dislamer
# TODO license
# 
# Requires Python 2.7 or later; will not work with Python 3.0 or later
#
# TODO expand doc string below

"""Simple command line tool for connecting to the meeprom EEPROMer.

Packages required that are not part of the Python Standard Library:
    PySerial
"""

# Package imports. PySerial is only imported if needed (see below)

import argparse 
import sys

# Constants

BAUDRATE = 9600
PROTOCOL_VERSION = None         # TODO add protocol version for checks
SERIAL_PORT = '/dev/cu.usbserial-FTAKHP5D'
SERIAL_TIMEOUT = 1              # in seconds


# ----------------------------------------------------------------------
# Parse command line arguments

parser = argparse.ArgumentParser(
        description='Meepromer Command Line Interface',
        epilog='Read source for further information')

task = parser.add_mutually_exclusive_group()
task.add_argument('-w', '--write', dest="cmd", action="store_const",
        const="write", help='Write to EEPROM')
task.add_argument('-r', '--read', dest="cmd", action="store_const",
        const="read", help='Read from EEPROM (binary)')
task.add_argument('-d', '--dump', dest="cmd", action="store_const",
        const="dump", help='Read from EEPROM (ascii)')
task.add_argument('-D', '--diff', dest="cmd", action="store_const",
        const="diff", help='Write bytes that differ')
task.add_argument('-v', '--verify', dest="cmd", action="store_const",
        const="verify", help='Compare EEPROM with file')

parser.add_argument('-a', '--address', nargs=1, action='store',
        default='0000', help='Starting address (as hex), default 0000')
parser.add_argument('-b', '--bytes', nargs=1, action='store',
        default='1', help='Number of bytes to read or write, default 1')
parser.add_argument('-f', '--file', nargs=1, help='Name of data file',
        action='store', type=file)
parser.add_argument('-i', '--info', action="store_true",
        help='Display status of EEPROMer and quit')
parser.add_argument('-s', '--serial', nargs=1, action='store', 
        help='Serial port address')
parser.add_argument('-V', '--version', action='version',
        version='%(prog)s ALPHA 0.1')

args = parser.parse_args()


# Quit ALPHA version cleanly
# TODO replace 

sys.exit(0)

# ----------------------------------------------------------------------
# Setup serial port

def _setup_serial():
    """Set up the serial port. Currently only tested on Mac OS X 10.8"""

    try:
        import serial
    except ImportError:
        print "Program requires the PySerial module. Install with"
        print "    sudo easy_install PySerial [for Mac and Linux]"
        print "and try again."
        sys.exit(1)

    ser = serial.Serial(SERIAL_PORT, BAUDRATE, timeout=1)



