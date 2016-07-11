/**
 * Copyright 2015 Nascent Objects, Inc. All Rights Reserved.
 *
 * Author: Baback Elmieh
 * Command line version of the Alexa Voice Service Sample App 
 * 
 */

package com.nascent.alexa.client;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

//com.nascent.alexa.client.AVSAppCmdLine
//--------------------------------------
// implements: command line interaction with alexa = press Enter to toggle through interaction prompts, q/Q exits
public class AVSAppCmdLine extends AVSAppBase
{
    public static final long DEFAULT_DURATION = 5000; 
    
    public AVSAppCmdLine() throws Exception {
        super();
    }

    public AVSAppCmdLine(String configName) throws Exception {
    	super(configName);
    }

    // waits for a new line before kicking off record
	@Override
	boolean handleInteraction() 
	{
    	if (actionButton.getText().equals(START_LABEL)) 
    	{
	        System.out.println("PRESS RETURN TO START RECORDING");
	        if (readCarriageReturnFromCommandLine().equalsIgnoreCase("q"))
	        	return false;
	        
	        controller.onUserActivity();
	        doRecord();
    	}
    	return true;
	}

	// records for DEFAULT_DURATION before stopping recording (called from doRecord in AVSAppBase)
	@Override
	boolean handleEndRecording() 
	{
		Timer timer = new Timer();
        timer.schedule(new TimerTask()
        {
        	@Override
        	public void run()
        	{
                actionButton.setText(PROCESSING_LABEL); // go into processing mode
                actionButton.setEnabled(false);
                controller.stopRecording();
        		System.out.println("Stopped recording at: " + (new Date()).toString());
        	}
        }, DEFAULT_DURATION);

		return true;
	}
}
