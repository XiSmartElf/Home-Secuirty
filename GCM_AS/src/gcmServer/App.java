package gcmServer;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

public class App 
{
	public static final String apiKey = "AIzaSyAkyenT8NUSNbnkefdsx_kwXvrR1nNIltU";
	public static final String regId = "APA91bFX-KJqAEPEy5aAM2koEV-eSvmcLR_R9SLPv8WNy9fygviNGUX8-nQUOoBUUpusTVwYC7k8917ht8Ii-TFAeAeD1brYB4TUMEkVqMJ6PfQgXBemYsErVXV9FQc6H4z3bcLkdHJl";
	private static native void visionCap();
	private static native String signal(String message);
	public static native byte[] frameObtain();
	private static Object startRequestedLock = new Object();
	private static Object stopRequestedLock = new Object();
	private static boolean sceneCheckRunning = true;
	private static boolean SYSTEM_ON = false;
	private static final int SYSTEM_CONNECT_PORT = 6789;
	private static boolean SYSTEM_RUN = true;
	
	static {
		System.load("/Users/BboyKellen/Library/Developer/Xcode/DerivedData/App-bjbfrdajvsexxhcqezyeezfxhrzg/Build/Products/Debug/App.dylib");   
	}
	private static Thread systemControl= new Thread(){

		public void run()
		{
	        ServerSocket welcomeSocket = null;
			Socket connectionSocket;
			BufferedReader inFromClient;
			DataOutputStream outToClient;
			
			//for create the system control socket
	        try{
	        	welcomeSocket = new ServerSocket(SYSTEM_CONNECT_PORT);
	        }
	        catch(IOException e){
	        	e.printStackTrace();
	        	System.out.println("<System Control> System unable to initialize! Retry later....");
	        	System.exit(0);
	        }
	        
			while(SYSTEM_RUN){
				//wait here till receive a request from client for start system
				try {
					System.out.println("<System Control> Waiting for client's request to start system.....");
					while(true){
						try{
							connectionSocket = welcomeSocket.accept();
					        inFromClient = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
					        outToClient = new DataOutputStream(connectionSocket.getOutputStream());
					        String clientSentence = inFromClient.readLine();
					        System.out.println("Received: " + clientSentence);
					        if(clientSentence != null && clientSentence.equals("queryState"))
						        outToClient.writeBytes("OFF\r\n");
					        if(clientSentence != null && clientSentence.equals("startRequest")){
					        	break;
					        }
						    connectionSocket.close();
						}
						catch(IOException ie){
							ie.printStackTrace();
						}
					}
					//when receive startSystem request from client
					synchronized(startRequestedLock){
						startRequestedLock.notify();
					}
					System.out.println("<System Control> System is going to be turned on............");
					while(SYSTEM_ON == false){
						Thread.sleep(2000);
					}
			        outToClient.writeBytes("startRequestRecv\r\n");
			        connectionSocket.close();
				} catch (IOException e1) {
					e1.printStackTrace();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

				//...... running now .......
				
				//wait here till receive a request from client for stop system
				try {
					System.out.println("<System Control> Waiting for client's request to stop system.....");
					while(true)
					{
						try{
							connectionSocket = welcomeSocket.accept();
					        inFromClient = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
					        outToClient = new DataOutputStream(connectionSocket.getOutputStream());
					        String clientSentence = inFromClient.readLine();
					        System.out.println("Received: " + clientSentence);
					        if(clientSentence != null && clientSentence.equals("queryState"))
						        outToClient.writeBytes("ON\r\n");
					        if(clientSentence != null && clientSentence.equals("stopRequest"))
					        	break;
					        else if(clientSentence != null && clientSentence.equals("warningClear")){
					        	signal("warningClear");
						        outToClient.writeBytes("warningClearRecv\r\n");
					        }
						    connectionSocket.close();
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
					while(SYSTEM_ON == true)
						Thread.sleep(2000);
			        outToClient.writeBytes("stopRequestRecv\r\n");
			        connectionSocket.close();
				} catch (IOException e1) {
					e1.printStackTrace();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			try {
				welcomeSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	};
	
	
	
	public static void main( String[] args ) throws InterruptedException, IOException
    {   
		systemControl.start();
		while(true)
		{
	    	synchronized (startRequestedLock) {
	    		startRequestedLock.wait();
	    	}
			systemStart();
		}
    }
	
	private static void systemStart() throws InterruptedException
	{
        VideoServer vidServer = new VideoServer();
        signal("warningClear");

        //start video capture
        Thread detection = new Thread(){
        	public void run(){
        		visionCap();
        	}
        };
        
        //start actively checking the scene:
        Thread checkScene = new Thread(){
        	public void run(){
        		while(sceneCheckRunning)
        		{
        			//return a address of picture that records a suspected scene
	                String sceneFileDirectory = signal("sceneCheck");//block till find crime
	                if(sceneFileDirectory.equals("Not found"))
	                	continue;
	                File file = new File(sceneFileDirectory);
	                PictureHandler.sendPicture(file);
	                System.out.println(sceneFileDirectory);
	                //warning clear is handled in system control socket thread
        		}
        	}
        };    
        detection.start();
        checkScene.start();
        vidServer.start();
        SYSTEM_ON = true;
        System.out.println("<Home-Server> ********************** System function has been turned ON now **********************"); 

        synchronized(stopRequestedLock){
        	stopRequestedLock.wait();
        }
        //listenSocket blocks so we need to force to stop it and catch the error
        if(vidServer.listenSocket!=null)
        {
	        try {
				vidServer.listenSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
        }
        else{
        	System.out.println("Terminating video requested. Please wait for client stoping the connection/stop using monitoring feature......");
        	vidServer.isRunning=false;
        }
        vidServer.join();
        System.out.println();
        System.out.println("<Home-Server> >>>>>> real-time video feature is closed");
        sceneCheckRunning = false;
        checkScene.join();
        sceneCheckRunning = true;
        System.out.println("<Home-Server> >>>>>> scene check feature is closed");
        signal("terminateVision");
        detection.join();
        signal("warningClear");
        System.out.println("<Home-Server> >>>>>> vision monitoring feature is closed");
        System.out.println();
        System.out.println("<Home-Server> ********************** System function has been turned OFF now **********************"); 
        System.out.println();
        SYSTEM_ON = false;

	}

	
	
}

