package com.geekyouup.android.newton;

import com.geekyouup.android.newton.BallsView.BallsThread;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.widget.Toast;

public class NewtonsBalls extends Activity {
    /** Called when the activity is first created. */
	
	private BallsView mBallsView;
	private BallsThread mBallsThread;
	private SensorManager mSensorManager;
	
    private static final int MENU_TOGGLESOUND = 0;
    private static final int MENU_ABOUT = 1;
    private static final int MENU_EXIT = 2;
    private static final int DIALOG_WELCOME=0;
    private boolean isSoundOn = true;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // turn off the window's title bar
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // tell system to use the layout defined in our XML file
        setContentView(R.layout.main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        // get handles to the LunarView from XML, and its LunarThread
        mBallsView = (BallsView) findViewById(R.id.myBalls);
        mBallsThread = mBallsView.getThread();        
        mBallsThread.doStart();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, MENU_TOGGLESOUND, 0, "Toggle Sound").setIcon(android.R.drawable.ic_menu_info_details);
        menu.add(0, MENU_ABOUT, 1, "About").setIcon(android.R.drawable.ic_menu_info_details);
        menu.add(0, MENU_EXIT, 2, "Exit").setIcon(android.R.drawable.ic_menu_info_details);
        return true;
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(mBallsThread,
                SensorManager.SENSOR_ALL,
                SensorManager.SENSOR_DELAY_UI);
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	if(item.getItemId() == MENU_TOGGLESOUND)
    	{
    		isSoundOn = !isSoundOn;
    		mBallsThread.setSoundState(isSoundOn);
    		Toast.makeText(this, "Sound " + (isSoundOn?"on":"off"), Toast.LENGTH_SHORT).show();
    		return true;
    	}else if(item.getItemId() == MENU_ABOUT)
   	 	{
    		showDialog(DIALOG_WELCOME);
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
        mBallsView.getThread().pause(); // pause game when Activity pauses
    }
    
    /**
     * Notification that something is about to happen, to give the Activity a
     * chance to save state.
     * 
     * @param outState a Bundle into which this Activity should save its state
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // just have the View's thread save its state into our Bundle
        super.onSaveInstanceState(outState);
    }
}