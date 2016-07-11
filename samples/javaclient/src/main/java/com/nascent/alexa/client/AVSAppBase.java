/**
 * Copyright 2015 Nascent Objects, Inc. All Rights Reserved.
 *
 * Author: Baback Elmieh
 * Command line version of the Alexa Voice Service Sample App 
 * 
 */

package com.nascent.alexa.client;

import com.amazon.alexa.avs.AVSAudioPlayerFactory;
import com.amazon.alexa.avs.AVSController;
import com.amazon.alexa.avs.AlertManagerFactory;
import com.amazon.alexa.avs.DialogRequestIdAuthority;
import com.amazon.alexa.avs.ExpectSpeechListener;
import com.amazon.alexa.avs.RecordingRMSListener;
import com.amazon.alexa.avs.RequestListener;
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

// com.nascent.alexa.client.AVSAppBase
// -----------------------------------
// Base class for nascent Alexa apps derived from com.amazon.alexa.avs.AVSApp
// 
// implements: auth, message pump using std in/out, recording and submitting to alexa
//
// externalizes: method for triggering and stopping recording events
// 
public abstract class AVSAppBase implements ExpectSpeechListener, RecordingRMSListener,
        RegCodeDisplayHandler, AccessTokenListener {

	protected static final Logger log = LoggerFactory.getLogger(AVSAppCmdLine.class);

    protected static final String APP_TITLE = "Alexa Voice Service";
    protected static final String START_LABEL = "Start Listening";
    protected static final String STOP_LABEL = "Stop Listening";
    protected static final String PROCESSING_LABEL = "Processing";
    
    protected final AVSController controller;
    protected final DeviceConfig deviceConfig;
    
    protected String accessToken = null;
    protected AuthSetup authSetup;

    // mimics SWING classes that keep state in amazon's implementation
    protected class AppToken
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
    
    protected AppToken actionButton = null;
    protected AppToken tokenTextField = null;
    
    public AVSAppBase() throws Exception {
        this(DeviceConfigUtils.readConfigFile());
    }

    public AVSAppBase(String configName) throws Exception {
        this(DeviceConfigUtils.readConfigFile(configName));
    }

    private AVSAppBase(DeviceConfig config) throws Exception {
        deviceConfig = config;
        controller = new AVSController(this, new AVSAudioPlayerFactory(), new AlertManagerFactory(),
                getAVSClientFactory(deviceConfig), DialogRequestIdAuthority.getInstance());

        // setup auth
        authSetup = new AuthSetup(config, this);
        authSetup.addAccessTokenListener(this);
        authSetup.addAccessTokenListener(controller);
        authSetup.startProvisioningThread();

        System.out.println(getAppTitle());
        System.out.println("Hello! UX interface: " + this.getClass().getName());

        // add state trackers for recording state
        addDeviceField();
        addActionField();

        // kick off controller
        controller.startHandlingDirectives();
        
        // wait until auth is finished
    	controller.playStartupIntro();

    	System.out.println("Waiting for accessToken...");
        while (accessToken == null)
        {
        	try
        	{
        		Thread.sleep(250);
        	}
        	catch(Exception e)
        	{        		
        	}
        }
    }
    
    public void startInteractionPump()
    {
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
            		bQuit = !handleInteraction();    		
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
    
    // starts recording and sending message to alexa    
    protected void doRecord()
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
        
        System.out.println("Recording started at: " + (new Date()).toString());
        controller.startRecording(rmsListener, requestListener);
        
        handleEndRecording();
    }
    
    // to be called to end communication with alexa 
    public void finishProcessing() 
    {
        actionButton.setText(START_LABEL);
        actionButton.setEnabled(true);
        controller.processingFinished();
    }

    // generic rms feedback while recording
    @Override
    public void rmsChanged(int rms) 
    { 
    	for (int i = 0; i < rms; i++)
    		System.out.print(".");
    	System.out.println(rms);
    }
    
    // TRUE = holds the next recording event 
    public boolean interactionPumpIsBusy()
    {
    	return (!actionButton.isEnabled() || !actionButton.getText().equals(START_LABEL));
    }

    @Override
    public void onExpectSpeechDirective() {
    	System.out.println("--- Expect Speech Directive ---");
        Thread thread = new Thread() {
            @Override
            public void run() {
                while (interactionPumpIsBusy() || controller.isSpeaking()) 
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
                doRecord();
            }
        };
        thread.start();
    }

    // flushes the input stream then waits for the next full line before returning
    protected String readCarriageReturnFromCommandLine()
    {
    	// flush the input before reading the next line
    	try
    	{
    		System.in.read(new byte[System.in.available()]);
    	}
    	catch (Exception e)
    	{
        	System.out.println(e.toString());
    	}
    	
    	// now read a full line
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

    
    @Override
    public void displayRegCode(String regCode) {
        String regUrl =
                deviceConfig.getCompanionServiceInfo().getServiceUrl() + "/provision/" + regCode;
        System.out.println("Please register your device by visiting the following website on "
                + "any system and following the instructions:\n" + regUrl
                + "\n\n Press RETURN once completed.");
        
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
    
    // listen and send commands to alexa
    abstract boolean handleInteraction();
    abstract boolean handleEndRecording();
}
