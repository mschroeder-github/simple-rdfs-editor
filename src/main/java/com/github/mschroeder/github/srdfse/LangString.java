package com.github.mschroeder.github.srdfse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.json.JSONObject;

/**
 * Map from langtag to string.
 * @author Markus Schr&ouml;der
 */
public class LangString extends HashMap<String, String> {

    @Override
    public String toString() {
        if(isEmpty())
            return "";
        
        List<Entry<String, String>> l = new ArrayList<>(entrySet());
        //sort by longest
        l.sort((o1, o2) -> {
            return Integer.compare(o2.getValue().length(), o1.getValue().length());
        });
        
        if(l.get(0).getValue().trim().isEmpty())
            return "";
        
        if(l.get(0).getKey().isEmpty()) {
            return l.get(0).getValue();
        }
        
        return "\"" + l.get(0).getValue() + "\"@" + l.get(0).getKey();
    }
    
    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        for(Entry<String, String> e : this.entrySet()) {
            json.put(e.getKey(), e.getValue());
        }
        return json;
    }
    
}
