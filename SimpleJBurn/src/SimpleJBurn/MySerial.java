/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package SimpleJBurn;

import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.HashSet;

/**
 *
 * @author mario
 */
public class MySerial {

    InputStream in = null;
    OutputStream out = null;
    SerialPort serialPort = null;

    public void MySerial() {
        in = null;
        out = null;
        serialPort = null;
    }
    /**
     * @return A HashSet containing the CommPortIdentifier for all serial ports
     * that are not currently being used.
     */
    public static HashSet<CommPortIdentifier> getAvailableSerialPorts() {
        HashSet<CommPortIdentifier> h = new HashSet<CommPortIdentifier>();
        Enumeration thePorts = CommPortIdentifier.getPortIdentifiers();
        while (thePorts.hasMoreElements()) {
            CommPortIdentifier com = (CommPortIdentifier) thePorts.nextElement();
            if (com.getPortType() == CommPortIdentifier.PORT_SERIAL) {
                h.add(com);
            }
        }
        return h;
    }

    void connect(String portName, int speed) throws Exception {
        CommPortIdentifier portIdentifier = CommPortIdentifier.getPortIdentifier(portName);
        if (portIdentifier.isCurrentlyOwned()) {
            throw (new Exception("Error: Port is currently in use"));
        } else {
            CommPort commPort = portIdentifier.open("MEEPROMMER", 2000);
            if (commPort instanceof SerialPort) {
                serialPort = (SerialPort) commPort;
                serialPort.setSerialPortParams(speed, SerialPort.DATABITS_8,
                        SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
                in = serialPort.getInputStream();
                out = serialPort.getOutputStream();

            } else {
                throw (new Exception("Error: Only serial ports are handled by this example."));
            }
        }
    }

    void disconnect() throws Exception {
        // do io streams need to be closed first?
        if (in != null) {
            in.close();
        }
        if (out != null) {
            out.close();
        }
        in = null;
        out = null;

        if (serialPort != null) {
            serialPort.close(); // close the port
        }
        serialPort = null;
    }
    
    boolean isConnected() {
        return (serialPort != null);
    }
}
