package com.github.mschroeder.github.srdfse;

import java.util.HashMap;

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
    
    
    
}
