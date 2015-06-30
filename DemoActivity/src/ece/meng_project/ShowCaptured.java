package ece.meng_project;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

public class ShowCaptured extends Activity implements OnClickListener {

	// http://hmkcode.com/android-google-cloud-messaging-tutorial/
	// https://github.com/selmantayyar/Custom-SMS-Popup/tree/master/CustomSMSPopUp/src/com/selman/CustomSMS
	ImageView iv;
	Button ok;
	Button ignore;
	Button dial;
	byte[] imageByteArray;
	String fileFormat;
	Handler handler;
	boolean firstSaveImage = true;
	
	private static final int UNREACHABLE = 0;
	private static final int OFF = 1;
	private static final int ON = 2;
	int systemState = UNREACHABLE;
	String ServerHost = MainActivity.ServerHost;
	
	Socket clientSocket = null;
	DataOutputStream outToServer = null;
	BufferedReader inFromServer = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.show_capture_image);
		iv = (ImageView) findViewById(R.id.imageView1);
		ok = (Button) findViewById(R.id.ok);
		ok.setOnClickListener(this);
		dial = (Button) findViewById(R.id.mainPageButtonDial);
		dial.setOnClickListener(this);
		handler = new Handler();
		Context context = getApplicationContext();


		String[] files = context.fileList();
		// read the file list in ascending order
		Arrays.sort(files);
		Log.e("fileListLength", Integer.toString(files.length));
		int segCount = files.length;

		for (int i = 0; i < segCount; i++) {
			Log.e("fileName", files[i]);
		}
		String fileString = "";
		for (int i = 0; i < segCount; i++) {

			try {
				String filePath = files[i];
				FileInputStream fis = openFileInput(filePath);

				InputStreamReader inputStreamReader = new InputStreamReader(fis);
				BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
				StringBuilder sb = new StringBuilder();
				String line;
				while ((line = bufferedReader.readLine()) != null) {
					sb.append(line);
				}

				inputStreamReader.close();
				fis.close();
				bufferedReader.close();

				fileString += sb.toString();
				context.deleteFile(filePath);
				//Log.e("fileString", sb.toString());

			} catch (FileNotFoundException e) {
				Log.e("readerror", "FileNotFoundException");
				showToast("Error occurred while showing message!");
				Intent backToMain = new Intent(getApplicationContext(),MainActivity.class);
				startActivity(backToMain);
				finish();
			} catch (Exception e) {
				Log.e("readerror", "OtherException");
				e.printStackTrace();
				showToast("Error occurred while showing message!");
				Intent backToMain = new Intent(getApplicationContext(),MainActivity.class);
				startActivity(backToMain);
				finish();
			}

		}

		// decode the Base64 encoded string to byte array 
		imageByteArray = decodeImage(fileString);
		saveImage();
		Bitmap bm = BitmapFactory.decodeByteArray(imageByteArray, 0, imageByteArray.length);
		iv.setImageBitmap(bm);
		Log.e("fileListLength", Integer.toString(context.fileList().length));

	}
	

	
	
	public static byte[] decodeImage(String imageDataString) {
		return Base64.decode(imageDataString, Base64.NO_WRAP);
	}

	
	
	@Override
	public void onClick(View v) {
		Log.e("id", Integer.toString(v.getId()));
		switch (v.getId()) 
		{
		case R.id.ok:
			Log.e("try to clear warning", "try to clear warning");
			clearWarning cw = new clearWarning();
			cw.start();
			try {
				cw.join();
			} catch (InterruptedException e) {
				showToast("Error when clearing warning!");
				e.printStackTrace();
				return;
			}
			//handle showToast her!
		    showToastBaseOnState();
		    Log.e("warning clear successfully", "warning clear successfully");
			
			Intent i_main = new Intent(getApplicationContext(),MainActivity.class);
		    startActivity(i_main);
		    finish();
		    break;
		case R.id.mainPageButtonDial:
			// make phone call here
			Log.e("dial", "dial");
			// http://stackoverflow.com/questions/22372561/android-dial-a-phone-number-programmatically
			Uri number = Uri.parse("tel:911");
		    Intent callIntent = new Intent(Intent.ACTION_DIAL, number);
		    startActivity(callIntent);
		    break;
		}
	}
	

	
	
	
	public void saveImage() {
		Log.e("save", "save");
		// do stuff;
		String filePath = getFilePath() + ".jpg";
		Log.e("filePath", filePath);
		
		// http://stackoverflow.com/questions/19462213/android-save-images-to-internal-storage
		if(filePath == ".jpg") {
			Log.e("error", "file not created");
		}
		
		try {
			FileOutputStream fos = new FileOutputStream(filePath);
			fos.write(imageByteArray);
			fos.close();
			Log.e("fileWritten", "fileWritten");
		} catch(Exception e) {
			showToast("Error when saving the scene image!");
			e.printStackTrace();
		}
	}
	
	
	
	
	
	
	public void createSocket() {
		try {
			Log.e("clientSocket", "create");
			clientSocket = new Socket();
			clientSocket.connect(new InetSocketAddress(ServerHost, MainActivity.SYSTEM_CONNECT_PORT),500);
		} catch (UnknownHostException e) {
			systemState = UNREACHABLE;
			Log.e("UnknownHostException", "UnknownHostException");
			e.printStackTrace();
			return;
		} catch (IOException e) {
			systemState = UNREACHABLE;
			Log.e("IOException", "IOException");
			e.printStackTrace();
			return;
		}

		try {
			clientSocket.setSoTimeout(5000);
			outToServer = new DataOutputStream(clientSocket.getOutputStream());
			inFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
		} catch (IOException e) {
			e.printStackTrace();
			systemState = UNREACHABLE;
		}
	}
	
	
	
	public void closeSocket() {
		if(clientSocket != null) {
			try {
				clientSocket.close();
				Log.e("socket closed", "socket closed");
			} catch (IOException e) {
				Log.e("IOException", "IOException");
				e.printStackTrace();
			}
		}
	}
	
	
	
	public class clearWarning extends Thread {
        public void run(){
        	
        	createSocket();
			
        	if(outToServer != null && inFromServer != null) {
        		//query system state before clearing the warning
				String response = null;
				try {
					outToServer.writeBytes("queryState\r\n");
					response = inFromServer.readLine();
				} catch (IOException e1) {
					showToast("Error when clearing the warning!");	
					e1.printStackTrace();
					systemState = UNREACHABLE;
					return;
				}
				Log.e("system is available", "system is available");	
				closeSocket();
				
				// Take action for clearing warning
				if(response != null && response.equals("ON")) {
					createSocket();	
					systemState = ON;
					String clearWarningResponse = null;
					
					try {
						Log.e("try to write", "try to write");
						outToServer.writeBytes("warningClear\r\n");
						Log.e("write", "write");
						Log.e("try to get clearWarningResponse", "try to get clearWarningResponse");
						clearWarningResponse = inFromServer.readLine();
					} catch (IOException e) {
						e.printStackTrace();
						Log.e("IOException", "write");
						showToast("Error when clearing the warning!");	
						systemState = UNREACHABLE;
						return;
					}
					
					Log.e("try to read", "try to read");
					if(clearWarningResponse != null && clearWarningResponse.equals("warningClearRecv")) {
						Log.e("warning clear successfully", "warning clear successfully");
						systemState = ON;
					}
					else
						systemState = UNREACHABLE;
					Log.e("read", "read");
					closeSocket();
					
				} else if(response != null && response.equals("OFF")) {
					systemState = OFF;
				}
			}
        }
    }

	
	@SuppressLint("SimpleDateFormat")
	public String getFilePath() {
		
		// http://stackoverflow.com/questions/19462213/android-save-images-to-internal-storage

		// To be safe, you should check that the SDCard is mounted
		// using Environment.getExternalStorageState() before doing this.

		String dirPath = Environment.getExternalStorageDirectory()
				+ "/Android/data/" + getApplicationContext().getPackageName()
				+ "/Images";
		File mediaStorageDir = new File(dirPath);

		Log.e("directory", dirPath);

		// This location works best if you want the created images to be shared
		// between applications and persist after your app has been uninstalled.

		// Create the storage directory if it does not exist
		if (!mediaStorageDir.exists()) {
			Log.e("error", "directory does not exist");
			if (!mediaStorageDir.mkdirs()) {
				Log.e("error", "cannot make directory");
				return null;
			} else {
				Log.e("error", "directory created");
			}
		} else {
			Log.e("error", "directory exists");
		}

		// Create a media file name based on the current time
		String timeStamp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(new Date());
		String filePath = mediaStorageDir.getPath() + File.separator
				+ timeStamp;

		Log.e("filePath", filePath);
		return filePath;
	}
	
	
	
	private void showToast(String condition) {
		final String msg = condition;
		if(handler != null) {
			handler.post(new Runnable() {
				public void run() {
					Log.e("toast", "toast");
					Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
				}
			});
		}
	}
	
	
	
	public void showToastBaseOnState() {
		switch(systemState) {
		case ON:
			showToast("clear warning sucessfully!");
			break;
		case OFF:
			showToast("System is off! Error at home server or you turned it off");
			break;
		case UNREACHABLE:
			showToast("system is not reachable!");
			break;
		}
	}
	
	
	
	@Override
	public void onBackPressed() {
		// disable back button
	}
	
	
	
	
}