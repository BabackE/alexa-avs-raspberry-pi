package com.nascent.alexa.client;


import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import com.amazon.alexa.avs.AudioCapture;
import com.amazon.alexa.avs.AudioInputFormat;
import com.amazon.alexa.avs.MicrophoneLineFactory;
import com.amazon.alexa.avs.RecordingStateListener;

import edu.cmu.pocketsphinx.Config;
import edu.cmu.pocketsphinx.Decoder;
import edu.cmu.pocketsphinx.Hypothesis;


//com.nascent.alexa.client.AVSAppKeyphrase
//----------------------------------------
//implements: pocketsphinx keyphrase detection & vad silence detection
//
public class AVSAppKeyphrase extends AVSAppBase implements RecordingStateListener 
{
    static 
    {
        System.loadLibrary("pocketsphinx_jni");
    }

    private static final String TRIGGER_PHRASE = "alexa";
	private static final float  KWS_THRESHOLD = 1e-5f;
	
	private static final String PATH_PREFIX = "model";
	private static final String ACOUSTIC_MODEL = PATH_PREFIX + "/en-us/en-us";
	private static final String DICTIONARY = PATH_PREFIX + "/en-us/cmudict-en-us.dict";

	public static final long DEFAULT_DURATION = 8000; 

    private Thread 	autoEndpoint = null; // used to auto-endpoint while listening
    private Timer 	autoKillRecordingTimer = null;

    // minimum audio level threshold under which is considered silence
    private static final int OSX_ENDPOINT_THRESHOLD = 5;
    private static final int NASCENT_ENDPOINT_THRESHOLD = 1;
    
    private int ENDPOINT_THRESHOLD = 1;
    private static final int ENDPOINT_SECONDS = 2; // amount of silence time before endpointing
	
	
	private static final AudioInputFormat SPHINX_AUDIO_TYPE = AudioInputFormat.LPCM;
	
	private Config				m_PocketSphinxConfig = null;
	private boolean				m_bAlexaNeedsMicrophone = false;
	private Decoder 			m_decoder;
	private AudioCapture 		microphone;
	private volatile boolean	m_bMicLock = false;

	public AVSAppKeyphrase() throws Exception 
    {
        super();
        setupSphinxRecognizer();
    }

    public AVSAppKeyphrase(String configName) throws Exception 
    {
    	super(configName);
    	setupSphinxRecognizer();
    }
    
    void setupSphinxRecognizer()
    {
    	String osName = System.getProperty("os.name").toLowerCase();
    	if (osName.contains("mac"))
    	{
    		ENDPOINT_THRESHOLD = OSX_ENDPOINT_THRESHOLD;
        	System.out.println("Configured Endpoint for OSX");
    	}
    	else
    	{
    		ENDPOINT_THRESHOLD = NASCENT_ENDPOINT_THRESHOLD;
        	System.out.println("Configured Endpoint for NASCENT-MAIN");
    	}
    	
    	m_PocketSphinxConfig = Decoder.defaultConfig();
    	m_PocketSphinxConfig.setString("-hmm", ACOUSTIC_MODEL);
    	m_PocketSphinxConfig.setString("-dict", DICTIONARY);
        
    	m_PocketSphinxConfig.setString("-keyphrase", TRIGGER_PHRASE);
    	m_PocketSphinxConfig.setFloat("-kws_threshold", KWS_THRESHOLD);
    	
    	m_PocketSphinxConfig.setBoolean("-allphone_ci", true);
    	m_PocketSphinxConfig.setString("-logfn", "/dev/null");
        
        m_decoder = new Decoder(m_PocketSphinxConfig);
        this.microphone = AudioCapture.getAudioHardware(SPHINX_AUDIO_TYPE.getAudioFormat(), new MicrophoneLineFactory());

        controller.playHello();
        while(controller.isPlayingMP3Resource())
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
    
    public synchronized void blockOnMic() {
    	m_bMicLock = true;
    }

    public synchronized void unblockOnMic() {
    	m_bMicLock = false;
        notify();
    }
    
	@Override
	public boolean handleInteraction() 
	{
        try
        {
        	// don't listen for a keyword if there is a request for the mic from dialog callback 
        	if (m_bAlexaNeedsMicrophone)
        		return true;
        	
        	synchronized(this)
        	{
        		if (m_bMicLock)
        		{
        			System.out.println("POCKETSPHINX  -- WAITING FOR LOCK -- ");
        			wait();
        		}
        		System.out.println("POCKETSPHINX LISTENING");
        		
        		blockOnMic();
        		
        		InputStream inputStream = microphone.getAudioInputStream(this, this);
        		
        		// flush the stream
        		Thread.sleep(50);
        		inputStream.read(new byte[inputStream.available()]);
        		
        		// now start decoding
		        byte[] b = new byte[40960];
		        short[] s = new short[40960];
		        int nbytes;
	
		        m_decoder.startUtt();
		        
		        // continue listening until the keyword is detected or alexa needs the microphone for a dialog call back
		        Hypothesis thisHyp = m_decoder.hyp();
		        while ((thisHyp == null) && !m_bAlexaNeedsMicrophone) 
		        {
		        	//System.out.println("AVAILABLE = " + inputStream.available());
		        	nbytes = inputStream.read(b);
		        	//System.out.println("Bytes read = " + Integer.toString(nbytes));
		        	if (nbytes > 0)
		        	{
			            ByteBuffer bb = ByteBuffer.wrap(b, 0, nbytes);
			            bb.order(ByteOrder.LITTLE_ENDIAN);
			            bb.asShortBuffer().get(s, 0, nbytes/2);
			            m_decoder.processRaw(s, nbytes/2, false, false);
			            thisHyp = m_decoder.hyp();
		        	}
		        }
	    		this.microphone.stopCapture();
	    		m_decoder.endUtt();
	
		        // record interaction in this thread if keyword is heard and there isn't a dialog call back waiting
		        if ((thisHyp != null) && !m_bAlexaNeedsMicrophone)
		        {
		        	System.out.println("HEARD ALEXA!!!");
			        controller.onUserActivity();
			        doRecord();	        
		        }
		        else
		        {
		        	System.out.println("---->> YIELDING TO ALEXA");
		        }
		        unblockOnMic();
        	}
        }
        catch(Exception e)
        {
        	System.out.println(e.toString());
        }
		return true;
	}
	
    @Override
    public void onExpectSpeechDirective() 
    {
    	System.out.println("--- Expect Speech Directive ---");
    	try
    	{
    		// first wait until pocketsphinx yields the mic
    		m_bAlexaNeedsMicrophone = true;
	    	synchronized(this)
	    	{
		    	if (m_bMicLock)
		    	{
			    	System.out.println("WAITING FOR POCKETSPHINX THREAD TO YIELD");
			    	wait();
		    	}
		    	blockOnMic();
	    	}
    	}
    	catch(Exception e)
    	{
        	System.out.println(e.toString());
    	}

    	// kick off a thread that waits for any previous interaction to finish before responding on this dialog directive
    	System.out.println("LOCK HELD BY EXPECT SPEECH DIRECTIVE");
        Thread thread = new Thread() {
            @Override
            public void run() 
            {
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
                if (!m_bMicLock)
                {
                	System.out.println("THIS SHOULD NEVER HAPPEN: I DON'T HAVE THE MIC LOCK!");
                }
                doRecord();
                unblockOnMic();
                m_bAlexaNeedsMicrophone = false;
            }
        };
        thread.start();
    }

    @Override
    public void rmsChanged(int rms) 
    {
    	if (super.interactionPumpIsBusy())
    	{
            // if greater than threshold or not recording, kill the autoendpoint thread
            if ((rms == 0) || (rms > ENDPOINT_THRESHOLD)) 
            {
                if (autoEndpoint != null) 
                {
                	System.out.println("RESETING AUTOENDPOINT");
                    autoEndpoint.interrupt();
                    autoEndpoint = null;
                }
            } 
            else if (rms <= ENDPOINT_THRESHOLD) 
            {
                // start the autoendpoint thread if it isn't already running
                if (autoEndpoint == null) 
                {
                	System.out.println("BELOW AUTOENDPOINT THRESHOLD");
                    autoEndpoint = new Thread() 
                    {
                        @Override
                        public void run() 
                        {
                            try 
                            {
                                Thread.sleep(ENDPOINT_SECONDS * 1000);
                                cancelRecording();
                            } 
                            catch (InterruptedException e) 
                            {
                                return;
                            }
                        }
                    };
                    
                    if (autoKillRecordingTimer != null)
                    {
                    	autoKillRecordingTimer.cancel();
                    	autoKillRecordingTimer = null;
                    }
                    
                    autoEndpoint.start();
                }
            }
    	}
    	/*
    	if (!super.interactionPumpIsBusy())
    		System.out.print("PS:");
    	super.rmsChanged(rms);
    	*/
    }
    
    private void cancelRecording()
    {
        actionButton.setText(PROCESSING_LABEL); // go into processing mode
        actionButton.setEnabled(false);
        controller.stopRecording();
		System.out.println("Stopped recording at: " + (new Date()).toString());
    }

	@Override
	public boolean handleEndRecording() 
	{
		System.out.println("handleEndRecording");
		if (autoKillRecordingTimer != null)
		{
			autoKillRecordingTimer.cancel();
			autoKillRecordingTimer = null;
		}
		
		autoKillRecordingTimer = new Timer();
		autoKillRecordingTimer.schedule(new TimerTask()
        {
        	@Override
        	public void run()
        	{
        		cancelRecording();
        		
        	}
        }, DEFAULT_DURATION);
		return true;
	}

	@Override
	public void recordingStarted() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void recordingCompleted() {
		// TODO Auto-generated method stub
		
	}

}


