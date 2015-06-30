package ece.meng_project;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.StringTokenizer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;


public class Video extends Activity {
	ImageView iv;
	String ServerHost = MainActivity.ServerHost; 
	BufferedReader RTSPBufferedReader;
	BufferedWriter RTSPBufferedWriter;
	DatagramSocket RTPsocket;
	DatagramPacket rcvdp;
	Socket RTSPsocket;
	int RTP_RCV_PORT = 25000; //port where the client will receive the RTP packets
	int RTSP_server_port = 8554;
	final static String CRLF = "\r\n";
	int RTSPid = 0; //ID of the RTSP session (given by the RTSP Server)
	int RTSPSeqNb = 0;
	boolean stop = false;
	CanvasView canvasView;
	Bitmap imageToDraw;
	String VideoFileName = "camera01";
	PlayVideo pv;
	Handler handler;
//    float BUTTON_X_MAX;
//    float BUTTON_X_MIN;
//    float BUTTON_Y_MAX;
//    float BUTTON_Y_MIN ;
//    float BUTTON_X_WIDTH;
//    float BUTTON_Y_WIDTH;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.e("onCreate", "onCreate");
		super.onCreate(savedInstanceState);
		startVideo();
	}
	
	
	@Override
	protected void onDestroy() {
		Log.e("onDestroy", "onDestroy");
		//stopVideo();
		super.onDestroy();
		
		if(RTPsocket != null) {
			RTPsocket.close();
		}
		if(RTSPsocket != null) {
			try {
				RTSPsocket.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	
	
	@Override
	protected void onRestart() {
		Log.e("onRestart", "onRestart");
		super.onRestart();
		startVideo();
	}
	
	

	@Override
	protected void onPause() {
		Log.e("onPause", "onPause");
		super.onPause();
		stopVideo();
		
		if(RTPsocket != null) {
			RTPsocket.close();
		}
		if(RTSPsocket != null) {
			try {
				RTSPsocket.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}		
	}
	
	
	
	
	private void startVideo() {
    	handler = new Handler();
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		Log.e("startVideo", "startVideo");
		stop = false;
		canvasView = new CanvasView(this);
        setContentView(canvasView);
		// http://stackoverflow.com/questions/20372704/run-asynctask-periodically-in-android
        pv = (PlayVideo) (new PlayVideo()).execute();
	}
	
	
	
	private void stopVideo() {
		Log.e("stopVideo", "stopVideo");
		if(pv == null) {
			Log.e("PlayVideo", "true");
		} else {
			pv.cancel(true);
			Log.e("PlayVideo", "false");
		}
		if(RTSPsocket == null) {
			stop = true;
			Log.e("stop", "true");
		} else {
			try {
				Log.e("stop", "true");
	
				RTSPSeqNb++;
				
				//send_RTSP_request("TEARDOWN", VideoFileName, RTSPSeqNb);
				
				RTSPBufferedWriter = new BufferedWriter(new OutputStreamWriter(RTSPsocket.getOutputStream()) );
				RTSPBufferedWriter.write("TEARDOWN"+" "+VideoFileName+" "+"RTSP/1.0"+CRLF);
				RTSPBufferedWriter.write("CSeq: "+RTSPSeqNb+CRLF);
				RTSPBufferedWriter.write("Session: "+RTSPid+CRLF);
				RTSPBufferedWriter.flush();
				stop = true;

				
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	
	Thread canvasThread;
	
	public class CanvasView extends SurfaceView implements Runnable 
    {
        SurfaceHolder ourHolder;
        Canvas canvas;

        public CanvasView(Context context) {
            super(context);
            ourHolder = getHolder();
            canvasThread = new Thread(this);
            canvasThread.start(); 
        }

        
        public void restart() {
        	handler = new Handler();
        	getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        	stop = false;
            ourHolder = getHolder();
            (new Thread(this)).start();
            (new PlayVideo()).execute();
        }
        
        
        @Override
        public void run() {
    		Bitmap drawable1, background1, drawable2, background2, back;
    		drawable1 = BitmapFactory.decodeResource(getResources(), R.drawable.network_broken);
    		//drawable2 = BitmapFactory.decodeResource(getResources(), R.drawable.button);
    		//back  = BitmapFactory.decodeResource(getResources(), R.drawable.back_button);

            while(!stop)
            {
                if(!ourHolder.getSurface().isValid())
                    continue;
                canvas = ourHolder.lockCanvas();

                Log.e("imageToDraw", "imageToDraw");
                float windowWidth =  canvas.getWidth();
                float windowHeight =  canvas.getHeight();
        		
            	if(imageToDraw == null) {
            		Log.e("image", "null");
            		background1 = Bitmap.createScaledBitmap(drawable1, (int)windowWidth, (int)windowHeight, false);
            		canvas.drawBitmap(background1, 0, 0, null);
            	} else {
            		Log.e("image", "not null");

            		//background2 = Bitmap.createScaledBitmap(drawable2, windowWidth, windowHeight, false);
            		//canvas.drawBitmap(background2, 0, 0, null);
            		
            		Log.e("image", "suffce....");
	                int width = (int)windowWidth;
	                int height = (int) (1.0 * 288 / 384 * width);
	                
	                Bitmap video;
	                video = Bitmap.createScaledBitmap(imageToDraw, width, height, false);
	                canvas.drawBitmap(video, 0, (windowHeight-height)/4, null);
	                
//	                BUTTON_X_MAX = windowWidth / 5 * 3;
//	                BUTTON_X_MIN = windowWidth / 5 * 2;
//	                BUTTON_Y_MAX = windowHeight / 1617 * 1300;
//	                BUTTON_Y_MIN = windowHeight / 1617 * 1200;
//	                BUTTON_X_WIDTH = BUTTON_X_MAX - BUTTON_X_MIN;
//	                BUTTON_Y_WIDTH = BUTTON_Y_MAX - BUTTON_Y_MIN;
//            		back = Bitmap.createScaledBitmap(back, (int)BUTTON_X_WIDTH, (int)BUTTON_Y_WIDTH, false);
//                    canvas.drawBitmap(back, BUTTON_X_MIN, BUTTON_Y_MIN , null);
            	}
                ourHolder.unlockCanvasAndPost(canvas);
            }
        }
        
    }

	
//    @Override
//    public boolean onTouchEvent(MotionEvent event) {
//    	Log.e("shit", "1");
//        if(event.getAction() == event.ACTION_UP)  
//        {
//        	Log.e("shit", "2");
//            float touchX = event.getRawX();
//            float touchY = event.getRawY();
//
//            if(touchX <= BUTTON_X_MAX  && touchX >= BUTTON_X_MIN 
//                && touchY <= BUTTON_Y_MAX  && touchY >= BUTTON_Y_MIN)
//            {
//            	Log.e("shit", "3");
//            	stop = true;
//            	finish();
//            }
//        }
//        return true;
//		//return super.dispatchTouchEvent(event);
//    }
    
	//------------------------------------
	//Parse Server Response
	//------------------------------------
	private int parse_server_response() 
	{
		int reply_code = 0;

		try{
			//parse status line and extract the reply_code:
			String StatusLine = RTSPBufferedReader.readLine();
			//System.out.println("RTSP Client - Received from Server:");
			Log.e("buffer line", StatusLine);

			StringTokenizer tokens = new StringTokenizer(StatusLine);
			tokens.nextToken(); //skip over the RTSP version
			reply_code = Integer.parseInt(tokens.nextToken());

			//if reply code is OK get and print the 2 other lines
			if (reply_code == 200)
			{
				String SeqNumLine = RTSPBufferedReader.readLine();
				System.out.println(SeqNumLine);

				String SessionLine = RTSPBufferedReader.readLine();
				System.out.println(SessionLine);

				//if state == INIT gets the Session Id from the SessionLine
				tokens = new StringTokenizer(SessionLine);
				tokens.nextToken(); //skip over the Session:
				RTSPid = Integer.parseInt(tokens.nextToken());
			}
		}
		catch(Exception ex)
		{
			System.out.println("Exception caught: "+ex);
			System.exit(0);
		}
		return(reply_code);
	}


	//------------------------------------
	//send Response
	//------------------------------------
	private void send_RTSP_request(String request_type, String VideoFileName, int RTSPSeqNb)
	{
		try{
			RTSPBufferedWriter.write(request_type+" "+VideoFileName+" "+"RTSP/1.0"+CRLF);
			RTSPBufferedWriter.write("CSeq: "+RTSPSeqNb+CRLF);
			Log.e("send_RTSP_request", "send_RTSP_request");
			if(request_type.equals("SETUP")) {
				RTSPBufferedWriter.write("Transport: RTP/UDP; client_port= "+RTP_RCV_PORT+CRLF);
			}
			else {
				RTSPBufferedWriter.write("Session: "+RTSPid+CRLF);
			}
			RTSPBufferedWriter.flush();
		}
		catch(Exception ex)
		{
			System.out.println("Exception caught: "+ex);
		}
	}

	
	private class PlayVideo extends AsyncTask<Void, Void, Void> {
		 
		protected Void doInBackground(Void... params) {

			// send the setup message to the serversend_RTSP_request in order to receive the live stream

			Socket socket=null;
			Log.e("here", "here");
			//get video filename to request:
			try{
				socket = new Socket(ServerHost, RTSP_server_port);
				Log.e("socket established", "socket established");
				RTSPsocket = socket;
				Log.e("here12", "here12");
				RTSPBufferedReader = new BufferedReader(new InputStreamReader(RTSPsocket.getInputStream()) );				
				Log.e("here13", "here13");
				RTSPBufferedWriter = new BufferedWriter(new OutputStreamWriter(RTSPsocket.getOutputStream()) );
			}
			catch (Exception ee)
			{
				Log.e("Exception", "Exception");
				if(handler == null)
					Log.e("handler", "null");
				else
					Log.e("handler", "not null");
				showToast("server is not reachable");
				Log.e("destroy", "destroy the activity");
				onPause();
				onDestroy();
				if(socket != null) {
					try {
						socket.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				return null;
			}



			assert RTSPBufferedReader != null;
			assert RTSPBufferedWriter != null;
			Log.e("here1", "here1");
			//Init non-blocking RTPsocket that will be used to receive data


			try{
				//construct a new DatagramSocket to receive RTP packets from the server, on port RTP_RCV_PORT
				RTPsocket = new DatagramSocket(RTP_RCV_PORT);
				RTPsocket.setSoTimeout(10000);
			}
			catch (SocketException se)
			{
				System.out.println("Socket exception: "+se);
				Log.e("DatagramSocket", "Exception");
			}



			Log.e("here2", "here2");

			RTSPSeqNb = 1;
			send_RTSP_request("SETUP", VideoFileName, RTSPSeqNb);
			if(parse_server_response() == 200) {
				RTSPSeqNb++;
				send_RTSP_request("PLAY", VideoFileName, RTSPSeqNb);
				if (parse_server_response() == 200) {
					byte[] buf = new byte[100000];
					Log.e("here3", "here3");

					long prevTime = System.currentTimeMillis();

					while(!stop) {
						//Construct a DatagramPacket to receive data from the UDP socket
						rcvdp = new DatagramPacket(buf, buf.length);
						Log.e("here4", "here4");
						try{
							//receive the DP from the socket:
							RTPsocket.receive(rcvdp);
							Log.e("here5", "here5");

							long currentTime = System.currentTimeMillis();
							if(((currentTime - prevTime)) > 3000L) {
								Log.e("play", "play");
								prevTime = currentTime;
								RTSPSeqNb++;
								send_RTSP_request("PLAY", VideoFileName, RTSPSeqNb);
								/*
									int m = RTSPSeqNb;
									StringBuffer sb = new StringBuffer();
									while(m != 0) {
										sb.insert(0, m % 10);
										m = m / 10;
									}
									String seq = new String(sb);
								 */
								Log.e("play send", "play send");
								Log.e("play send", "" + RTSPSeqNb);
							}
							//create an RTPpacket object from the DP
							RTPpacket rtp_packet = new RTPpacket(rcvdp.getData(), rcvdp.getLength());
							Log.e("here6", "here6");
							//get the payload bitstream from the RTPpacket object
							int payload_length = rtp_packet.getpayload_length();
							Log.e("here7", "here7");
							byte [] payload = new byte[payload_length];
							rtp_packet.getpayload(payload);
							Log.e("here8", "here8");

							imageToDraw = BitmapFactory.decodeByteArray(payload , 0, payload.length);
							/*
								for(int i = 0; i < payload.length; i++) {
									Log.e(Integer.toString(i), Byte.toString(payload[i]));
								}*/
							Log.e("payload length", Integer.toString(payload.length));
							Log.e("first", Byte.toString(payload[0]));
						}
						catch (SocketTimeoutException e) {
							Log.e("timeout", "timeout");
							if(handler == null)
								Log.e("handler", "null");
							else
								Log.e("handler", "not null");
							showToast("network broken");
							Log.e("destroy", "destroy the activity");
							onPause();
							onDestroy();
						} catch (IOException ioe) {
							ioe.printStackTrace();
							Log.e("IOException", "IOException");
						}
					}
				}
			}
			Log.e("return", "return");
			return null;
		}
	}
	
	private void showToast(String condition) {
		final String msg = condition;
		if(handler != null) {
			handler.post(new Runnable() {
				public void run() {
					Log.e("toast", "toast");
					Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT)
							.show();
				}
			});
		}
	}
	
	public void backToMainActivity() {
		Intent i_main = new Intent(getApplicationContext(),MainActivity.class);
	    startActivity(i_main);
	}
	

	
	@Override
	public void onBackPressed() {
		backToMainActivity();
		finish();
	}
}
