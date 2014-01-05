/**
 * ** project Arduino EEPROM programmer **
 * 
 * This sketch can be used to read and write data to a
 * AT28C64 or AT28C256 parallel EEPROM
 *
 * $Author: mario, edited by Zack $
 * $Date: 2013/05/05 11:01:54 $
 * $Revision: 1.3 $
 *
 * This software is freeware and can be modified, reused or thrown away without any restrictions.
 *
 * Use this code at your own risk. I'm not responsible for any bad effect or damages caused by this software!!!
 *
 **/

#define VERSIONSTRING "MEEPROMMER $Revision: 1.3 $ $Date: 12/18/2013 15:56:00 $, CMD:R,r,w,W,V, u"

// shiftOut part
#define PORTC_DS      0
#define PORTC_LATCH   1
#define PORTC_CLOCK   2

// eeprom IO lines:D0-D7 = pins2-9

// IO lines for the eeprom control
#define PORTC_CE   3
#define PORTC_OE   4
#define PORTC_WE   5

//a buffer for bytes to burn
#define BUFFERSIZE 1024 
byte buffer[BUFFERSIZE];
//command buffer for parsing commands
#define COMMANDSIZE 16
char cmdbuf[COMMANDSIZE];

unsigned int startAddress,endAddress;
unsigned int lineLength,dataLength;

//define COMMANDS
#define NOCOMMAND    0
#define VERSION      1

#define READ_HEX    10
#define READ_BIN    11

#define WRITE_PAGE  20
#define WRITE_BIN   21

#define UNLOCK      30

/*****************************************************************
 *
 *  CONTROL and DATA functions
 *
 ****************************************************************/

void data_bus_input(){
  DDRD &= 0b00000011;
  DDRB &= 0b11111100;
}

void data_bus_output(){
  DDRD |= 0b11111100;
  DDRB |= 0b00000011;
}

byte read_data_bus(){
  return ((PINB<<6)|(PIND>>2));
}

void write_data_bus(byte data){
  PORTB &= 0b11111100;  
  PORTB |= (data>>6);
  PORTD &= 0b00000011;
  PORTD |= (data<<2);
}

//shift out the given address to the 74hc595 registers
void set_address_bus(unsigned int address){
  bitClear(PORTC,PORTC_LATCH);		//disable latch line
  fastShiftOut(highByte(address));	//shift out highbyte
  fastShiftOut(lowByte(address));	//shift out lowbyte
  bitSet(PORTC,PORTC_LATCH);		//enable latch and set address
}

void fastShiftOut(byte data){
  for(int i=7; i>=0; i--){//shift MSB first
    //clear data pin after shift to prevent bleed through
    bitClear(PORTC,PORTC_DS);
    bitClear(PORTC,PORTC_CLOCK);
    if(bitRead(data,i))bitSet(PORTC,PORTC_DS);
    //register shifts bits on rising clock
    bitSet(PORTC,PORTC_CLOCK);
  }
  bitClear(PORTC,PORTC_CLOCK);
}

byte read_byte(unsigned int address){
  data_bus_input();			//set databus for reading
  bitClear(PORTC,PORTC_CE);	//enable chip select
  bitSet(PORTC,PORTC_WE);	//disable write
  set_address_bus(address);	//set address bus
  bitClear(PORTC,PORTC_OE);	//enable output
  //delay 312.5ns @ 16MHz
  __asm__("nop\n\t""nop\n\t""nop\n\t""nop\n\t""nop\n\t");
  byte data = read_data_bus();
  bitSet(PORTC,PORTC_OE);	//disable output
  bitSet(PORTC,PORTC_CE);	//enable chip select
  return data;
}

void fast_write(unsigned int address, byte data){
  bitSet(PORTC,PORTC_OE);	//first disable output
  bitSet(PORTC,PORTC_WE);	//disable write
  set_address_bus(address);	//set address bus
  data_bus_output();		//set databus to output
  write_data_bus(data);		//set data bus
  bitClear(PORTC,PORTC_CE);	//enable chip select
  bitClear(PORTC,PORTC_WE);	//enable write
  __asm__("nop\n\t""nop\n\t");  //delay 125ns @ 16MHz
  bitSet(PORTC,PORTC_WE);	//disable write
  data_bus_input();
  bitClear(PORTC,PORTC_OE);
  //delay 312.5ns @ 16MHz
  __asm__("nop\n\t""nop\n\t""nop\n\t""nop\n\t""nop\n\t");
  while(data != read_data_bus()); //poll data
  bitSet(PORTC,PORTC_OE);
  bitSet(PORTC,PORTC_CE);
}

void write_block_page(unsigned int address, byte* buffer, int len, int page){
  address &= ~(page-1);	//address must not break page boundaries
  bitSet(PORTC,PORTC_WE);	//disable write
  bitClear(PORTC,PORTC_CE);	//enable chip
  for(int n=0; n<len/page; n++){
    bitSet(PORTC,PORTC_OE);
    data_bus_output();		//set databus to output
    for(unsigned int i=n*page; i<(n+1)*page; i++){
      bitSet(PORTC,PORTC_WE);		//disable write
      set_address_bus(address+i);	//set address bus
      write_data_bus(buffer[i]);	//set data bus
      bitClear(PORTC,PORTC_WE);		//enable write
      //100ns+ tWP delay caused by for looping
    }
    bitSet(PORTC,PORTC_WE);		//write must be high for tBLC(100us)
    data_bus_input();
    bitClear(PORTC,PORTC_OE);	//enable output
    //delay 312.5ns @ 16MHz
    __asm__("nop\n\t""nop\n\t""nop\n\t""nop\n\t""nop\n\t");
    while(buffer[(n+1)*page-1] != read_data_bus()); //poll data
  }
  bitSet(PORTC,PORTC_CE);
}

/************************************************
 *
 * COMMAND and PARSING functions
 *
 *************************************************/

void readCommand(){
  int idx = 0;
  do {//read 'til linebreak or buffer is full
    if(Serial.available())cmdbuf[idx++] = Serial.read();
  } while (cmdbuf[idx-1] != '\n' && idx < COMMANDSIZE);
  for(;idx<COMMANDSIZE;idx++)cmdbuf[idx] = 0;//clear the rest of command buffer 
}

byte parseCommand(){
  //command format:C AAAA DDDD LL
  startAddress=hexWord(cmdbuf+2);	//A
  dataLength=hexWord(cmdbuf+7);	//D
  lineLength=hexByte(cmdbuf+12);	//L
  switch(cmdbuf[0]){
  case 'R':
    return(READ_HEX);
  case 'r':
    return(READ_BIN);
  case 'W':
    return(WRITE_PAGE);
  case 'w': 
    return(WRITE_BIN);
  case 'V':
    return(VERSION);
  case 'u':
    return(UNLOCK);
  default:
    return(NOCOMMAND);
  }
}

byte hexDigit(char c){ //ascii char to value
  if(c >= '0' && c <= '9')return c - '0';
  if(c >= 'a' && c <= 'f')return c - 'a' + 10;
  if(c >= 'A' && c <= 'F')return c - 'A' + 10;
  return 0;// getting here is bad: it means the character was invalid
}

byte hexByte(char* a){ //ascii byte to value
  return ((hexDigit(a[0])<<4) | hexDigit(a[1]));
}

//ascii word to value
unsigned int hexWord(char* data){
  return ((hexDigit(data[0])<<12)|
    (hexDigit(data[1])<<8)|
    (hexDigit(data[2])<<4)|
    (hexDigit(data[3]))); 
}


/************************************************
 *
 * INPUT / OUTPUT Functions
 *
 *************************************************/

void read_block(unsigned int from, unsigned int to, int linelength){
  int outcount = 0;
  //loop from "from address" to "to address" (included)
  for(unsigned int address = from; address <= to; address++){
    if(outcount == 0){
      //print out the address at the beginning of the line
      Serial.println();
      Serial.print("0x");
      printAddress(address);
      Serial.print(" : ");
    }
    //print data, separated by a space
    printByte(read_byte(address));
    Serial.print(" ");
    outcount = (++outcount % linelength);
  }
  Serial.println();
}

void read_binblock(unsigned int from, unsigned int to){
  for(unsigned int address = from; address <= to; address++){
    Serial.write(read_byte(address));
  }
  Serial.print('\0');//success return code
}  

void write_block(unsigned int address, byte* buffer, int len){
  for(unsigned int i = 0; i < len; i++){
    fast_write(address+i,buffer[i]);
  }   
}

void printAddress(unsigned int address){
  if(!(address & 0xfff0)) Serial.print("0");
  if(!(address & 0xff00)) Serial.print("0");
  if(!(address & 0xf000)) Serial.print("0");
  Serial.print(address, HEX);
}

void printByte(byte data){
  if(!(data & 0xf0)) Serial.print("0");
  Serial.print(data, HEX);  
}


/************************************************
 *
 * MAIN
 *
 *************************************************/
void setup(){
  DDRC = 0b00111111;	//set EEPROM & shiftOut Pins as output
  PORTC = 0b00111000;	//set EEPROM Pins high
  //Serial.begin(57600);	//set speed of serial connection
  Serial.begin(115200)
}

void loop(){
  int bytes = 0;
  readCommand();
  switch(parseCommand()){
  case READ_HEX:
    if(lineLength==0) lineLength=32; //default
    endAddress = startAddress + dataLength -1;
    read_block(startAddress,endAddress,lineLength);
    Serial.println('%');//success return code
    break;
  case READ_BIN:
    endAddress = startAddress + dataLength -1;
    read_binblock(startAddress,endAddress);
    break;
  case WRITE_PAGE:
    if(dataLength > 1024) dataLength = 1024;
    while(bytes < dataLength)if(Serial.available())buffer[bytes++] = Serial.read();
    if(lineLength==0) lineLength=32; //page size
    write_block_page(startAddress,buffer,dataLength,lineLength);
    Serial.println('%');
    break;
  case WRITE_BIN:
    if(dataLength > 1024) dataLength = 1024; //1024 is max
    while(bytes < dataLength)if(Serial.available())buffer[bytes++] = Serial.read();
    write_block(startAddress,buffer,dataLength);
    Serial.println('%');
    break;
  case VERSION:
    Serial.println(VERSIONSTRING);
    break;
  case UNLOCK:
    fast_write(0x5555,0xAA);
    fast_write(0x2AAA,0x55);
    fast_write(0x5555,0x80);
    fast_write(0x5555,0xAA);
    fast_write(0x2AAA,0x55);
    fast_write(0x5555,0x20);
    Serial.print('%');//success return code
    break;
  }
}
