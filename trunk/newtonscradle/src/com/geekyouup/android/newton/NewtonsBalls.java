package com.geekyouup.android.newton;

import com.geekyouup.android.newton.BallsView.BallsThread;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

public class NewtonsBalls extends Activity {
    /** Called when the activity is first created. */
	
	private BallsView mBallsView;
	private BallsThread mBallsThread;
	private SensorManager mSensorManager;
	
    private static final int MENU_TOGGLESOUND = 0;
    private static final int MENU_ABOUT = 1;
    private static final int MENU_BALLS = 2;
    private static final int MENU_EXIT = 3;
    private static final int DIALOG_WELCOME=0;
    private boolean isSoundOn = true;
    private static final String PREFS_NAME ="GYUNEWTON";
    private static final String PREFS_SOUND ="SOUNDON";
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
     	   isSoundOn = settings.getBoolean(PREFS_SOUND, true);
     	   mBallsThread.setSoundState(isSoundOn);
        }
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, MENU_TOGGLESOUND, 0, "Toggle Sound").setIcon(android.R.drawable.ic_menu_info_details);
        menu.add(0, MENU_BALLS, 1, "Change balls").setIcon(android.R.drawable.ic_input_get);
        menu.add(0, MENU_ABOUT, 2, "About").setIcon(android.R.drawable.ic_menu_info_details);
        menu.add(0, MENU_EXIT, 3, "Exit").setIcon(android.R.drawable.ic_menu_info_details);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	if(item.getItemId() == MENU_TOGGLESOUND)
    	{
    		isSoundOn = !isSoundOn;
    		mBallsThread.setSoundState(isSoundOn);
    		
    		Toast.makeText(this, "Sound " + (isSoundOn?"on":"off"), Toast.LENGTH_SHORT).show();
    		
            //Make sure the welcome message only appears on first launch
            SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
            if(settings !=null)
            {
	           SharedPreferences.Editor editor = settings.edit();
	           editor.putBoolean(PREFS_SOUND, isSoundOn);
	           editor.commit();
            }
    		return true;
    	}else if(item.getItemId() == MENU_ABOUT)
   	 	{
    		showDialog(DIALOG_WELCOME);
    		return true;
   	 	}else if(item.getItemId() == MENU_BALLS)
   	 	{
   	 		mBallsThread.switchBalls();
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
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(mBallsThread,
                SensorManager.SENSOR_ACCELEROMETER,
                SensorManager.SENSOR_DELAY_UI);
        
        mBallsThread.doStart();
    }
}