package gcmServer;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.UnknownHostException;

public class Sim_controller {

	public static void main(String[] args) throws UnknownHostException, IOException
	{
	
		
		 // BufferedReader inFromUser = new BufferedReader( new InputStreamReader(System.in));
		  Socket clientSocket = new Socket("localhost", 6789);
		  clientSocket.setSoTimeout(5000);

		  DataOutputStream outToServer = new DataOutputStream(clientSocket.getOutputStream());
		  BufferedReader inFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
		  outToServer.writeBytes("queryState" + '\n'); // warningClear ,  stopRequest,  startRequest, queryState
		  String clientMess = inFromServer.readLine();
		  System.out.println("FROM SERVER: " + clientMess);
		  clientSocket.close();
		  String mess = null;
		  if(clientMess.equals("ON"))
		  {
			  mess = "stopRequest";
		  }
		  else if(clientMess.equals("OFF"))
			  mess = "startRequest";
		  Socket clientSocket2 = new Socket("localhost", 6789);
		  DataOutputStream outToServer2 = new DataOutputStream(clientSocket2.getOutputStream());
		  BufferedReader inFromServer2 = new BufferedReader(new InputStreamReader(clientSocket2.getInputStream()));
		  outToServer2.writeBytes(mess + '\n'); // warningClear ,  stopRequest,  startRequest, queryState
		  System.out.println("FROM SERVER: " +inFromServer2.readLine() );
		  clientSocket2.close();
		  
	}
	
	
}
