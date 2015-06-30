package gcmServer;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

public class CopyOfApp 
{
	public static final String apiKey = "AIzaSyAkyenT8NUSNbnkefdsx_kwXvrR1nNIltU";
	public static final String regId = "APA91bED5VcKjpN178b2oKLUw0VuLxqK3PE7hPLXRKfkGUH_HtbK7yraqP8_N5Yk9VDST5dSEgFoJevZ4lQr_HqsDikzBrZkR7b-DYsWBj6sE8WxsOtu9WwZZusKxW5NQUhncp2iCV_G";
	private static native void visionCap();
	private static native String signal(String message);
	public static native byte[] frameObtain();
	private static Object startRequestedLock = new Object();
	private static Object stopRequestedLock = new Object();
	private static boolean sceneCheckRunning = true;
	private static boolean SYSTEM_ON = false;
	
	static {
		System.load("/Users/BboyKellen/Library/Developer/Xcode/DerivedData/App-bjbfrdajvsexxhcqezyeezfxhrzg/Build/Products/Debug/App.dylib");   
	}
	private static Thread systemControl= new Thread(){
		
		Socket centralSocket;
		DataOutputStream outToCentral;
		BufferedReader inFromCentral;
		
		public void run()
		{
			connect();
			try{
				System.out.println("<System Control> Waiting for client's request to start system.....");
				while(true){
					try{
						if(centralSocket.isClosed())
							connect();
						System.out.println("Connected! and wait for inputs");

						String centralMess = inFromCentral.readLine();
						System.out.println("Received: " + centralMess);
						if(centralMess != null && centralMess.equals("queryState"))
							outToCentral.writeBytes("OFF\r\n");
						if(centralMess != null && centralMess.equals("startRequest")){
							break;
						}
						outToCentral.flush();
					}
					catch(IOException ie){
						ie.printStackTrace();
						System.exit(0);
					}
				}
				//when receive startSystem request from client
				synchronized(startRequestedLock){
					startRequestedLock.notify();
				}

				System.out.println("<System Control> System is going to be turned on............");
				outToCentral.writeBytes("startRequestRecv\r\n");
				outToCentral.flush();


			} catch (IOException e1) {
				e1.printStackTrace();
			}

				 

			//...... running now .......

			//wait here till receive a request from client for stop system
			try {
				System.out.println("<System Control> Waiting for client's request to stop system.....");
				while(true)
				{
					try{
						if(!centralSocket.isConnected())
							connect();
						String centralMess = inFromCentral.readLine();
						System.out.println("Received: " + centralMess);
						if(centralMess != null && centralMess.equals("queryState"))
							outToCentral.writeBytes("ON\r\n");
						if(centralMess != null && centralMess.equals("stopRequest"))
							break;
						else if(centralMess != null && centralMess.equals("warningClear")){
							signal("warningClear");
							outToCentral.writeBytes("warningClearRecv\r\n");
						}
						outToCentral.flush();
					}
					catch(IOException ie){
						ie.printStackTrace();
					}
				}
				//when receive stopSystem request from client	
				synchronized(stopRequestedLock){
					stopRequestedLock.notify();
				}
				System.out.println("<System Control> System is going to be shutted off............");

				outToCentral.writeBytes("stopRequestRecv\r\n");
				outToCentral.flush();

			} catch (IOException e1) {
				e1.printStackTrace();
			}
			
			try {
				centralSocket.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		public void connect()
		{
			//for create the system control socket
	        try{
	        	 centralSocket = new Socket("localhost", 6790);
	
	        	 outToCentral = new DataOutputStream(centralSocket.getOutputStream());
		  		 inFromCentral = new BufferedReader(new InputStreamReader(centralSocket.getInputStream()));
		  		 System.out.println("connected succesfuly!");
	  		  }
	        catch(IOException e){
	        	e.printStackTrace();
	        	System.out.println("<System Control> System unable to initialize! Retry later....");
	        	System.exit(0);
	        }
		}
	};

	
	
	public static void main( String[] args ) throws InterruptedException, IOException
    {   
		systemControl.start();
    }

	
 
}

