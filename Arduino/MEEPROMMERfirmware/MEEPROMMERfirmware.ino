/**
 * ** project Arduino EEPROM programmer **
 * 
 * This sketch can be used to read and write data to a
 * AT28C64 or AT28C256 parallel EEPROM
 *
 * $Author: mario $
 * $Date: 2013/05/05 11:01:54 $
 * $Revision: 1.2 $
 *
 * This software is freeware and can be modified, reused or thrown away without any restrictions.
 *
 * Use this code at your own risk. I'm not responsible for any bad effect or damages caused by this software!!!
 *
 **/

#define VERSIONSTRING "MEEPROMMER $Revision: 1.2 $ $Date: 2013/05/05 11:01:54 $, CMD:B,b,w,W,V"

// shiftOut part
#define DS      A0
#define LATCH   A1
#define CLOCK   A2

#define PORTC_DS      0
#define PORTC_LATCH   1
#define PORTC_CLOCK   2


// eeprom stuff
// define the IO lines for the data - bus
#define D0 2
#define D1 3
#define D2 4
#define D3 5
#define D4 6
#define D5 7
#define D6 8
#define D7 9

// define the IO lines for the eeprom control
#define CE A3
#define OE A4
#define WE A5
#define PORTC_CE   3
#define PORTC_OE   4
#define PORTC_WE   5

//a buffer for bytes to burn
#define BUFFERSIZE 1024 
byte buffer[BUFFERSIZE];
//command buffer for parsing commands
#define COMMANDSIZE 32
char cmdbuf[COMMANDSIZE];

unsigned int startAddress,endAddress;
unsigned int lineLength,dataLength;

//define COMMANDS
#define NOCOMMAND    0
#define VERSION      1

#define READ_HEX    10
#define READ_BIN    11
#define READ_ITL    12

#define WRITE_HEX   20
#define WRITE_BIN   21
#define WRITE_ITL   22


/*****************************************************************
 *
 *  CONTROL and DATA functions
 *
 ****************************************************************/

// switch IO lines of databus to INPUT state
void	    data_bus_input() {
  pinMode(D0, INPUT);
  pinMode(D1, INPUT);
  pinMode(D2, INPUT);
  pinMode(D3, INPUT);
  pinMode(D4, INPUT);
  pinMode(D5, INPUT);
  pinMode(D6, INPUT);
  pinMode(D7, INPUT);
}

//switch IO lines of databus to OUTPUT state
void data_bus_output() {
  pinMode(D0, OUTPUT);
  pinMode(D1, OUTPUT);
  pinMode(D2, OUTPUT);
  pinMode(D3, OUTPUT);
  pinMode(D4, OUTPUT);
  pinMode(D5, OUTPUT);
  pinMode(D6, OUTPUT);
  pinMode(D7, OUTPUT);
}

//set databus to input and read a complete byte from the bus
//be sure to set data_bus to input before
byte read_data_bus()
{
  return ((digitalRead(D7) << 7) +
    (digitalRead(D6) << 6) +
    (digitalRead(D5) << 5) +
    (digitalRead(D4) << 4) +
    (digitalRead(D3) << 3) +
    (digitalRead(D2) << 2) +
    (digitalRead(D1) << 1) +
    digitalRead(D0));

}


//write a byte to the data bus
//be sure to set data_bus to output before
void write_data_bus(byte data)
{
  //2 bits belong to PORTB and have to be set separtely 
  digitalWrite(D6, (data >> 6) & 0x01);
  digitalWrite(D7, (data >> 7) & 0x01);
  //bit 0 to 6 belong to bit 2 to 8 of PORTD
  PORTD = PIND | ( data << 2 );
}

//shift out the given address to the 74hc595 registers
void set_address_bus(unsigned int address)
{

  //get high - byte of 16 bit address
  byte hi = address >> 8;
  //get low - byte of 16 bit address
  byte low = address & 0xff;

  //disable latch line
  bitClear(PORTC,PORTC_LATCH);

  //shift out highbyte
  fastShiftOut(low);
  //shift out lowbyte
  fastShiftOut(hi);

  //enable latch and set address
  bitSet(PORTC,PORTC_LATCH);

}

//faster shiftOut function then normal IDE function (about 4 times)
void fastShiftOut(byte data) {
  //clear data pin
  bitClear(PORTC,PORTC_DS);
  //Send each bit of the myDataOut byte MSBFIRST
  for (int i=7; i>=0; i--)  {
    bitClear(PORTC,PORTC_CLOCK);
    //--- Turn data on or off based on value of bit
    if ( bitRead(data,i) == 1) {
      bitSet(PORTC,PORTC_DS);
    }
    else {      
      bitClear(PORTC,PORTC_DS);
    }
    //register shifts bits on upstroke of clock pin  
    bitSet(PORTC,PORTC_CLOCK);
    //zero the data pin after shift to prevent bleed through
    bitClear(PORTC,PORTC_DS);
  }
  //stop shifting
  bitClear(PORTC,PORTC_CLOCK);
}

//short function to set the OE(output enable line of the eeprom)
// attention, this line is LOW - active
void set_oe (byte state)
{
  digitalWrite(OE, state);
}

//short function to set the CE(chip enable line of the eeprom)
// attention, this line is LOW - active
void set_ce (byte state)
{
  digitalWrite(CE, state);
}

//short function to set the WE(write enable line of the eeprom)
// attention, this line is LOW - active
void set_we (byte state)
{
  digitalWrite(WE, state);
}

//highlevel function to read a byte from a given address
byte read_byte(unsigned int address)
{
  byte	    data = 0;
  //set databus for reading
  data_bus_input();
  //first disbale output
  set_oe(HIGH);
  //enable chip select
  set_ce(LOW);
  //disable write
  set_we(HIGH);
  //set address bus
  set_address_bus(address);
  //enable output
  set_oe(LOW);
  data = read_data_bus();

  //disable output
  set_oe(HIGH);

  return data;

}


//highlevel function to write a byte to a given address
//this function uses /DATA polling to get the end of the
//write cycle. This is much faster then waiting 10ms
void fast_write(unsigned int address, byte data)
{
  byte cyclecount=0;

  //first disbale output
  set_oe(HIGH);

  //disable write
  set_we(HIGH);

  //set address bus
  set_address_bus(address);

  //set databus to output
  data_bus_output();

  //set data bus
  write_data_bus(data);

  //enable chip select
  set_ce(LOW);

  //wait some time to finish writing
  delayMicroseconds(1);

  //enable write
  set_we(LOW);

  //wait some time to finish writing
  delayMicroseconds(1);

  //disable write
  set_we(HIGH);

  data_bus_input();

  set_oe(LOW);

  while(data != read_data_bus()) {
    cyclecount++;
  };

  set_oe(HIGH);
  set_ce(HIGH);

}



/************************************************
 *
 * COMMAND and PARSING functions
 *
 *************************************************/

//waits for a string submitted via serial connection
//returns only if linebreak is sent or the buffer is filled
void readCommand() {
  //first clear command buffer
  for(int i=0; i< COMMANDSIZE;i++) cmdbuf[i] = 0;
  //initialize variables
  char c = ' ';
  int idx = 0;
  //now read serial data until linebreak or buffer is full
  do {
    if(Serial.available()) {
      c = Serial.read();
      cmdbuf[idx++] = c;
    }
  } 
  while (c != '\n' && idx < (COMMANDSIZE)); //save the last '\0' for string end
  //change last newline to '\0' termination
  cmdbuf[idx - 1] = 0;
}

//parse the given command by separating command character and parameters
//at the moment only 5 commands are supported
byte parseCommand() {
  //set ',' to '\0' terminator (command string has a fixed strucure)
  //first string is the command character
  cmdbuf[1]  = 0;
  //second string is startaddress (4 bytes)
  cmdbuf[6]  = 0;
  //third string is endaddress (4 bytes)
  cmdbuf[11] = 0;
  //fourth string is length (2 bytes)
  cmdbuf[14] = 0;
  startAddress=hexWord((cmdbuf+2));
  dataLength=hexWord((cmdbuf+7));
  lineLength=hexByte(cmdbuf+12);
  byte retval = 0;
  switch(cmdbuf[0]) {
  case 'R':
    retval = READ_HEX;
    break;
  case 'r':
    retval = READ_BIN;
    break;
  case 'W':
    retval = WRITE_HEX;
    break;
  case 'w': 
    retval = WRITE_BIN;
    break;
  case 'V':
    retval = VERSION; 
    break;
  default:
    retval = NOCOMMAND;
    break;
  }

  return retval;
}

/************************************************************
 * convert a single hex digit (0-9,a-f) to byte
 * @param char c single character (digit)
 * @return byte represented by the digit 
 ************************************************************/
byte hexDigit(char c)
{
  if (c >= '0' && c <= '9') {
    return c - '0';
  } 
  else if (c >= 'a' && c <= 'f') {
    return c - 'a' + 10;
  } 
  else if (c >= 'A' && c <= 'F') {
    return c - 'A' + 10;
  } 
  else {
    return 0;   // getting here is bad: it means the character was invalid
  }
}

/************************************************************
 * convert a hex byte (00 - ff) to byte
 * @param c-string with the hex value of the byte
 * @return byte represented by the digits 
 ************************************************************/
byte hexByte(char* a)
{
  return ((hexDigit(a[0])*16) + hexDigit(a[1]));
}

/************************************************************
 * convert a hex word (0000 - ffff) to unsigned int
 * @param c-string with the hex value of the word
 * @return unsigned int represented by the digits 
 ************************************************************/
unsigned int hexWord(char* data) {
  return ((hexDigit(data[0])*4096)+
    (hexDigit(data[1])*256)+
    (hexDigit(data[2])*16)+
    (hexDigit(data[3]))); 
}


/************************************************
 *
 * INPUT / OUTPUT Functions
 *
 *************************************************/


/**
 * read a data block from eeprom and write out a hex dump 
 * of the data to serial connection
 * @param from       start address to read fromm
 * @param to         last address to read from
 * @param linelength how many hex values are written in one line
 **/
void read_block(unsigned int from, unsigned int to, int linelength)
{
  //count the number fo values that are already printed out on the
  //current line
  int		    outcount = 0;
  //loop from "from address" to "to address" (included)
  for (unsigned int address = from; address <= to; address++) {
    if (outcount == 0) {
      //print out the address at the beginning of the line
      Serial.println();
      Serial.print("0x");
      printAddress(address);
      Serial.print(" : ");
    }
    //print data, separated by a space
    byte data = read_byte(address);
    printByte(data);
    Serial.print(" ");
    outcount = (++outcount % linelength);

  }
  //print a newline after the last data line
  Serial.println();

}

/**
 * read a data block from eeprom and write out the binary data 
 * to the serial connection
 * @param from       start address to read fromm
 * @param to         last address to read from
 **/
void read_binblock(unsigned int from, unsigned int to) {
  for (unsigned int address = from; address <= to; address++) {
    Serial.write(read_byte(address));
  }
  //print out an additional 0-byte as success return code
  Serial.print('\0');
  
}  
  
/**
 * write a data block to the eeprom
 * @param address  startaddress to write on eeprom
 * @param buffer   data buffer to get the data from
 * @param len      number of bytes to be written
 **/
void write_block(unsigned int address, byte* buffer, int len) {
  for (unsigned int i = 0; i < len; i++) {
    fast_write(address+i,buffer[i]);
  }   
}


/**
 * print out a 16 bit word as 4 character hex value
 **/
void printAddress(unsigned int address) {
  if(address < 0x0010) Serial.print("0");
  if(address < 0x0100) Serial.print("0");
  if(address < 0x1000) Serial.print("0");
  Serial.print(address, HEX);

}

/**
 * print out a byte as 2 character hex value
 **/
void printByte(byte data) {
  if(data < 0x10) Serial.print("0");
  Serial.print(data, HEX);  
}





/************************************************
 *
 * MAIN
 *
 *************************************************/
void setup() {
  //define the shiuftOut Pins as output
  pinMode(DS, OUTPUT);
  pinMode(LATCH, OUTPUT);
  pinMode(CLOCK, OUTPUT);

  //define the EEPROM Pins as output
  // take care that they are HIGH
  digitalWrite(OE, HIGH);
  pinMode(OE, OUTPUT);
  digitalWrite(CE, HIGH);
  pinMode(CE, OUTPUT);
  digitalWrite(WE, HIGH);
  pinMode(WE, OUTPUT);

  //set speed of serial connection
  Serial.begin(57600);
}

/**
 * main loop, that runs invinite times, parsing a given command and 
 * executing the given read or write requestes.
 **/
void loop() {
  readCommand();
  byte cmd = parseCommand();
  int bytes = 0;
  switch(cmd) {
  case READ_HEX:
    //set a default if needed to prevent infinite loop
    if(lineLength==0) lineLength=32;
    endAddress = startAddress + dataLength -1;
    read_block(startAddress,endAddress,lineLength);
    Serial.println('%');
    break;
  case READ_BIN:
    endAddress = startAddress + dataLength -1;
    read_binblock(startAddress,endAddress);
    break;
  case READ_ITL:
    break;
  case WRITE_BIN:
    //take care for max buffer size
    if(dataLength > 1024) dataLength = 1024;
    endAddress = startAddress + dataLength -1;
    while(bytes < dataLength) {
      if(Serial.available()) buffer[bytes++] = Serial.read();
    }    
    write_block(startAddress,buffer,dataLength);
    Serial.println('%');
    
    break;
  case WRITE_HEX:
    break;
  case WRITE_ITL:
    break;
  case VERSION:
    Serial.println(VERSIONSTRING);
    break;
  default:
    break;    
  }


}





