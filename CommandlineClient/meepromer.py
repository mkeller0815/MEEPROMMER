#Meeprommer commandline interface
#By Zack Nelson
#Project Home:
#https://github.com/mkeller0815/MEEPROMMER
#http://www.ichbinzustaendig.de/dev/meeprommer-en

import serial, sys, argparse

# Parse command line arguments
parser = argparse.ArgumentParser(
        description='Meepromer Command Line Interface',
        epilog='Read source for further information')

task = parser.add_mutually_exclusive_group()
task.add_argument('-w', '--write', dest="cmd", action="store_const",
        const="write", help='Write to EEPROM')
task.add_argument('-W', '--write_paged', dest="cmd", action="store_const",
        const="write_paged", help='Fast paged write to EEPROM')
task.add_argument('-r', '--read', dest="cmd", action="store_const",
        const="read", help='Read from EEPROM as ascii')
task.add_argument('-d', '--dump', dest="cmd", action="store_const",
        const="dump", help='Dump EEPROM to binary file')
task.add_argument('-v', '--verify', dest="cmd", action="store_const",
        const="verify", help='Compare EEPROM with file')
task.add_argument('-u', '--unlock', dest="cmd", action="store_const",
        const="unlock", help='Unlock EEPROM')
task.add_argument('-l', '--list', dest="cmd", action="store_const",
        const="list", help='List serial ports')

parser.add_argument('-a', '--address', action='store', default='0',
        help='Starting eeprom address (as hex), default 0')
parser.add_argument('-o', '--offset', action='store', default='0',
        help='Input file offset (as hex), default 0')
parser.add_argument('-b', '--bytes', action='store', default='8',
        type=int, help='Number of kBytes to r/w, default 8')
parser.add_argument('-p', '--page_size', action='store', default='32',
        type=int, help='Number of bytes per EEPROM page e.g.:'+
            'CAT28C*=32, AT28C*=64, X28C*=64, default 32')
parser.add_argument('-f', '--file', action='store',
        help='Name of data file')
parser.add_argument('-c', '--com', action='store', 
        default='COM5', help='Com port address')
parser.add_argument('-s', '--speed', action='store', 
        type=int, default='115200', help='Com port baud, default 115200')

def list_ports():
    from serial.tools import list_ports
    for x in list_ports.comports():
        print(x[0], x[1])

def dump_file():
    ser.flushInput()
    ser.write(bytes("r "+format(args.address,'04x')+" "+
        format(args.address+args.bytes*1024,'04x')+
        " 10\n", 'ascii'))
    eeprom = ser.read(args.bytes*1024)
    if(ser.read(1) != b'\0'):
        print("Error: no Ack")
        sys.exit(1)
    try:
        fo = open(args.file,'wb+')
    except OSError:
        print("Error: File cannot be opened, verify it is not in use")
        sys.exit(1)
    fo.write(eeprom)
    fo.close()

def verify():
    print("Verifying...")
    ser.flushInput()
    ser.write(bytes("r "+format(args.address,'04x')+" "+
        format(args.bytes*1024,'04x')+" 10\n", 'ascii'))
    try:
        fi = open(args.file,'rb')
    except FileNotFoundError:
        print("Error: ",args.file," not found, please select a valid file")
        sys.exit(1)
    except TypeError:
        print("Error: No file specified")
        sys.exit(1)
		
    fi.seek(args.offset)
    file = fi.read(args.bytes*1024)
    eeprom = ser.read(args.bytes*1024)
    if ser.read(1) != b'\0':
        print("Error: no EOF received")
        
    if file != eeprom:
        print("Not equal")
        n = 0
        for i in range(args.bytes*1024):
            if file[i] != eeprom[i]:
                n+=1
        print(n,"differences found")
        sys.exit(1)
    else:
        print("Ok")

def read_eeprom():
    ser.flushInput()
    ser.write(bytes("R "+format(args.address,'04x')+" "+
        format(args.address+args.bytes*1024,'04x')+
        " 10\n", 'ascii'))
    ser.readline()#remove blank starting line
    for i in range(round(args.bytes*1024/16)):
        print(ser.readline().decode('ascii').rstrip())

def write_eeprom(paged):
    import time
    
    fi = open(args.file,'rb')
    fi.seek(args.offset)
    now = time.time() #start our stopwatch
    for i in range(args.bytes): #write n blocks of 1024 bytes
        output = fi.read(1024)
        print("Writing from",format(args.address+i*1024,'04x'),
              "to",format(args.address+i*1024+1023,'04x'))
        if paged:
            ser.write(bytes("W "+format(args.address+i*1024,'04x')+
                " 0400 "+format(args.page_size,'02x')+"\n", 'ascii'))
        else:
            ser.write(bytes("w "+format(args.address+i*1024,'04x')+
                " 0400 00\n", 'ascii'))

        ser.flushInput()
        ser.write(output)
        if(ser.read(1) != b'%'):
            print("Error: no Ack")
            sys.exit(1)
    print("Wrote",args.bytes*1024,"bytes in","%.2f"%(time.time()-now),"seconds")
	
def unlock():
    print("Unlocking...")
    ser.flushInput()
    ser.write(bytes("u 0000 0000 00\n", 'ascii'))
    if ser.read(1) != b'%':
        print("Error: no ack")
        sys.exit(1)
    
args = parser.parse_args()
#convert our hex strings to ints
args.address = int(args.address,16)
args.offset = int(args.offset,16)

SERIAL_TIMEOUT = 10 #seconds
try:
    ser = serial.Serial(args.com, args.speed, timeout=SERIAL_TIMEOUT)
except serial.serialutil.SerialException:
    print("Error: Serial port is not valid, please select a valid port")
    sys.exit(1)

if args.cmd == 'write':
    write_eeprom(False)
elif args.cmd == 'write_paged':
    write_eeprom(True)
elif args.cmd == 'read':
    read_eeprom()
elif args.cmd == 'dump':
    dump_file()
elif args.cmd == 'verify':
    verify()
elif args.cmd == 'unlock':
    unlock();
elif args.cmd == 'list':
    list_ports()
    
ser.close()
sys.exit(0)
