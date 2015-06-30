package gcmServer;

import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class Content implements Serializable {

	private static final long serialVersionUID = 1L;
	
	public List<String> registration_ids;
    public Map<String,String> data;

    public void addRegId(String regId){
        if(this.registration_ids == null)
        	this.registration_ids = new LinkedList<String>();
        this.registration_ids.add(regId);
    }

    public void createData(String title, String message){
        if(this.data == null)
        	this.data = new HashMap<String,String>();

        this.data.put("title", title);
        this.data.put("message", message);
    }
        
    public Content (String title, String data, String regId)
    {
        addRegId(regId);
    	createData(title, data);
    }    
    
}