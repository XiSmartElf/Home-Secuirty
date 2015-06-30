package gcmServer;

import java.io.File;
import java.io.FileInputStream;

import org.apache.commons.codec.binary.Base64;

public class PictureHandler {
	
	//create contents and send picture
	public static void sendPicture(File file)
	{
        try
        {
	        FileInputStream imageInFile = new FileInputStream(file);
	        byte imageData[] = new byte[(int) file.length()];
	        imageInFile.read(imageData);
	        
	        // Converting Image byte array into Base64 String
	        String imageDataString = encodeImage(imageData);
	        System.out.println(imageDataString.length());
	        
	        if(imageDataString.length()>3000)
	        {
	        	int len = imageDataString.length()/3000+1;
	        	String[] segments = new String[len];
	        	int index=0;
	        	int strLen = imageDataString.length();
	        	for(int i=0; i<len ; i++)
	        	{
	        		if(index+3000> strLen)
		        		segments[i] = imageDataString.substring(index,strLen); //"sequence "+i+":"+

	        		else
	        			segments[i] = imageDataString.substring(index,index +3000);
	        		index = index+3000;     
	        	}
	        	String back="";
	        	for(int i=0; i<len;i++)
	        	{
	        		back = back+segments[i];
	    	        System.out.println("sequence number: "+i); 
	    	        Content content;
	        		if(i==0)
	        			content = new Content(Integer.toString(i+1)+"-"+len,segments[i],App.regId);
	        		if(i==len-1)
	        			content = new Content(Integer.toString(i+1)+"-"+len,segments[i],App.regId);
	        		else
	        			content = new Content(Integer.toString(i+1)+"-"+len,segments[i],App.regId);
		            POST2GCM.post(App.apiKey, content);
	        	}
	    	    imageInFile.close();
	            }
	        else
	        {
	            Content content = new Content("1"+"-1",imageDataString,App.regId);
	            POST2GCM.post(App.apiKey, content);
	        }
        }
        catch(Exception e)
        {
        	e.printStackTrace();
        }
	}
	
    private static String encodeImage(byte[] imageByteArray) {
        return new String(Base64.encodeBase64String(imageByteArray));
    }

        
}
