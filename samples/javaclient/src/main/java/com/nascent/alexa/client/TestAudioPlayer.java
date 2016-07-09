package com.nascent.alexa.client;

import java.io.InputStream;
import java.util.HashSet;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

import javazoom.jl.player.Player;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.caprica.vlcj.component.AudioMediaPlayerComponent;

public class TestAudioPlayer {
	
	private static final Logger log = LoggerFactory.getLogger(TestAudioPlayer.class);
	
	private final AudioMediaPlayerComponent m_player;
	private final ClassLoader resLoader; // used to load resource files
	private Thread playThread;
	private Object playLock = new Object();
	
	private Player speaker = null;
    private static final String START_SOUND = "res/start.mp3";
    private static final String END_SOUND = "res/stop.mp3";
    private static final String ERROR_SOUND = "res/error.mp3";
    private static final String ALARM_SOUND = "res/alarm.mp3";
    private static final String RESPONSE_SOUND = "res/response.mp3";

    public static void main(String[] args) throws Exception {
    	TestAudioPlayer testAudioPlayer = new TestAudioPlayer();

    	testAudioPlayer.tryPlayingMP3(START_SOUND);
    	testAudioPlayer.tryPlayingMP3(ALARM_SOUND);
    	testAudioPlayer.tryPlayingMP3(RESPONSE_SOUND);
    	//testAudioPlayer.tryPlayingStream();
    	//testAudioPlayer.tryWritingToSource();
    }
	
    public TestAudioPlayer()
    {
    	m_player = new AudioMediaPlayerComponent();
    	resLoader = Thread.currentThread().getContextClassLoader();
    }

    public void tryPlayingMP3(String snd)
    {
    	System.out.println("---- playing " + snd);
    	final InputStream inpStream = resLoader.getResourceAsStream(snd);
    	if (inpStream != null)
    	{
    		try
    		{
    			System.out.println("got file, file size = " + Integer.toString(inpStream.available()));
    		}
    		catch(Exception e)
    		{
    		}
    	}
        playThread = new Thread() {
            @Override
            public void run() {
                synchronized (playLock) {
                    try {
                        speaker = new Player(inpStream);
                        speaker.play();
                    } catch (Exception e) {
                        log.error("An error occurred while trying to play audio", e);
                    } finally {
                        IOUtils.closeQuietly(inpStream);
                    }
                    playLock.notifyAll();
                }
            }
        };
        playThread.start();
    }
    
    public boolean tryPlayingStream()
    {
    	synchronized (m_player.getMediaPlayer()) 
    	{		
    		String url = "https://d29r7idq0wxsiz.cloudfront.net/DigitalMusicDeliveryService/HPS.m3u8?m=m&dmid=208760244&c=cf&f=ts&t=10&bl=256k&s=true&e1=1467094500000&e2=1467095400000&v=V2&h=ce88e7f44bca698cec814d69c86289cb754a50749573e7edf16527609c5d8645";
	        log.debug("playing {}", url);
	        System.out.println("playing " + url);
	
	        if (m_player.getMediaPlayer().startMedia(url)) 
	        {
	        	m_player.getMediaPlayer().setVolume(100);	
	            return true;
	        }
	        return false;
    	}
    }
	
	protected byte[] getByteArray(int length)
	{
		if (byteBuf.length < length)
		{
			byteBuf = new byte[length+1024];
		}
		return byteBuf;
	}

	private byte[]			byteBuf = new byte[4096];
    protected byte[] toByteArray(short[] samples, int offs, int len)
	{
		byte[] b = getByteArray(len*2);
		int idx = 0;
		short s;
		while (len-- > 0)
		{
			s = samples[offs++];
			b[idx++] = (byte)s;
			b[idx++] = (byte)(s>>>8);
		}
		return b;
	}    
    public void tryWritingToSource()
    {
    	SourceDataLine source;
        try
        {
    		AudioFormat fmt = new AudioFormat(22050, 16, 1, true, false);
    		DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt);

    		Line line = AudioSystem.getLine(info);
            if (line instanceof SourceDataLine)
            {
            	System.out.println("shoving random bits into audio source " + info.toString());
            	source = (SourceDataLine)line;
				source.open(fmt);
                source.start();

                short[] samples = new short[22050*2];
                for (int i = 0; i < samples.length; i++)
                {
                	samples[i] = (short)(Math.random() * 65535);
                }
        		byte[] b = toByteArray(samples, 0, samples.length);
        		source.write(b, 0, samples.length*2);

        		source.stop();
        		source.drain();
        		source.flush();
            }
          } 
        catch (RuntimeException ex)
		{
        	throw ex;
		}
		catch (LinkageError ex)
		{
			throw ex;
		}
		catch (LineUnavailableException ex)
		{
			System.out.println(ex.toString());
		}
    }
}
