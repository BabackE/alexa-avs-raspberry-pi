package com.nascent.alexa.client;

//com.nascent.alexa.client.AVSAppLaunch
//-------------------------------------
//Launch one of the UX handlers

public class AVSAppLaunch 
{

    public static void main(String[] args) throws Exception 
    {
    	// Amazon's swing UX
    	/*
    	if (args.length == 1) 
    	{
            new com.amazon.alexa.avs.AVSApp(args[0]);
        } 
    	else 
    	{
            new com.amazon.alexa.avs.AVSApp();
        }
        */
    	
    	
    	AVSAppBase app = null;
    	if (args.length == 1) 
    	{
    		// command line ux
            //app = new AVSAppCmdLine(args[0]);
            
            // pocketsphinx + vad ux
            app = new AVSAppKeyphrase(args[0]);
        } 
    	else 
    	{
    		// command line ux
            //app = new AVSAppCmdLine();
            
            // pocketsphinx + vad ux
            app = new AVSAppKeyphrase();
        }
    	
    	app.startInteractionPump();    	
    }
}
