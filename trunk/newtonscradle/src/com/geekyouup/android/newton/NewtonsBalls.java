package com.geekyouup.android.newton;

import com.geekyouup.android.newton.BallsView.BallsThread;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

public class NewtonsBalls extends Activity {
    /** Called when the activity is first created. */
	
	private BallsView mBallsView;
	private BallsThread mBallsThread;
	private SensorManager mSensorManager;
	
    private static final int MENU_TOGGLESOUND = 0;
    private static final int MENU_TOGGLEACCEL = 4;
    private static final int MENU_FLIP_ORIENTATION = 5;
    private static final int MENU_ABOUT = 1;
    private static final int MENU_BALLS = 2;
    private static final int MENU_EXIT = 3;
    private static final int DIALOG_WELCOME=0;
    private boolean isSoundOn = false;
    private boolean isAccelOn = false;
    private boolean isOrientNormal = true;
    private int mBallsState = 2;
    private static final String PREFS_NAME ="GYUNEWTON";
    private static final String PREFS_SOUND ="SOUNDON";
    private static final String PREFS_ACCEL ="ACCELON";
    private static final String PREFS_CLOCK ="CLOCK";
    private static final String PREFS_BALLS ="BALLS";
    private static final String PREFS_ORIENT ="ORIENTATION";
    private MenuItem mSoundMenuItem;
    private MenuItem mAccelMenuItem;
    
	private PowerManager mPowerMan;
	private PowerManager.WakeLock mWakeLock;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPowerMan = (PowerManager) getSystemService(Context.POWER_SERVICE);
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        mSoundMenuItem= menu.add(0, MENU_TOGGLESOUND, 0, "Sound").setIcon(isSoundOn?android.R.drawable.button_onoff_indicator_on:android.R.drawable.button_onoff_indicator_off);
        mAccelMenuItem = menu.add(0, MENU_TOGGLEACCEL, 1, "Accelerometer").setIcon(isAccelOn?android.R.drawable.button_onoff_indicator_on:android.R.drawable.button_onoff_indicator_off);
        menu.add(0, MENU_FLIP_ORIENTATION, 2, "Flip").setIcon(android.R.drawable.ic_menu_rotate);
        menu.add(0, MENU_BALLS, 3, "Balls").setIcon(android.R.drawable.ic_input_get);
        menu.add(0, MENU_ABOUT, 4, "About").setIcon(android.R.drawable.ic_menu_info_details);
        menu.add(0, MENU_EXIT, 5, "Exit").setIcon(android.R.drawable.ic_lock_power_off );
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	if(item.getItemId() == MENU_TOGGLESOUND)
    	{
    		isSoundOn = !isSoundOn;
    		if(mSoundMenuItem!=null) mSoundMenuItem.setIcon(isSoundOn?android.R.drawable.button_onoff_indicator_on:android.R.drawable.button_onoff_indicator_off);
    		mBallsThread.setSoundState(isSoundOn);
    		
    		Toast.makeText(this, "Sound " + (isSoundOn?"on":"off"), Toast.LENGTH_SHORT).show();
    		
            //Make sure the welcome message only appears on first launch
            saveBoolean(PREFS_SOUND, isSoundOn);
    		return true;
    	}else if(item.getItemId() == MENU_TOGGLEACCEL)
    	{
    		if(isAccelOn)
    		{
    			mSensorManager.unregisterListener(mBallsThread);
    			mBallsThread.setAccelerometer(false);
    			isAccelOn=false;
    		}else
    		{
        		mSensorManager.registerListener(mBallsThread,
                        SensorManager.SENSOR_ACCELEROMETER,
                        SensorManager.SENSOR_DELAY_UI);
        		mBallsThread.setAccelerometer(true);
        		isAccelOn=true;
    		}
    		
    		mAccelMenuItem.setIcon(isAccelOn?android.R.drawable.button_onoff_indicator_on:android.R.drawable.button_onoff_indicator_off);
    		Toast.makeText(this, "Accelerometer " + (isAccelOn?"on":"off"), Toast.LENGTH_SHORT).show();
            saveBoolean(PREFS_ACCEL, isAccelOn);
    	}else if(item.getItemId() == MENU_FLIP_ORIENTATION)
    	{
    		isOrientNormal = !isOrientNormal;
    		mBallsThread.setOrientation(isOrientNormal);
    		saveBoolean(PREFS_ORIENT, isOrientNormal);
    		return true;
    	}else if(item.getItemId() == MENU_ABOUT)
   	 	{
    		showDialog(DIALOG_WELCOME);
    		return true;
   	 	}else if(item.getItemId() == MENU_BALLS)
   	 	{
   	 		nextBallsGraphic();
    		return true;
   	 	}else if(item.getItemId() == MENU_EXIT)
   	 	{
   	 		finish();
   	 		return true;
   	 	}
        return false;
    }

	protected Dialog onCreateDialog(int id) {
		if(id == DIALOG_WELCOME)
		{
			String message = getString(R.string.about);

			AlertDialog dialog = new AlertDialog.Builder(NewtonsBalls.this).create();//new AlertDialog(Bookmarker.this);
			dialog.setTitle(getString(R.string.about_title));
            dialog.setMessage(message);
            dialog.setButton("OK", new DialogInterface.OnClickListener() {
	             public void onClick(DialogInterface dialog, int whichButton) {
	            	//nothing
	             }
	         });
            
            dialog.setCancelable(true);
            return dialog;
		}else return null;
	}
    
    /**
     * Invoked when the Activity loses user focus.
     */
    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(mBallsThread);
        mBallsThread.doPause();
        mWakeLock.release();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        // tell system to use the layout defined in our XML file
        setContentView(R.layout.main);
        
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        // get handles to the LunarView from XML, and its LunarThread
        mBallsView = (BallsView) findViewById(R.id.myBalls);
        mBallsThread = mBallsView.getThread();
        
        //Make sure the welcome message only appears on first launch
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if(settings !=null)
        {
     	   isSoundOn = settings.getBoolean(PREFS_SOUND, false);
     	   mBallsThread.setSoundState(isSoundOn);
     	   
     	   isAccelOn = settings.getBoolean(PREFS_ACCEL, false);
     	   if(!isAccelOn) mBallsThread.setAccelerometer(false);
     	   
     	   mBallsState = settings.getInt(PREFS_BALLS, 2);
     	   setBallsGraphic(mBallsState);
     	   
     	   isOrientNormal = settings.getBoolean(PREFS_ORIENT, true);
     	   if(!isOrientNormal) mBallsThread.setOrientation(isOrientNormal);
     	   mBallsThread.setApp(this);
     	   
     	   mBallsThread.setClockState(settings.getInt(PREFS_CLOCK, 0));
        }
        
        if(isAccelOn)
        {
	        mSensorManager.registerListener(mBallsThread,
	                SensorManager.SENSOR_ACCELEROMETER,
	                SensorManager.SENSOR_DELAY_UI);
        }
        
        mBallsThread.doStart();
        
        mWakeLock = mPowerMan.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "Stopwatch");
        mWakeLock.acquire();
    }
    
    public void saveClockState(int clockState) {saveInt(PREFS_CLOCK,clockState);}
    public void nextBallsGraphic()
    {
    	mBallsState = (++mBallsState)%3;
    	setBallsGraphic(mBallsState);
    }
    
    private void setBallsGraphic(int ballState)
    {
    	int ballGraphic = R.drawable.ball0;
    	if(ballState==1) ballGraphic=R.drawable.ball1;
    	else if(ballState==2) ballGraphic=R.drawable.ball2;

    	mBallsThread.setBallsGraphic(ballGraphic, ballState==2);
    	saveInt(PREFS_BALLS, ballState);
    }
    
    private void saveInt(String key, int value)
    {
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        if(settings !=null)
        {
           SharedPreferences.Editor editor = settings.edit();
           editor.putInt(key, value);
           editor.commit();
        }
    }
    
    private void saveBoolean(String key, boolean value)
    {
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        if(settings !=null)
        {
           SharedPreferences.Editor editor = settings.edit();
           editor.putBoolean(key, value);
           editor.commit();
        }
    }
    
	/*** Capture Back Button and use for browser back, else quit ****/
	public boolean onKeyDown(int keyCode, KeyEvent event) 
	{
		if(keyCode == KeyEvent.KEYCODE_VOLUME_UP)
		{
        	try{((AudioManager)getSystemService(Context.AUDIO_SERVICE)).adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);}catch(Exception e){}
			return true;
		}else if(keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
		{
        	try{((AudioManager)getSystemService(Context.AUDIO_SERVICE)).adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);}catch(Exception e){}
			return true;
		}else //not back button or no history to go back to
		{
			return super.onKeyDown(keyCode, event);
		}
	}
}