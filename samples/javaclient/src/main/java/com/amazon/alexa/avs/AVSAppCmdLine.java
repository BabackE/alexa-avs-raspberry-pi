/**
 * Copyright 2015 Nascent Objects, Inc. All Rights Reserved.
 *
 * Author: Baback Elmieh
 * Command line version of the Alexa Voice Service Sample App 
 * 
 */

package com.amazon.alexa.avs;

import com.amazon.alexa.avs.auth.AccessTokenListener;
import com.amazon.alexa.avs.auth.AuthSetup;
import com.amazon.alexa.avs.auth.companionservice.RegCodeDisplayHandler;
import com.amazon.alexa.avs.config.DeviceConfig;
import com.amazon.alexa.avs.config.DeviceConfigUtils;
import com.amazon.alexa.avs.http.AVSClientFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

public class AVSAppCmdLine implements ExpectSpeechListener, RecordingRMSListener,
        RegCodeDisplayHandler, AccessTokenListener {

    private static final Logger log = LoggerFactory.getLogger(AVSAppCmdLine.class);

    private static final String APP_TITLE = "Alexa Voice Service";
    private static final String START_LABEL = "Start Listening";
    private static final String STOP_LABEL = "Stop Listening";
    private static final String PROCESSING_LABEL = "Processing";
    
    public static final long DEFAULT_DURATION = 5000; 
    
    private final AVSController controller;
    private final DeviceConfig deviceConfig;
    
    private String accessToken = null;
    private AuthSetup authSetup;

    private class AppToken
    {
    	String 	m_state;
    	boolean m_enabled;
    	public AppToken()
    	{
    		m_state = START_LABEL;
    		m_enabled = true;
    	}
    	
    	public void setText(String s)
    	{
    		m_state = s;
    	}
    	
    	public String getText()
    	{
    		return m_state;
    	}
    	
    	public void setEnabled(boolean b)
    	{
    		m_enabled = b;
    	}
    	public boolean isEnabled()
    	{ 
    		return (m_enabled == true);
    	}
    }
    
    private AppToken actionButton = null;
    private AppToken tokenTextField = null;
    
    public static void main(String[] args) throws Exception {
        if (args.length == 1) {
            new AVSAppCmdLine(args[0]);
        } else {
            new AVSAppCmdLine();
        }
    }

    public AVSAppCmdLine() throws Exception {
        this(DeviceConfigUtils.readConfigFile());
    }

    public AVSAppCmdLine(String configName) throws Exception {
        this(DeviceConfigUtils.readConfigFile(configName));
    }

    private AVSAppCmdLine(DeviceConfig config) throws Exception {
        deviceConfig = config;
        controller = new AVSController(this, new AVSAudioPlayerFactory(), new AlertManagerFactory(),
                getAVSClientFactory(deviceConfig), DialogRequestIdAuthority.getInstance());

        authSetup = new AuthSetup(config, this);
        authSetup.addAccessTokenListener(this);
        authSetup.addAccessTokenListener(controller);
        authSetup.startProvisioningThread();

        System.out.println(getAppTitle());
        System.out.println("Hello! Command line interface: " + this.getClass().getName());

        addDeviceField();
        addActionField();

        controller.startHandlingDirectives();
        
        System.out.println("Waiting for accessToken...");
        while (accessToken == null)
        {
        	try
        	{
        		Thread.sleep(500);
        	}
        	catch(Exception e)
        	{        		
        	}
        }
        
        // message pump thread
        Thread messagePumpThread = new Thread() {
        	public void run()
        	{
            	boolean bQuit = false;
            	while(!bQuit)
            	{
            		while(interactionPumpIsBusy())
            		{
                        try 
                        {
                            Thread.sleep(500);
                        } 
                        catch (Exception e) 
                        {
            	        	System.out.println(e.toString());
                        }
            		}
            		bQuit = !recordFromCommandLine();    		
            	}
            	System.out.println("Goodbye!");
            	System.exit(0);
        	}
        };
        messagePumpThread.start();
    }

    private String getAppVersion() {
        final Properties properties = new Properties();
        try (final InputStream stream = getClass().getResourceAsStream("/res/version.properties")) {
            properties.load(stream);
            if (properties.containsKey("version")) {
                return properties.getProperty("version");
            }
        } catch (IOException e) {
            log.warn("version.properties file not found on classpath");
        	System.out.println(e.toString());
        }
        return null;
    }

    private String getAppTitle() {
        String version = getAppVersion();
        String title = APP_TITLE;
        if (version != null) {
            title += " - v" + version;
        }
        return title;
    }

    protected AVSClientFactory getAVSClientFactory(DeviceConfig config) {
        return new AVSClientFactory(config);
    }

    private void addDeviceField() {
        String productIdLabel = deviceConfig.getProductId();
        String dsnLabel = deviceConfig.getDsn();

        System.out.println("Device: " + productIdLabel);
        System.out.println("DSN: " + dsnLabel);
    }

    private void addActionField() {
    	actionButton = new AppToken();
    	actionButton.setText(START_LABEL);
    }
    
    private void doRecord(long durationMS)
    {
        final RecordingRMSListener rmsListener = this;
        
        actionButton.setText(STOP_LABEL);
        RequestListener requestListener = new RequestListener() {
            @Override
            public void onRequestSuccess() 
            {
                finishProcessing();
            }

            @Override
            public void onRequestError(Throwable e) 
            {
                log.error("An error occured creating speech request", e);
                System.out.println("Error: " + e.getMessage());
                finishProcessing();
            }
        };
        
        System.out.println("Will record " + Long.toString(durationMS) + "ms");
        controller.startRecording(rmsListener, requestListener);
        System.out.println("Recording started at: " + (new Date()).toString());
        
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
        }, durationMS);
    }
    
    private String readCarriageReturnFromCommandLine()
    {
    	String rc = "";
        InputStreamReader r=new InputStreamReader(System.in);  
        BufferedReader br=new BufferedReader(r);
        try
        {
        	rc = br.readLine();
        }
        catch(Exception e)
        {
        	System.out.println(e.toString());
        }
        return rc;
    }
        
    private boolean recordFromCommandLine()
    {      
    	if (actionButton.getText().equals(START_LABEL)) 
    	{
	        System.out.println("PRESS RETURN TO START RECORDING");
	        if (readCarriageReturnFromCommandLine().equalsIgnoreCase("q"))
	        	return false;
	        
	        controller.onUserActivity();
	        doRecord(DEFAULT_DURATION);
    	}
    	return true;
    }

    public void finishProcessing() 
    {
        actionButton.setText(START_LABEL);
        actionButton.setEnabled(true);
        controller.processingFinished();
    }

    @Override
    public void rmsChanged(int rms) 
    { 
    	float fRMS = ((float)rms/100.0f) * 10.0f;
    	if (fRMS <= 1.0f)
    		fRMS = 1.0f;
    	int dotRMS = (int)fRMS;
    	for (int i = 0; i < dotRMS; i++)
    		System.out.print(".");
    	System.out.println(Integer.toString(rms));
    }
    
    public boolean interactionPumpIsBusy()
    {
    	return (!actionButton.isEnabled() || !actionButton.getText().equals(START_LABEL)
                || controller.isSpeaking());
    }

    @Override
    public void onExpectSpeechDirective() {
    	System.out.println("--- Expect Speech Directive ---");
        Thread thread = new Thread() {
            @Override
            public void run() {
                while (interactionPumpIsBusy()) 
                {
                    try 
                    {
                    	System.out.println("WAITING ON INTERACTION PUMP");
                        Thread.sleep(500);
                    } 
                    catch (Exception e) 
                    {
        	        	System.out.println(e.toString());
                    }
                }
                doRecord(DEFAULT_DURATION);
            }
        };
        thread.start();
    }


    @Override
    public void displayRegCode(String regCode) {
        String regUrl =
                deviceConfig.getCompanionServiceInfo().getServiceUrl() + "/provision/" + regCode;
        System.out.println("Please register your device by visiting the following website on "
                + "any system and following the instructions:\n" + regUrl
                + "\n\n Hit OK once completed.");
        
        readCarriageReturnFromCommandLine();
    }

    @Override
    public synchronized void onAccessTokenReceived(String accessToken) 
    {
        this.accessToken = accessToken;
        if (tokenTextField == null) 
        	tokenTextField = new AppToken();
        tokenTextField.setText(accessToken);
        
    	System.out.print("New Bearer Token: ");
    	System.out.println(accessToken);
    	
        controller.onUserActivity();
    }
}
