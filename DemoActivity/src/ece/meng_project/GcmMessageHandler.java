package ece.meng_project;

import java.io.FileOutputStream;

import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

// reference: http://hmkcode.com/android-google-cloud-messaging-tutorial/
public class GcmMessageHandler extends IntentService {

	String mes;
	String title;
	String[] tuple;
	private Handler handler;

	public GcmMessageHandler() {
		super("GcmMessageHandler");
		handler = new Handler();

	}
	
	
	
	private void cleanUpFileChunks(Context context) {
		String[] files = context.fileList();
		Log.e("file list length", Integer.toString(files.length));
		for(int i = 0; i < files.length; i++) {
			String filePath = files[i];
			context.deleteFile(filePath);
		}
		String[] newFiles = context.fileList();
		Log.e("file list length", Integer.toString(newFiles.length));
	}
	
	
	
	
	private void showToast(String condition) {
		final String msg = condition;
		if(handler != null) {
			handler.post(new Runnable() {
				public void run() {
					Log.e("toast", "toast");
					Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
				}
			});
		}
	}
	
	
	
	
	@Override
	protected void onHandleIntent(final Intent intent) {
		
		Bundle extras = intent.getExtras();
		Context context = getApplicationContext();
		
		title = extras.getString("title");
		mes = extras.getString("message");
				
		String[] titleSplit = title.split("-");
		
		
		// if we receive the data chunk with the title 1-400, the title will be changed to 001-400
		// by a couple of steps:
		// 1. counting the digit difference between 400 and 1, it is 2
		// 2. adding 2 leading '0's to "1"
		int countDigitDifference = titleSplit[1].length() - titleSplit[0].length();
		
		for(int i = 0; i < countDigitDifference; i++)
			title = "0" + title;

		// the file name format is 100-400.bin
		String fileName = title + ".bin";

		//Log.e("fileName", fileName);
			
		// clean up all the file in the file list when receiving the first data chunk
		int seq = Integer.parseInt(title.split("-")[0]);
		if(seq == 1){
			cleanUpFileChunks(context);
			showToast("Receiving alert messages..");
		}
			
	
		try {
			Log.i("here", "here");
			// store the data chunks in the local private file system
			FileOutputStream fos = openFileOutput(fileName, Context.MODE_PRIVATE);
			fos.write(mes.getBytes());
			fos.close();
			Log.e("success", "file written successful!");
		} catch (Exception e) {
			e.printStackTrace();
		}


		String[] files = context.fileList();
		Log.e("success", "file length"+files.length);
		Log.e("success", title);

		// When receiving the whole image, we display it on the screen
		// We can know how many chunks is the image divided into by the file name
		// for example, the file name is 100-400.bin, we can know the image is divided
		// into 400 data chunks
		String aFile = files[0];
		int a = 0, b = 0;
		for(int i = 0; i < aFile.length(); i++) {
			if(aFile.charAt(i) == '-') a = i + 1;
			if(aFile.charAt(i) == '.') b = i;
		}
		int length = Integer.parseInt(aFile.substring(a, b));
		
		// if we haven't received all the data chunks, do nothing
		if(length != files.length)
			return;
		
		// if we received all the data chunks, make notification for the user once the
		// user click on the notification icon, the captured image will be showed
		Log.e("notification", "make notification");
		
		Intent myIntent = new Intent(context, ShowCaptured.class);
		PendingIntent pendingIntent = PendingIntent.getActivity(context, 1, myIntent, PendingIntent.FLAG_CANCEL_CURRENT);

		NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this);
		mBuilder.setSmallIcon(R.drawable.notification_icon);
		mBuilder.setContentTitle("Theft comes in!!!");
		mBuilder.setContentText("Contact 911 immediately!!!");
		mBuilder.setPriority(NotificationCompat.PRIORITY_MAX);
		mBuilder.setDefaults(NotificationCompat.DEFAULT_ALL);
		mBuilder.setContentIntent(pendingIntent);
		mBuilder.setAutoCancel(true);

		NotificationManager mNotificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
		mNotificationManager.notify(100, mBuilder.build());
		Log.e("notification", "complete making notification");

		GcmBroadcastReceiver.completeWakefulIntent(intent);
	}
	

	
}
