package com.github.mschroeder.github.srdfse;

import java.util.HashMap;
import org.json.JSONObject;

/**
 *
 * @author Markus Schr&ouml;der
 */
public class LangString extends HashMap<String, String> {

    @Override
    public String toString() {
        if(isEmpty())
            return "";
        
        return this.getOrDefault("en", "") + "@en";
    }
    
    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        for(Entry<String, String> e : this.entrySet()) {
            json.put(e.getKey(), e.getValue());
        }
        return json;
    }
    
}
