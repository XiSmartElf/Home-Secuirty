package gcmServer;

import java.io.*;
import java.net.*;
import java.util.*;


public class VideoServer extends Thread {

	//RTP variables:
	DatagramSocket RTPsocket; //socket to be used to send and receive UDP packets
	DatagramPacket senddp; //UDP packet containing the video frames

	InetAddress ClientIPAddr; //Client IP address
	int RTP_dest_port = 0; //destination port for RTP packets  (given by the RTSP Client)

	//Video variables:
	int imagenb = 0; //image nb of the image currently transmitted
	static int MJPEG_TYPE = 26; //RTP payload type for MJPEG video
	static int FRAME_PERIOD = 50; //Frame period of the video to stream, in ms
	public int RTSPport = 8554;

	SendVideo sendVid; //timer used to send the images at the video frame rate
	byte[] buf; //buffer used to store the images to send to the client 

	//RTSP variables
	final static int INIT = 0;
	final static int READY = 1;
	final static int PLAYING = 2;
	final static int SETUP = 3;
	final static int PLAY = 4;
	final static int PAUSE = 5;
	final static int TEARDOWN = 6;
	static int state; //RTSP Server state == INIT or READY or PLAY
	
	BufferedReader RTSPBufferedReader;
	BufferedWriter RTSPBufferedWriter;
	static int RTSP_ID = 123456; //ID of the RTSP session
	int RTSPSeqNb = 0; //Sequence number of RTSP messages within the session

	final static String CRLF = "\r\n";	
	public boolean isRunning = true;
	public boolean isSending = true;
	public boolean isConnected = false;
	public ServerSocket listenSocket;
	private Socket RTSPsocket;
	
	
	@Override
	public void run() {
		
		while(isRunning)
		{
			buf = new byte[100000]; 
		    sendVid = new SendVideo();
			RTSPsocket = null;
			try{
				//init TCP RTSP connection
				listenSocket = new ServerSocket(RTSPport);
				try{
					RTSPsocket = listenSocket.accept();
				}
				catch(SocketException se)
				{
					System.out.println("Socket is closed..");
					break;
				}
				listenSocket.close();
				listenSocket = null;

				//Get Client IP address
				ClientIPAddr = RTSPsocket.getInetAddress();
				//Initiate RTSPstate
				state = INIT;

				//Set input and output stream filters:
				RTSPBufferedReader = new BufferedReader(new InputStreamReader(RTSPsocket.getInputStream()) );
				RTSPBufferedWriter = new BufferedWriter(new OutputStreamWriter(RTSPsocket.getOutputStream()) );
				RTSPsocket.setSoTimeout(10000);
			}
			catch(Exception ee)
			{
				System.out.println("<Vid-Monitoring> Exception caught when creating RTPS socket");
				ee.printStackTrace();
				System.exit(0);
			}
			//Wait for the SETUP message from the client
			int request_type;
			boolean done = false;
			isConnected = true;
			isSending = true;
			
			while(done == false && isConnected == true)
			{
				System.out.println("wait for Setup...");
				request_type = parse_RTSP_request(); //blocking

				if (request_type == SETUP)
				{
					done = true;
					//update RTSP state
					state = READY;

					//Send response
					send_RTSP_response();

					//init RTP UDP socket
					try {
						RTPsocket = new DatagramSocket();
					} catch (SocketException e) {
						System.out.println("<Vid-Monitoring> Exception caught when creating RTP socket");
						e.printStackTrace();
						System.exit(0);
					}
				}
			}

			//loop to handle RTSP requests
			while(isConnected)
			{
				//parse the request
				request_type = parse_RTSP_request(); //blocking
				
				System.out.println("	handling the request of: "+request_type);

				if ((request_type == PLAY) && (state == READY))
				{
					//send back response
					send_RTSP_response();
					//start timer
					sendVid.start();
					//update state
					state = PLAYING;
				}
				else if ((request_type == PAUSE) && (state == PLAYING))
				{
					//send back response
					send_RTSP_response();
					//stop timer
					isSending = false;
					try {
						sendVid.join();
					} catch (InterruptedException e) {
						e.printStackTrace();
					};
					//update state
					state = READY;
				}
				else if (request_type == TEARDOWN)
				{
					//send back response
					send_RTSP_response();
					//stop timer
					isSending = false;
					try {
						sendVid.join();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					break;
				}
				else if ((request_type == PLAY) && (state == PLAYING))
				{
					System.out.println("<Vid-Monitoring> Received remain connection request from client...");
				}
			}
			try {
				RTSPsocket.close();
			} catch (IOException e) {
				System.out.println("<Vid-Monitoring> Exception caught when closing RTSP socket");
				e.printStackTrace();
				System.exit(0);
			}
			if(RTPsocket!=null){
				if(sendVid.isAlive()){
					try {
						sendVid.join();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				RTPsocket.close();
			}
			isConnected = false;
		}
	}

	
	
	
	 private class SendVideo extends Thread{

		@Override
		public void run() {
			System.out.println();
			System.out.println("****************** sending packets to client ***********************");
			System.out.println();
			while(isSending)
			{
				imagenb++;
				//get next frame to send from the video, as well as its size
				byte[] image = App.frameObtain();
				int image_length = image.length;
				RTPpacket rtp_packet = new RTPpacket(MJPEG_TYPE, imagenb, imagenb*FRAME_PERIOD, image, image_length);
				int packet_length = rtp_packet.getlength();
				byte[] packet_bits = new byte[packet_length];
				rtp_packet.getpacket(packet_bits);
	
				try{
					//send the packet as a DatagramPacket over the UDP socket 
					senddp = new DatagramPacket(packet_bits, packet_length, ClientIPAddr, RTP_dest_port);
					//System.out.println("RTP_dest_port =" + RTP_dest_port +" .....  image length is: "+image_length );
					RTPsocket.send(senddp);
					Thread.sleep(FRAME_PERIOD);
				}
				catch(IOException | InterruptedException ex)
				{
					System.out.println("<Vid-Monitoring> Exception caught when sending data to client");
					System.out.println("<Vid-Monitoring> UDP video: Client must has terminated its session without notice. i.e. power off or app crashed...");
					ex.printStackTrace();	
					isSending = false;
					isConnected = false;
					break;
				}
			}
			isSending = true;
			System.out.println();
			System.out.println("****************** stop sending packets to client ***********************");
			System.out.println();
		}
		
	 }
	
	
	
	
	
	//------------------------------------
	//Parse RTSP Request
	//------------------------------------
	public int parse_RTSP_request()
	{
		int request_type = -1;
		String RequestLine = null;

		try{
			try{
				RequestLine = RTSPBufferedReader.readLine();
			}
			catch(SocketTimeoutException se)
			{
				System.out.println("<Vid-Monitoring> TCP Conn: Client teminated without notice. i.e. power off, network problem, or app crashed...");
				isConnected = false;
				isSending =false;
				return(request_type);
			}
			System.out.println(" __________________Packet Content________________");
			System.out.println("RequestLine:---> "+RequestLine);

			StringTokenizer tokens = new StringTokenizer(RequestLine);
			String request_type_string = tokens.nextToken();

			//convert to request_type structure:
			if ((new String(request_type_string)).compareTo("SETUP") == 0)
				request_type = SETUP;
			else if ((new String(request_type_string)).compareTo("PLAY") == 0)
				request_type = PLAY;
			else if ((new String(request_type_string)).compareTo("PAUSE") == 0)
				request_type = PAUSE;
			else if ((new String(request_type_string)).compareTo("TEARDOWN") == 0)
				request_type = TEARDOWN;

			if (request_type == SETUP){
				//extract VideoFileName from RequestLine
				String cameraSelection = tokens.nextToken();
				System.out.println("Select camera:---> "+cameraSelection);
			}

			//parse the SeqNumLine and extract CSeq field
			String SeqNumLine = RTSPBufferedReader.readLine();
			System.out.println("SeqNumLine:---> "+SeqNumLine);
			tokens = new StringTokenizer(SeqNumLine);
			tokens.nextToken();
			RTSPSeqNb = Integer.parseInt(tokens.nextToken());

			//get LastLine
			String LastLine = RTSPBufferedReader.readLine();
			System.out.println("LastLine:---> "+LastLine);
			System.out.println(" _______________________________________________");

			if (request_type == SETUP)
			{
				//extract RTP_dest_port from LastLine
				tokens = new StringTokenizer(LastLine);
				for (int i=0; i<3; i++)
					tokens.nextToken(); //skip unused stuff
				RTP_dest_port = Integer.parseInt(tokens.nextToken());
			}
			//else LastLine will be the SessionId line ... do not check for now.
		}
		catch(Exception ex)
		{
			if(RequestLine==null)
			{
				//client is offline somehow
				System.out.println("<Vid-Monitoring> Client terminated the session!!!!");
				isConnected = false;
				isSending = false;
			}
			else
			{
				System.out.println("<Vid-Monitoring> Exception caught when parsing client response");
				ex.printStackTrace();			
				System.exit(0);
			}
		}
		return(request_type);
	}

	
	//------------------------------------
	//Send RTSP Response
	//------------------------------------
	public void send_RTSP_response()
	{
		try{
			RTSPBufferedWriter.write("RTSP/1.0 200 OK"+CRLF);
			RTSPBufferedWriter.write("CSeq: "+RTSPSeqNb+CRLF);
			RTSPBufferedWriter.write("Session: "+RTSP_ID+CRLF);
			RTSPBufferedWriter.flush();
		}
		catch(Exception ex)
		{
			System.out.println("<Vid-Monitoring> Exception caught when sending response");
			ex.printStackTrace();
			System.exit(0);
		}
	}
	

}


	