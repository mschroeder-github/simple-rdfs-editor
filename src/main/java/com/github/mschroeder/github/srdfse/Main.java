package com.github.mschroeder.github.srdfse;

/**
 *
 * @author Markus Schr&ouml;der
 */
public class Main {
    public static void main(String[] args) {
        if(args.length > 0 && args[0].equals("server")) {
            Server server = new Server(args);
        } else {
            EditorFrame.showGUI(args);
        }
    }
}
