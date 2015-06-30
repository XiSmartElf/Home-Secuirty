package ece.meng_project;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.gcm.GoogleCloudMessaging;

// reference: http://hmkcode.com/android-google-cloud-messaging-tutorial/
public class MainActivity extends Activity implements OnClickListener {
	Button btnRegId;
	EditText etRegId;
	GoogleCloudMessaging gcm;
	String regid;
	String PROJECT_NUMBER = "305103839184";

	Button systemSwitch;
	Button video;

	private static final int TURNOFF = 0;
	private static final int TURNON = 1;
	private static final int QUERYSTATE = 2;
	
	private static final int UNREACHABLE = 0;
	private static final int OFF = 1;
	private static final int ON = 2;
	public static final int SYSTEM_CONNECT_PORT = 6789;
	
	int systemState;
	public static String ServerHost ="10.132.3.74"; 
	Handler handler;
	
	
	
	
	/**
	 * @function: enable button click
	 */
	public void setButtonClickable(boolean b) {
		Log.e("button", "button");
		video.setClickable(b);
		systemSwitch.setClickable(b);
		video.setEnabled(b);
		systemSwitch.setEnabled(b); 
	}
	
	
	/**
	 * @function: click button and take actions
	 */
			
	public class HandleOnOff extends Thread {
		int op;
		
		public HandleOnOff(int operation) {
			op = operation;
		}
		
        public void run(){
        	Socket clientSocket = null;
        	DataOutputStream outToServer = null;
        	BufferedReader inFromServer = null;
			try {
				Log.e("clientSocket", "create");

				//create socket for communicating with system control at home server
				clientSocket = new Socket();
				clientSocket.connect(new InetSocketAddress(ServerHost, SYSTEM_CONNECT_PORT),500);
				Log.e("clientSocket", "finish create");
			} 
			catch (IOException e) {
				systemState = UNREACHABLE;
				Log.e("IOException", "IOException");
				e.printStackTrace();
				socketClose(clientSocket);
				return;
			}

			try {
				clientSocket.setSoTimeout(5000);
				outToServer = new DataOutputStream(clientSocket.getOutputStream());
				inFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
			} catch (IOException e) {
				e.printStackTrace();
				Log.e("timeout", "timeout");
				systemState = UNREACHABLE;
				socketClose(clientSocket);
				return;
			}

			
			if(outToServer != null && inFromServer != null) {
				//send a request based on operation type
				try {
					Log.e("try to write", "try to write");
					switch(op) {
					case TURNON:
						outToServer.writeBytes("startRequest\r\n");
						break;
					case TURNOFF:
						outToServer.writeBytes("stopRequest\r\n");
						break;
					case QUERYSTATE:
						outToServer.writeBytes("queryState\r\n");
						break;
					}
					Log.e("write", "write");
				} catch (IOException e) {
					e.printStackTrace();
					Log.e("IOException", "write");
					systemState = UNREACHABLE;
					socketClose(clientSocket);
					return;
				}
				
				//receive a response message from the home server
				String response = null;
				Log.e("try to get response", "try to get response");
				try {
					response = inFromServer.readLine();
					Log.e("read", "read");
				} catch (IOException e) {
					e.printStackTrace();
					Log.e("IOException", "read");
					systemState = UNREACHABLE;
					socketClose(clientSocket);
					return;
				}
				
				//based on the response, we take corresponding actions
				switch(op) {
				case TURNON:
					if(response != null && response.equals("startRequestRecv")) 
						systemState = ON;
					break;
				case TURNOFF:
					if(response != null && response.equals("stopRequestRecv")) 
						systemState = OFF;
					break;
				case QUERYSTATE:
					if(response != null && response.equals("ON")) {
						systemState = ON;
						Log.e("system is on", "system is on");
					} else if(response != null && response.equals("OFF")) {
						systemState = OFF;
						Log.e("system is off", "system is off");
					}
					break;
				}
				Log.e("response", "response");
				socketClose(clientSocket);				
			}

        }
        
        //close socket
        public void socketClose(Socket socket)
        {
        	if(socket!=null){
	        	try {
					socket.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
        	}
        }
    }
	
	
	
	
	
	/**
	 * @function: show system message
	 */
	
	private void showToast(String condition) {
		Toast.makeText(getApplicationContext(), condition, Toast.LENGTH_SHORT).show();
	}
	
	
	
	/**
	 * @function: choose which system message to show
	 */
	private void setButtonText() {
		switch(systemState) {
		case ON:
			systemSwitch.setText("Turn System Off");
			break;
		case OFF:
			systemSwitch.setText("Turn System On");
			break;
		case UNREACHABLE:
			systemSwitch.setText("Turn System On");
			break;
		}
	}
	
	
	
	
	/**
	 * @function: create activity when on start
	 */
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);	
		setContentView(R.layout.activity_main);
		video = (Button) findViewById(R.id.video);
		systemSwitch = (Button) findViewById(R.id.systemSwitch);
		video.setOnClickListener(this);
		systemSwitch.setOnClickListener(this);


	}

	
	
	@Override
	protected void onResume() {
		super.onResume();
		Log.e("on resume","on resume");
		setButtonClickable(false);
		showToast("Please wait while query system state.....");

		HandleOnOff query = new HandleOnOff(QUERYSTATE);
		query.start();
		try {
			query.join();
		} catch (InterruptedException e) {
			showToast("Error!");
			e.printStackTrace();
		}
		Log.e("on resume","on resume");
		//set button text and enable them
		if(systemState == UNREACHABLE)
			showToast("System is unreachable");
		else
			showToast("System is connected");

		setButtonText();
		setButtonClickable(true);
		
	}



	/**
	 * @function finish the activity when quit
	 */
	@Override
	protected void onDestroy() {
		super.onDestroy();
		finish();
	}

	
	
	/**
	 * @function: when screen button is clicked
	 */
	@Override
	public void onClick(View v) {          
		
		//Disable button clickable
		setButtonClickable(false);
		//here we should query to system state first
		HandleOnOff query = new HandleOnOff(QUERYSTATE);
		query.start();
		try {
			query.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
			setButtonClickable(true);
			showToast("Error!");
			setButtonText();
			return;
		}
		
		//based on system state, we take corresponding action for the button clicked
		switch (v.getId()) 
		{
		//when video button is clicked
		case R.id.video:
			switch(systemState) 
			{
			case ON:
				Intent i_video = new Intent(getApplicationContext(),Video.class);
				startActivity(i_video);
				finish();
				break;
			case OFF:
				showToast("please turn on the system first");
				break;
			case UNREACHABLE:
				showToast("system is not reachable");
				break;
			}
			break;
			
			
			//when system switch button is clicked	
		case R.id.systemSwitch:
			Log.e("click on switch", "click on switch");
			
			switch(systemSwitch.getText().toString())
			{
			case "Turn System Off":
				switch(systemState) 
				{
				case ON:
					HandleOnOff action = new HandleOnOff(TURNOFF);
					action.start();
					try {
						action.join();
					} catch (InterruptedException e1) {
						e1.printStackTrace();
						showToast("Error!");
					}
					if(systemState == UNREACHABLE)
						showToast("System is unreachable");
					else
						showToast("System has been turned off!");
					break;
				case OFF:
					showToast("System is already off");
					break;
				case UNREACHABLE:
					showToast("System is unreachable");
					break;
				}
				break;

			case "Turn System On":
				switch(systemState) 
				{
				case ON:
					showToast("System is already on");
					break;
				case OFF:
					HandleOnOff action = new HandleOnOff(TURNON);
					action.start();
					try {
						action.join();
					} catch (InterruptedException e1) {
						e1.printStackTrace();
						showToast("Error!");
					}
					if(systemState == UNREACHABLE)
						showToast("System is unreachable");
					else
						showToast("System has been turned on!");
					break;
				case UNREACHABLE:
					showToast("System is unreachable");
					break;
				}
				break;
			}
			setButtonText();
			break;
		}
		
		//Enable button clickable
		setButtonClickable(true);
		Log.e("click on switch finished", "click on switch finished");
	}
	
	

	

	/*
	 * The following can let the phone get the registration id so that the
	 * computer can know which phone to send the message to.
	 */

	/*
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		btnRegId = (Button) findViewById(R.id.btnGetRegId);
		etRegId = (EditText) findViewById(R.id.etRegId);

		btnRegId.setOnClickListener(this);
	}

    // how to use GCM: reference: http://hmkcode.com/android-google-cloud-messaging-tutorial/
	public void getRegId() {
		new AsyncTask<Void, Void, String>() {
			@Override
			protected String doInBackground(Void... params) {

				String msg = "";
				try {
					if (gcm == null) {
						gcm = GoogleCloudMessaging
								.getInstance(getApplicationContext());
					}
					regid = gcm.register(PROJECT_NUMBER);
					msg = "Device registered, registration ID=" + regid;
				} catch (IOException ex) {
					msg = "Error :" + ex.getMessage();
					ex.printStackTrace();
					Log.e("Error", msg);

				}
				return msg;
			}

			@Override
			protected void onPostExecute(String msg) {
				etRegId.setText(msg + "\n");
			}
		}.execute(null, null, null);
	}

	@Override
	public void onClick(View v) {
		getRegId();
	}
	 */
}