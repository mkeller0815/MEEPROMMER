/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package SimpleJBurn;

import gnu.io.CommPortIdentifier;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Toolkit;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashSet;
import java.util.Iterator;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;

/**
 *
 * @author mario
 */
public class MainFrame extends javax.swing.JFrame implements PropertyChangeListener {

    static private final String revision = "$Revision: 1.5 $";
    static private final String date = "$Date: 2013/07/19 05:44:46 $";
    //used to end a line in the output window
    static private final String newline = "\n";
    private static final String[] EEPROMTYPES = {"28C64  (8k) ", "28C128 (16k)", "28C256 (32k)"};
    private static final String[] OFFSETS = {"---", " 1k", " 2k", " 3k", " 4k", " 5k", " 6k",
        " 7k", " 8k", " 9k", "10k", "11k", "12k", "13k", "14k", "15k", "16k", "17k",
        "18k", "19k", "20k", "21k", "22k", "23k", "24k", "25k", "26k", "27k",
        "28k", "29k", "30k", "31k"};
    private ReadTask readTask;
    private WriteTask writeTask;
    MySerial mySerial = new MySerial();
    String selectedComPort;
    byte[] data = new byte[32768];
    byte[] eeprom = new byte[32768];
    int maxAddress = 8192;
    int offset = 0;
    long filesize = 0;
    int readwrite = 0;

    class ReadTask extends SwingWorker<Void, Void> {

        public int done = 0;
        boolean diff = false;
        long start, end = 0;
        int readProgress = 0;

        public ReadTask(boolean d) {
            this.diff = d;
        }

        /*
         * Main task. Executed in background thread.
         */
        @Override
        public Void doInBackground() {
            //check if eeprom should be read or written
            try {
            try {
                //remove old data from input stream to prevent them "poisening" our
                //data
                mySerial.in.skip(mySerial.in.available());
                //take time to read the eeprom
                start = System.currentTimeMillis();
                BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(mySerial.out));
                String line = "";


                log.insertString(log.getLength(), "sending command." + newline,null);
                bw.write("r,0000," + Utility.wordToHex(maxAddress) + ",20" + newline);
                bw.flush();
                log.insertString(log.getLength(), "command sent." + newline,null);
                log.insertString(log.getLength(), "trying to read." + newline,null);
                int counter = 0;
                byte c = ' ';
                do {
                    eeprom[counter++] = (byte) mySerial.in.read();
                    if (counter % 100 == 0) {
                        readProgress = 100 * counter / maxAddress;
                        setProgress(readProgress);
                    }
                } while (counter < maxAddress);
                end = System.currentTimeMillis();
                setProgress(100);


            } catch (Exception e) {
                log.insertString(log.getLength(), "Error: " + e.getMessage() + newline,null);
            }
        } catch (BadLocationException e) {
            System.err.println("Output Error");
        }



            /**
             * Random random = new Random(); int progress = 0; int steps = 100 /
             * (maxAddress / 1024); //Initialize progress property.
             * setProgress(0); for (int i = offset; i < maxAddress; i += 1024) {
             * try { Thread.sleep(random.nextInt(300)); } catch
             * (InterruptedException ignore) { } progress = i * 100 /
             * maxAddress; done = i; setProgress(Math.min(progress, 100)); }
             * setProgress(100); return null; *
             */
            return null;
        }

        /*
         * Executed in event dispatching thread
         */
        @Override
        public void done() {
            try{ 
                Toolkit.getDefaultToolkit().beep();
            clearButton.setEnabled(true);
            writeButton.setEnabled(true);
            readButton.setEnabled(true);
            setCursor(null); //turn off the wait cursor
            log.insertString(log.getLength(), maxAddress + " bytes read in " + (float) (end - start) / 1000
                    + " seconds " + newline,null);
            textPane.setCaretPosition(textPane.getDocument().getLength());
            if (this.diff) {
                log.insertString(log.getLength(), "Checking difference between loaded ROM file and data on EEPROM"
                        + newline,null);
                int byteCount = 0;
                //this.readEEPROM();
                for (int i = 0; i < filesize; i++) {
                    if (data[i] != eeprom[i + offset]) {
                        byteCount++;
                    }
                }
                log.insertString(log.getLength(), filesize + " bytes checked from 0x" + Utility.wordToHex(offset)
                        + " to 0x" + Utility.wordToHex(offset + (int) filesize - 1) + ", " + byteCount
                        + " byte are different." + newline,null);
            }
        } catch (BadLocationException e) {
            System.err.println("Output Error");
        }

        }
    }

    class WriteTask extends SwingWorker<Void, Void> {

        public int done = 0;
        int len;
        int address;
        long start, end = 0;
        int writeProgress = 0;

        public WriteTask(int a, int l) {
            this.len = l;
            this.address = a;
        }
        /*
         * Main task. Executed in background thread.
         */

        @Override
        public Void doInBackground() {
            try{
            try {
                //take time to read the eeprom
                start = System.currentTimeMillis();
                BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(mySerial.out));
                String line = "";
                log.insertString(log.getLength(), "sending command." + newline,null);
                for (int i = 0; i < len; i += 1024) {
                    bw.write("w," + Utility.wordToHex(address + i) + "," + Utility.wordToHex(1024) + newline);
                    bw.flush();
                    writeProgress = i * 100 / len;
                    setProgress(writeProgress);
                    mySerial.out.write(data, i, 1024);
                    log.insertString(log.getLength(), "wrote data from 0x" + Utility.wordToHex(address + i)
                            + " to 0x" + Utility.wordToHex(address + i + 1023) + newline,null);
                    byte c = ' ';
                    do {
                        c = (byte) mySerial.in.read();
                    } while (c != '%');

                }
                end = System.currentTimeMillis();
                setProgress(100);



            } catch (Exception e) {
                log.insertString(log.getLength(), "Error: " + e.getMessage() + newline,null);
            }

            /**
             * Random random = new Random(); int progress = 0; int steps = 100 /
             * (maxAddress / 1024); //Initialize progress property.
             * setProgress(0); for (int i = offset; i < maxAddress; i += 1024) {
             * try { Thread.sleep(random.nextInt(300)); } catch
             * (InterruptedException ignore) { } progress = i * 100 /
             * maxAddress; done = i; setProgress(Math.min(progress, 100)); }
             * setProgress(100); return null; *
             */
        } catch (BadLocationException e) {
            System.err.println("Output Error");
        }
            return null;
            
        }

        /*
         * Executed in event dispatching thread
         */
        @Override
        public void done() {
            Toolkit.getDefaultToolkit().beep();
            clearButton.setEnabled(true);
            writeButton.setEnabled(true);
            readButton.setEnabled(true);
            setCursor(null); //turn off the wait cursor
            try{
            log.insertString(log.getLength(), "data sent." + newline,null);

            log.insertString(log.getLength(), "wrote " + len + " bytes from 0x"
                    + Utility.wordToHex(address) + " to 0x"
                    + Utility.wordToHex(address + (int) len - 1) + " in "
                    + (float) (end - start) / 1000
                    + " seconds " + newline,null);
        } catch (BadLocationException e) {
            System.err.println("Output Error");
        }

        }
    }

    /**
     * Invoked when task's progress property changes.
     */
    public void propertyChange(PropertyChangeEvent evt) {
        if ("progress" == evt.getPropertyName()) {
            int progress = (Integer) evt.getNewValue();
            progressBar.setValue(progress);
            //log.replaceSelection(readTask.getProgress() + "% completed, " + task.done + " bytes written " + newline,null);
        }
    }

    /**
     * Creates new form MainFrame
     */
    public MainFrame() {

        initComponents();

        //Create a file chooser
        fc = new JFileChooser();

        StyleContext sc = StyleContext.getDefaultStyleContext();
        AttributeSet aset = sc.addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Foreground, Color.BLACK);

        aset = sc.addAttribute(aset, StyleConstants.FontFamily, "Lucida Console");
        aset = sc.addAttribute(aset, StyleConstants.Alignment, StyleConstants.ALIGN_JUSTIFIED);

        int len = textPane.getDocument().getLength();
        textPane.setCaretPosition(len);
        textPane.setCharacterAttributes(aset, false);


    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel1 = new javax.swing.JPanel();
        progressBar = new javax.swing.JProgressBar();
        writeButton = new javax.swing.JButton();
        serialSelect = new javax.swing.JComboBox();
        readButton = new javax.swing.JButton();
        clearButton = new javax.swing.JButton();
        loadButton = new javax.swing.JButton();
        saveButton = new javax.swing.JButton();
        versionButton = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        eepromTypeSelect = new javax.swing.JComboBox();
        jLabel3 = new javax.swing.JLabel();
        offsetSelect = new javax.swing.JComboBox();
        showImageButton = new javax.swing.JButton();
        showDataButton = new javax.swing.JButton();
        showDiffButton = new javax.swing.JButton();
        jScrollPane2 = new javax.swing.JScrollPane();
        textPane = new javax.swing.JTextPane();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("Simple JBurn 2");
        setBounds(new java.awt.Rectangle(0, 0, 0, 0));
        setMinimumSize(new java.awt.Dimension(800, 600));

        writeButton.setText("Write EEPROM");
        writeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                writeButtonActionPerformed(evt);
            }
        });

        HashSet h = mySerial.getAvailableSerialPorts();

        Iterator<CommPortIdentifier> thePorts = h.iterator();
        while (thePorts.hasNext()) {
            serialSelect.addItem(thePorts.next().getName());
        }

        serialSelect.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                serialSelectActionPerformed(evt);
            }
        });

        readButton.setText("Read EEPROM");
        readButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                readButtonActionPerformed(evt);
            }
        });

        clearButton.setText("Clear EEPROM");
        clearButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                clearButtonActionPerformed(evt);
            }
        });

        loadButton.setText("Load Image");
        loadButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadButtonActionPerformed(evt);
            }
        });

        saveButton.setText("Save Image");
        saveButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveButtonActionPerformed(evt);
            }
        });

        versionButton.setText("Version");
        versionButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                versionButtonActionPerformed(evt);
            }
        });

        jLabel1.setText("Serial :");

        jLabel2.setText("EEPROM Type :");

        eepromTypeSelect.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "28C64  (8k) ", "28C128 (16k)", "28C256 (32k)" }));
        eepromTypeSelect.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                eepromTypeSelectActionPerformed(evt);
            }
        });

        jLabel3.setText("Offset :");

        offsetSelect.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "---", " 1k", " 2k", " 3k", " 4k", " 5k", " 6k", " 7k", " 8k", " 9k", "10k", "11k", "12k", "13k", "14k", "15k", "16k", "17k", "18k", "19k", "20k", "21k", "22k", "23k", "24k", "25k", "26k", "27k", "28k", "29k", "30k", "31k" }));
        offsetSelect.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                offsetSelectActionPerformed(evt);
            }
        });

        showImageButton.setText("Show Image");
        showImageButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showImageButtonActionPerformed(evt);
            }
        });

        showDataButton.setText("Show Data");
        showDataButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showDataButtonActionPerformed(evt);
            }
        });

        showDiffButton.setText("Show Diff");
        showDiffButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showDiffButtonActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout jPanel1Layout = new org.jdesktop.layout.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1Layout.createSequentialGroup()
                .add(25, 25, 25)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jPanel1Layout.createSequentialGroup()
                        .add(clearButton)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(readButton)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(writeButton)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .add(progressBar, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 298, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .add(jPanel1Layout.createSequentialGroup()
                        .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(jPanel1Layout.createSequentialGroup()
                                .add(jLabel1)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(serialSelect, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 245, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                                .add(jLabel2)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                                .add(eepromTypeSelect, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 135, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .add(jLabel3)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED))
                            .add(jPanel1Layout.createSequentialGroup()
                                .add(loadButton)
                                .add(18, 18, 18)
                                .add(saveButton)
                                .add(18, 18, 18)
                                .add(versionButton)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .add(showImageButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 125, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                .add(18, 18, 18)
                                .add(showDataButton)
                                .add(24, 24, 24)))
                        .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(offsetSelect, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(showDiffButton))
                        .add(18, 18, 18))))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, jPanel1Layout.createSequentialGroup()
                .addContainerGap(12, Short.MAX_VALUE)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                        .add(showDataButton)
                        .add(showDiffButton)
                        .add(showImageButton))
                    .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                        .add(loadButton)
                        .add(saveButton)
                        .add(versionButton)))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                        .add(serialSelect, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .add(jLabel1))
                    .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                        .add(offsetSelect, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .add(jLabel3))
                    .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                        .add(eepromTypeSelect, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .add(jLabel2)))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, jPanel1Layout.createSequentialGroup()
                        .add(progressBar, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .add(5, 5, 5))
                    .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                        .add(writeButton)
                        .add(readButton)
                        .add(clearButton))))
        );

        textPane.setEditable(false);
        textPane.setFont(new java.awt.Font("Monospaced", 0, 12)); // NOI18N
        jScrollPane2.setViewportView(textPane);
        log = textPane.getStyledDocument();

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jScrollPane2, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 800, Short.MAX_VALUE)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, jPanel1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(jPanel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jScrollPane2, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 493, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void appendToLog(String text) {
        try {
            log.insertString(log.getLength(),text,null);
        } catch (BadLocationException e) {
         System.err.println("Output Error" + e.getMessage() + newline);   
        }
    }
    
    private void serialSelectActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_serialSelectActionPerformed
        appendToLog("now selected: " + serialSelect.getSelectedItem() + newline);
        selectedComPort = (String) serialSelect.getSelectedItem();
        try {
            mySerial.disconnect();
            mySerial.connect(selectedComPort, 57600);
            log.insertString(log.getLength(), selectedComPort + " is now connected." + newline,null);
            Thread.sleep(1000);
        } catch (Exception ex) {
           appendToLog( "Error : " + ex.getMessage() + newline);
        }

    }//GEN-LAST:event_serialSelectActionPerformed

    private void writeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_writeButtonActionPerformed
        writeButton.setEnabled(false);
        progressBar.setValue(0);
        readwrite = 1;
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        //Instances of javax.swing.SwingWorker are not reusuable, so
        //we create new instances as needed.
        writeTask = new WriteTask(offset, (int) filesize);
        writeTask.addPropertyChangeListener(this);
        writeTask.execute();

    }//GEN-LAST:event_writeButtonActionPerformed

    private void loadButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadButtonActionPerformed
        int returnVal = fc.showOpenDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            //This is where a real application would open the file.
            appendToLog( "Opening: " + file.getAbsolutePath() + "."
                    + newline);
            if (file.length() <= 32768) {
                loadFile(file);
            } else {
                appendToLog( "Error: " + file.getName()
                        + "is too big to load.");
            }
        } else {
            appendToLog( "Open command cancelled by user." + newline);
        }

    }//GEN-LAST:event_loadButtonActionPerformed

    private void eepromTypeSelectActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_eepromTypeSelectActionPerformed
        switch (eepromTypeSelect.getSelectedIndex()) {
            case 0:
                maxAddress = 8192;
                break;
            case 1:
                maxAddress = 16384;
                break;
            case 2:
                maxAddress = 32768;
                break;
            default:
                maxAddress = 8192;
                break;
        }

        int len = textPane.getDocument().getLength();
        textPane.setCaretPosition(len);
        appendToLog( "now selected: " + eepromTypeSelect.getSelectedItem()
                + ", address range = 0x0000 to 0x"
                + Utility.wordToHex(maxAddress - 1) + newline);
       
    }//GEN-LAST:event_eepromTypeSelectActionPerformed

    private void offsetSelectActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_offsetSelectActionPerformed
        offset = offsetSelect.getSelectedIndex() * 1024;

        appendToLog("Offset is now set to : " + offsetSelect.getSelectedItem() + newline);
        appendToLog("data will be written from 0x" + Utility.wordToHex(offset) + newline);

        if (offset + filesize > maxAddress) {
            JOptionPane.showMessageDialog(this, "The offset you choose will cause the current file not to fit in the choosen EEPROM anymore", "Warning", JOptionPane.WARNING_MESSAGE);
            textPane.setForeground(Color.red);
            appendToLog("WARNING!! The offset you choose will cause the current file not to fit in the choosen EEPROM anymore " + newline);
            textPane.setForeground(Color.black);

            textPane.setCaretPosition(textPane.getDocument().getLength());
        }
        
    }//GEN-LAST:event_offsetSelectActionPerformed

    private void versionButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_versionButtonActionPerformed
            appendToLog("Simple JBurn - Revision : " + revision + ", " + date + newline);
        if (mySerial.isConnected()) {
            try {
                mySerial.out.write('V');
                mySerial.out.write('\n');
                String line = "";
                byte c = ' ';
                do {
                    c = (byte) mySerial.in.read();
                    line = line + (char) c;
                    if (c == '\n') {
                        appendToLog( line);
                        line = "";
                    }
                } while (c != '\n');
            } catch (Exception e) {
                appendToLog("Error: " + e.getMessage() + newline);
            }
        } else {
            appendToLog("Error: Not connected to any Programmer!" + newline);
        }
    }//GEN-LAST:event_versionButtonActionPerformed

    private void saveButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveButtonActionPerformed
            int returnVal = fc.showSaveDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            //This is where a real application would open the file.
            appendToLog("Saving: " + file.getAbsolutePath() + "."
                    + newline);
            try {
                FileOutputStream fout = new FileOutputStream(file.getAbsolutePath());
                fout.write(eeprom, 0, maxAddress);
                appendToLog( maxAddress + " bytes saved to \"" + file.getName() + "\"" + newline);
            } catch (IOException e) {
                appendToLog( "Error while saving file");
            }
        } else {
            appendToLog( "Save command cancelled by user." + newline);
        }
    }//GEN-LAST:event_saveButtonActionPerformed

    private void showImageButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showImageButtonActionPerformed
        String line = "";
        for (int i = 0; i < maxAddress; i++) {
            if (i % 32 == 0) {
                line = line + "0x" + Utility.wordToHex(i) + "  ";
            }
            line = line + Utility.byteToHex(data[i]) + " ";
            if (i % 32 == 31) {
                appendToLog( line + newline);
                line = "";
            }

        }
    }//GEN-LAST:event_showImageButtonActionPerformed

    private void showDataButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showDataButtonActionPerformed
        String line = "";
        for (int i = 0; i < maxAddress; i++) {
            if (i % 32 == 0) {
                line = line + "0x" + Utility.wordToHex(i) + "  ";
            }
            line = line + Utility.byteToHex(eeprom[i]) + " ";
            if (i % 32 == 31) {
                appendToLog(line + newline);
                line = "";
            }

        }
    }//GEN-LAST:event_showDataButtonActionPerformed

    private void showDiffButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showDiffButtonActionPerformed
        readButton.setEnabled(false);
        readwrite = 2;
        progressBar.setValue(0);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        //Instances of javax.swing.SwingWorker are not reusuable, so
        //we create new instances as needed.
        readTask = new ReadTask(true);
        readTask.addPropertyChangeListener(this);
        readTask.execute();


    }//GEN-LAST:event_showDiffButtonActionPerformed

    private void readButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_readButtonActionPerformed
        readButton.setEnabled(false);
        readwrite = 2;
        progressBar.setValue(0);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        //Instances of javax.swing.SwingWorker are not reusuable, so
        //we create new instances as needed.
        readTask = new ReadTask(false);
        readTask.addPropertyChangeListener(this);
        readTask.execute();
    }//GEN-LAST:event_readButtonActionPerformed

    private void clearButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_clearButtonActionPerformed
        appendToLog("Clearing EEPROM. setting " + maxAddress + " bytes to 0x00"
                + newline);
        for (int i = 0; i < maxAddress; i++) {
            data[i] = 0;
        }
        clearButton.setEnabled(false);
        readwrite = 0;
        progressBar.setValue(0);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        //Instances of javax.swing.SwingWorker are not reusuable, so
        //we create new instances as needed.
        writeTask = new WriteTask(0, maxAddress);
        writeTask.addPropertyChangeListener(this);
        writeTask.execute();

    }//GEN-LAST:event_clearButtonActionPerformed

    public void loadFile(File file) {
            try {
                FileInputStream fin = new FileInputStream(file.getAbsolutePath());
                filesize = file.length();
                for (int i = 0; i < file.length(); i++) {
                    data[i] = (byte) fin.read();
                }
                appendToLog( filesize + " bytes loaded from \"" + file.getName() + "\"" + newline);
            } catch (IOException e) {
                appendToLog( "Error: File not found");
            }
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton clearButton;
    private javax.swing.JComboBox eepromTypeSelect;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JButton loadButton;
    private javax.swing.JComboBox offsetSelect;
    private javax.swing.JProgressBar progressBar;
    private javax.swing.JButton readButton;
    private javax.swing.JButton saveButton;
    private javax.swing.JComboBox serialSelect;
    private javax.swing.JButton showDataButton;
    private javax.swing.JButton showDiffButton;
    private javax.swing.JButton showImageButton;
    public javax.swing.JTextPane textPane;
    private javax.swing.JButton versionButton;
    private javax.swing.JButton writeButton;
    // End of variables declaration//GEN-END:variables
    private JFileChooser fc;
    private StyledDocument log;
}
