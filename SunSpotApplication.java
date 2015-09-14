package org.sunspotworld;

import javax.microedition.io.Connector;
import com.sun.spot.io.j2me.radiogram.*;
import com.sun.spot.peripheral.Spot;
import com.sun.spot.peripheral.radio.IRadioPolicyManager;
import com.sun.spot.resources.Resources;
import com.sun.spot.resources.transducers.LEDColor;
import com.sun.spot.resources.transducers.ITriColorLEDArray;
import com.sun.spot.util.Utils;
import java.io.IOException;
import com.sun.spot.resources.transducers.IAnalogInput;
import com.sun.spot.sensorboard.EDemoBoard;
import com.sun.spot.sensorboard.peripheral.Servo;
import com.sun.spot.service.BootloaderListenerService;
import javax.microedition.midlet.MIDlet;
import javax.microedition.midlet.MIDletStateChangeException;
import javax.microedition.io.Datagram;

public class SunSpotApplication extends MIDlet {
    private Object lock = new Object(); 
    //LEDs for status indicator blinking
    private static final ITriColorLEDArray leds = (ITriColorLEDArray) Resources.lookup(ITriColorLEDArray.class);
    private static final int LED_ON = 10;
    //MUST be the same as the BROADCAST_PORT from RSSIBeacon.java
    //All beacons transmit on the same port, so you shouldn't change this!
    private static final String RECEIVE_PORT = "55";
    
    private static final int BROADCAST_CHANNEL = 15;
    private static int TRANSMIT_PORT = 56;
    //MUST be the same as the CONFIRM_BYTE from RSSIBeacon.java
    private static final byte RSSI_CONFIRM_BYTE = 23;
    
    //MUST be the same as the CONFIRM_BYTE from RSSIReceiver.java
    //Change this to keep people from using your port and screwing up your data!
    private static final byte TRIP_CONFIRM_BYTE = 100;
    
    //static radio connection objects
    private static RadiogramConnection rxConnection = null;
    private static Radiogram rxg = null;
    
    /// CONTROL STUFF   ///
    
    private static final int SERVO_CENTER_VALUE = 1350;     // Center
    private static final int SERVO_LEFT_VALUE = 2000;        //left
    private static final int SERVO_RIGHT_VALUE = 950;       // Right
   
    private static final int RESET_SERVO_CENTER_VALUE = 1500;     // Center
    
    private static final int FULL_DELAY = 1000;
    private static final int HALF_DELAY = 500;
    private static final int SPEED_CENTER_VALUE = 1395;  // no motion
    private static final int SPEED_FORWARD_VALUE = 1200; // max seep in front
    private static final int SPEED_REVERSE_VALUE = 1600; // max speed in reverse
    
    private EDemoBoard eDemo = EDemoBoard.getInstance();
    private Servo servo = new Servo(eDemo.getOutputPins()[EDemoBoard.H0]);  // servo controller
    private Servo esc = new Servo(eDemo.getOutputPins()[EDemoBoard.H1]);    // Motor controller
    
    
    private final IAnalogInput IR1 = EDemoBoard.getInstance().getAnalogInputs()[EDemoBoard.A0];
    private final IAnalogInput IR2 = EDemoBoard.getInstance().getAnalogInputs()[EDemoBoard.A1];
    private final IAnalogInput IR3 = EDemoBoard.getInstance().getAnalogInputs()[EDemoBoard.A2];
    private final IAnalogInput IR4 = EDemoBoard.getInstance().getAnalogInputs()[EDemoBoard.A3];
    
    public static int tripAt1 = 0;
    public static int tripAt2 = 0;
    public static int tripAt3 = 0;
    public static int tripAt4 = 0;
        
    //public static String spotTripAddress;
    
    public static boolean turnEnable;
    
    private static final int infinteRangeIR = 50;       // Set to check infinity
    public static int PIDEnable; 
    private static final int DEBUG = 0;
    
     
    private void run() 
    {
        new Thread() 
        {
            public void run () 
            {
                receiverLoop();
            }
        }.start();
		
       new Thread() 
       {
            public void run () 
            {
		        transmitLocationLoop();
            }
        }.start(); 
       
       new Thread() 
        {
            public void run () 
            {
                controlLoop();
            }
        }.start();
	
        
        //RespondToUser(); 
    }
    
    private void  controlLoop()
    {
        if(DEBUG == 1)System.out.println("In Control Loop");
        double sensorVoltage_IR1 = 0;           // min distnce 20 cm V = 2.52
        double sensorVoltage_IR2 = 0;
        double sensorVoltage_IR3 = 0;
        double sensorVoltage_IR4 = 0;
            
        float distance_IR1 = 0;
        float distance_IR2 = 0;
        float distance_IR3 = 0;
        float distance_IR4 = 0;
        
        
        
        float error = 0;
        float prevError = 0;
        float Ku = 100;         // MAX oscillation
        float Pu = 2;
        float correction;
        float P;
        float I = 0;
        float D;
                
        int speedLevel;
   
        int enableRight;
        int enableLeft;
     
        int servoCorrection;
        
        while(true)
        {
            
            if( turnEnable == false )
            {
                try
                {    
                    sensorVoltage_IR1 =  IR1.getVoltage();      // LEFT
                    sensorVoltage_IR2 =  IR2.getVoltage();      // RIGHT    
                    sensorVoltage_IR3 =  IR3.getVoltage();      // FRONT
                    //sensorVoltage_IR4 =  IR2.getVoltage();        
                }
                catch (IOException ex) 
                {
                    System.out.println("Error Getting Voltage");    
                }
                distance_IR1 = (float) ( 10 * (-0.0042*sensorVoltage_IR1*sensorVoltage_IR1*sensorVoltage_IR1 + 0.1237*sensorVoltage_IR1*sensorVoltage_IR1 - 1.2905*sensorVoltage_IR1 + 5.4067) );
                distance_IR2 = (float) ( 10 * (-0.0042*sensorVoltage_IR2*sensorVoltage_IR2*sensorVoltage_IR2 + 0.1237*sensorVoltage_IR2*sensorVoltage_IR2 - 1.2905*sensorVoltage_IR2 + 5.4067) ) ;
                distance_IR3 = (float) ( 10 * (-0.0042*sensorVoltage_IR3*sensorVoltage_IR3*sensorVoltage_IR3 + 0.1237*sensorVoltage_IR3*sensorVoltage_IR3 - 1.2905*sensorVoltage_IR3 + 5.4067) );
                //distance_IR4 = (float) ( 10 * (-0.0042*sensorVoltage_IR4*sensorVoltage_IR4*sensorVoltage_IR4 + 0.1237*sensorVoltage_IR4*sensorVoltage_IR4 - 1.2905*sensorVoltage_IR4 + 5.4067) ) ;
                
                System.out.println("distance_IR1: "+ distance_IR1 + " distance_IR2: " + distance_IR2);
                                                    // Left                             // Right
                
                if(distance_IR1 >= infinteRangeIR )
                    distance_IR1 = infinteRangeIR; 
                
                if(distance_IR2 >= infinteRangeIR )
                    distance_IR2 = infinteRangeIR; 
                
                error = distance_IR1 - distance_IR2 + 5;    
                
                P = error ; 
                D = error - prevError ;

                correction = (float) (2.25 * (P + D));
                 if(DEBUG == 1)  System.out.println("Correction is= "+correction);
                //correction = error; if no PID
                prevError = error;
                speedLevel = 1;

                /*if(tripAt1 == 1 || tripAt2 == 1 || tripAt3 == 1 || tripAt4 == 1 )         // Checking for infinity
                {       
                    speedLevel = 2;
                    System.out.println("Trip Identified");
                }

                if( speedLevel == 2 )// && distance_IR1 >= infinteRangeIR )  // IR1 on left side
                {    
                    PIDEnable = 0;
                    System.out.println("Take Left Turn");
                    leftTurn();
                }
                else if ( speedLevel == 3 )// && distance_IR2 >= infinteRangeIR)    //// IR2 on right side
                {
                    PIDEnable = 0;
                    System.out.println("Take Right Turn");
                    rightTurn();
                }
                else
                {
                    PIDEnable = 1;
                }*/    


                 if(DEBUG == 1) System.out.println("Turn Enable enable is: "+ turnEnable);

                EscControl(correction, speedLevel);
                servoCorrection = ServoControl(correction);
            
            }

        
        }    
        
    }        
    
    private synchronized void cornerLoop1()
    {   
        turnEnable = true;
        tripAt1 ++; 
        if(DEBUG == 1) System.out.println(" At Trip1 ");
        if(tripAt1 == 1)    // Take Right
        {   
             if(DEBUG == 1) System.out.println("Trip1 being executed ");
            rightTurn();
            // Take turn COde here
        }
         if(DEBUG == 1) System.out.println("tripAt1: "+tripAt1 + " tripAt2: "+tripAt2 + " tripAt3: "+tripAt3 + " tripAt4: "+tripAt4);
        
        tripAt1 ++; 
        if(tripAt1 >= 5)
          Utils.sleep(5000);  
        turnEnable = false;
    }        
    
    private synchronized void cornerLoop2()
    {
        turnEnable = true;
        tripAt2 ++; 
         if(DEBUG == 1) System.out.println(" At Trip2 ");
        if(tripAt2 == 1)    // Take Left
        {
             if(DEBUG == 1) System.out.println("Trip2 being executed ");
            leftTurn();
            // Take turn COde here
        }
         if(DEBUG == 1) System.out.println("tripAt1: "+tripAt1 + " tripAt2: "+tripAt2 + " tripAt3: "+tripAt3 + " tripAt4: "+tripAt4);
        
        tripAt2++; 
        turnEnable = false;
    }     
    
    private synchronized void cornerLoop3()
    {
        turnEnable = true;
        tripAt3 ++; 
        System.out.println(" At Trip3 ");
        if(tripAt3 == 1)    // Take left
        {
            System.out.println("Trip3 being executed ");
            leftTurn3();
            
            // Take turn COde here
        }
        
        System.out.println("tripAt1: "+tripAt1 + " tripAt2: "+tripAt2 + " tripAt3: "+tripAt3 + " tripAt4: "+tripAt4);
        tripAt3++; 
        turnEnable = false;
    }   
    
    private synchronized void leftTurn3()
    {
        
        turnServoControl(SERVO_CENTER_VALUE);
        turnEscControl(SPEED_CENTER_VALUE, FULL_DELAY);
        Utils.sleep(1500);
        
        turnServoControl(SERVO_LEFT_VALUE);
        turnEscControl(SPEED_FORWARD_VALUE, FULL_DELAY);
        turnEscControl(SPEED_CENTER_VALUE, FULL_DELAY);
        turnServoControl(SERVO_RIGHT_VALUE);
        turnEscControl(SPEED_REVERSE_VALUE, FULL_DELAY);
        turnEscControl(SPEED_CENTER_VALUE, FULL_DELAY);
        turnServoControl(SERVO_LEFT_VALUE);  
        turnEscControl(SPEED_FORWARD_VALUE, 350 + FULL_DELAY);
        turnEscControl(SPEED_CENTER_VALUE, HALF_DELAY);
        turnServoControl(SERVO_CENTER_VALUE);
        Utils.sleep(1000);
        
        turnEscControl(SPEED_FORWARD_VALUE, HALF_DELAY * 16);
        
        turnEscControl(SPEED_CENTER_VALUE, FULL_DELAY);
        Utils.sleep(1000);
        
        turnServoControl(SERVO_CENTER_VALUE); 
        //turnEscControl(SPEED_FORWARD_VALUE, FULL_DELAY);
        turnEscControl(SPEED_CENTER_VALUE, FULL_DELAY);
        turnServoControl(SERVO_LEFT_VALUE);
        turnEscControl(SPEED_FORWARD_VALUE, FULL_DELAY + HALF_DELAY);
        turnEscControl(SPEED_CENTER_VALUE, FULL_DELAY);
        turnServoControl(SERVO_RIGHT_VALUE);
        turnEscControl(SPEED_REVERSE_VALUE, FULL_DELAY);
        turnEscControl(SPEED_CENTER_VALUE, FULL_DELAY);
        turnServoControl(SERVO_LEFT_VALUE);  
        turnEscControl(SPEED_FORWARD_VALUE, HALF_DELAY);
        turnServoControl(SERVO_CENTER_VALUE);
        //turnEscControl(SPEED_FORWARD_VALUE, HALF_DELAY);
        turnEscControl(SPEED_CENTER_VALUE, HALF_DELAY);
        
        
        
    }        
    
    
    public synchronized void cornerLoop4()
    {
        turnEnable = true;
        tripAt4 ++; 
        
        System.out.println(" At Trip4 ");
        if(tripAt4 == 1)    // Take left
        {
            System.out.println("Trip4 being executed ");
            //leftTurn();
            
            // Take turn COde here
        }
        System.out.println("tripAt1: "+tripAt1 + " tripAt2: "+tripAt2 + " tripAt3: "+tripAt3 + " tripAt4: "+tripAt4);
        
        tripAt4++; 
        turnEnable = false;
    }     
    
    private synchronized void updateTrip( String Address)
    {
        turnEnable = true;
       //blink color code to confirm successful send!
            if (Address.endsWith("7F48"))
            {   
                blinkLED(0,1,LEDColor.GREEN);
                cornerLoop1();
                
            } else if (Address.endsWith("7E5D"))
            {   
                blinkLED(2,3,LEDColor.MAGENTA);
                cornerLoop2();
            } else if (Address.endsWith("80F5"))
            {   
                blinkLED(4,5,LEDColor.ORANGE);
                cornerLoop3();
            } else if (Address.endsWith("7FEE"))
            {   
                blinkLED(6,7,LEDColor.TURQUOISE);
                cornerLoop4();
            } else
            {
                blinkLED(6,7,LEDColor.RED);
                System.out.println("Unknown Beacon");
            }    
    
    }        
    
    
    private void receiverLoop()
    {
         if(DEBUG == 1) System.out.println("In receiver Loop");
        RadiogramConnection rx = null; 
        int rssiValue;
        String spotTripAddress;        
        while (true) 
        { 
            try 
            { 
                rxConnection = (RadiogramConnection) Connector.open("radiogram://:" + RECEIVE_PORT);
                rxg = (Radiogram) rxConnection.newDatagram(10);
            
                // blink white to confirm successful connection setup!
                blinkLEDall(LEDColor.WHITE);
                
            while (true)
            {    
                if(DEBUG == 1) System.out.println("In Receiver");
                rxg.reset(); 
                rxConnection.receive(rxg);

                byte checkByte = rxg.readByte();     //read confirmation byte data from the radiogram

                if(DEBUG == 1) System.out.println("In Receiver lock start");
                
                if (checkByte == TRIP_CONFIRM_BYTE) 
                {
                    turnEnable = true;
                    //grab the RSSI and address info embedded in the radiogram
                    rssiValue = rxg.getRssi();
                    spotTripAddress = rxg.getAddress();
                    //System.out.println("Received Tripwire packet from: " + spotTripAddress  + ", RSSI: " + rssiValue);
                    updateTrip(spotTripAddress);
                } 
                else 
                {   
                        turnEnable = false;
                        //blink red upon failure. :(
                        //blinkLEDall(LEDColor.RED);
                         if(DEBUG == 1) System.out.println("Unrecognized radiogram type! Expected: " + RSSI_CONFIRM_BYTE + " or " + TRIP_CONFIRM_BYTE + ", Saw: " + checkByte);
                }
                
                if(DEBUG == 1) System.out.println("In Receiver end");

                }
            }
             
            catch (Exception ex) 
            { 
                blinkLEDall( LEDColor.RED);
                System.err.println("Could not open radiogram broadcast connection!");
                System.err.println(ex);
            } 
            finally 
            { 
                if (rx != null) 
                { 
                    try 
                    { 
                        rx.close(); 
                    } 
                    catch (IOException ex) 
                    { 
                        
                    } 
                } 
            } 
        }
              
        
  
    }        
    
   
    
    
    private void transmitLocationLoop () 
    {
	// transmitt all necessary data	    
        System.out.println("Initalizing Transmitter");
        RadiogramConnection tx = null; 
        Datagram dg = null;
        long currentTime;
        int tripValue =0;
        
        
        while (true) 
        { 
            try 
                { 
                tx = (RadiogramConnection)Connector.open("radiogram://broadcast:" + TRANSMIT_PORT); 
                dg = tx.newDatagram(50);      // No Rebroadcasting Packets
                //Datagram txData = tx.newDatagram(tx.getMaximumLength()); 
                while (true) 
                    { 
                            
                        currentTime = System.currentTimeMillis();
                        dg.reset(); 
                        if(DEBUG == 1) System.out.println("In transmitter Start");
                        if( tripAt1 >= 1 ) 
                        {
                            tripValue = 1;  // define laater according to GUI
                            
                        }

                        else if( tripAt2 >= 1 ) 
                        {
                            tripValue = 2;  // define laater according to GUI
                            
                        }

                        else if( tripAt3 >= 1 ) 
                        {
                            tripValue = 3;  // define laater according to GUI
                            
                        }

                        else if( tripAt4 >= 1 ) 
                        {
                            tripValue = 4;  // define laater according to GUI
                            
                        }

                        System.out.println("Value of Trip" + tripValue);
                        dg.writeLong(currentTime); 
                        dg.writeInt(tripValue); 
                        
                       
                        leds.getLED(4).setColor(LEDColor.YELLOW);     // If transmitting
                        leds.getLED(3).setColor(LEDColor.YELLOW);
                        leds.getLED(4).setOn();
                        leds.getLED(3).setOn();

                        tx.send(dg); 
                        Utils.sleep(1000); //Delay was 5000
                        System.out.println("In transmitter End");
                    }
                }
                catch (IOException ex) 
                { 
                 
                } 
                finally 
                { 
                if (tx != null) 
                { 
                    try 
                    { 
                        tx.close(); 
                    } 
                    catch (IOException ex) 
                    {
                        
                    } 
                } 
            } 
        }
        
    
    }
    
    
    
    
    
    private void EscControl(float correction, int speedLevel) 
    {
        if(turnEnable == false)
        {
            int speedCorrection = 0;        

            speedCorrection = (int) (1200 + Math.abs(correction));

            if(speedCorrection > 1275)
            speedCorrection = 1275; // No stopping on PID control

             if(DEBUG == 1) System.out.println("Correction in ESC = " + speedCorrection);
            esc.setValue(speedCorrection);

            Utils.sleep(100);
    
        }
    }
    private int ServoControl(float correction) 
    {   
        if(turnEnable == false)
        {    
            int servoCorrection = 1250 + (int)correction * 15;  // Direct is 45

            if(servoCorrection > SERVO_LEFT_VALUE)
                servoCorrection = SERVO_LEFT_VALUE;


            if(servoCorrection < SERVO_RIGHT_VALUE)
                servoCorrection = SERVO_RIGHT_VALUE;


             if(DEBUG == 1) System.out.println("Correction Sevo Correction is = " + servoCorrection);
            servo.setValue(servoCorrection);
            Utils.sleep(100);

            return servoCorrection;
        }  
        return 0;
    }

    
    /* Helper method for blinking LEDs the specified color. */
    private static void blinkLED(int led0, int led1, LEDColor color) 
    {
        leds.getLED(led0).setColor(color);
        leds.getLED(led1).setColor(color);
        leds.getLED(led0).setOn();
        leds.getLED(led1).setOn();
        Utils.sleep(LED_ON);
        leds.setOff();
    }
    private static void blinkLEDall(LEDColor color) 
    {
        leds.setColor(color);
        leds.setOn();
        Utils.sleep(LED_ON);
        leds.setOff();
    }
    
    /* Establish RadiogramConnections on the specified ports. */
    private static void setupConnection() 
    {   
        System.out.println("Setting up receiving comm port");
        IRadioPolicyManager rpm = Spot.getInstance().getRadioPolicyManager();
        rpm.setOutputPower(-16);
        rpm.setChannelNumber(BROADCAST_CHANNEL);
        
          
    }
    /* Wait for beacon transmissions, then forward them to the basestation. */
    
    private void initializeMotorControl() 
    {   
        System.out.println("Setting up Motor Control");
        servo.setValue(RESET_SERVO_CENTER_VALUE);  // Center value always initalize
        esc.setValue(SPEED_CENTER_VALUE);
        Utils.sleep(10);
        servo.setValue(RESET_SERVO_CENTER_VALUE);  // Center value always initalize
        esc.setValue(SPEED_CENTER_VALUE);
        Utils.sleep(100);
        servo.setValue(RESET_SERVO_CENTER_VALUE);  // Center value always initalize
        esc.setValue(SPEED_CENTER_VALUE);
        Utils.sleep(1000);
        PIDEnable = 1;
        
    }
    
    
    private void turnEscControl(int correction, int delay) 
    {
        esc.setValue(correction); 
        Utils.sleep(delay);
    }
       
    
    private void turnServoControl(int correction) 
    {   
        servo.setValue(correction);
        Utils.sleep(1000);
    }

    
    
    
    private void rightTurn()
    {
        // Take tuen here
        System.out.println();
        System.out.println("In method Taking Right");
        System.out.println();
        System.out.println();
        System.out.println();
        turnServoControl(SERVO_CENTER_VALUE); 
        turnEscControl(SPEED_FORWARD_VALUE, FULL_DELAY);
        turnEscControl(SPEED_CENTER_VALUE, FULL_DELAY);
        turnServoControl(SERVO_RIGHT_VALUE);
        turnEscControl(SPEED_FORWARD_VALUE, FULL_DELAY);
        turnEscControl(SPEED_CENTER_VALUE, FULL_DELAY);
        turnServoControl(SERVO_LEFT_VALUE);
        turnEscControl(SPEED_REVERSE_VALUE, FULL_DELAY);
        turnEscControl(SPEED_CENTER_VALUE, FULL_DELAY);
        turnServoControl(SERVO_RIGHT_VALUE);  
        turnEscControl(SPEED_FORWARD_VALUE, FULL_DELAY);
        turnServoControl(SERVO_CENTER_VALUE);
        //turnEscControl(SPEED_FORWARD_VALUE, HALF_DELAY);
        //turnEscControl(SPEED_CENTER_VALUE, HALF_DELAY);

        
        
        System.out.println("leaving  method Taking right");
        Utils.sleep(1000);
    }        
    
    
    private void leftTurn()
    {
        // take turn here
        System.out.println();
        System.out.println("In method Taking left");
        System.out.println();
        System.out.println();
        System.out.println();
        turnServoControl(SERVO_CENTER_VALUE); 
        turnEscControl(SPEED_FORWARD_VALUE, HALF_DELAY);
        turnEscControl(SPEED_CENTER_VALUE, FULL_DELAY);
        turnServoControl(SERVO_LEFT_VALUE);
        turnEscControl(SPEED_FORWARD_VALUE, FULL_DELAY + HALF_DELAY);
        turnEscControl(SPEED_CENTER_VALUE, FULL_DELAY);
        turnServoControl(SERVO_RIGHT_VALUE);
        turnEscControl(SPEED_REVERSE_VALUE, FULL_DELAY);
        turnEscControl(SPEED_CENTER_VALUE, FULL_DELAY);
        turnServoControl(SERVO_LEFT_VALUE);  
        turnEscControl(SPEED_FORWARD_VALUE, HALF_DELAY);
        turnServoControl(SERVO_CENTER_VALUE);
        //turnEscControl(SPEED_FORWARD_VALUE, HALF_DELAY);
        turnEscControl(SPEED_CENTER_VALUE, HALF_DELAY);

        
        System.out.println("leaving  method Taking left");
        Utils.sleep(1000);
        
        
    }        
    
    
    
    
    protected void startApp() throws MIDletStateChangeException 
    {
        
        System.out.println("Starting Auto Drive...");
        // Listen for downloads/commands over USB connection
        BootloaderListenerService.getInstance().start();
        initializeMotorControl();
        setupConnection();
        run();
    }
    
    protected void pauseApp() {}
    
    protected void destroyApp(boolean unconditional) throws MIDletStateChangeException 
    {
        leds.setOff();
    }
    
}
