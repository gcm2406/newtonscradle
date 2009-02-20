package com.geekyouup.android.newton;

import java.util.Calendar;
import java.util.HashMap;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.hardware.SensorListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.SoundPool;
import android.util.AttributeSet;
import android.util.FloatMath;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;


/**
 * View that draws, takes touch input.
 */
class BallsView extends SurfaceView implements SurfaceHolder.Callback {
    class BallsThread extends Thread implements SensorListener, OnTouchListener{
        /*
         * State-tracking constants
         */
        public static final int STATE_PAUSE = 2;
        public static final int STATE_READY = 3;
        public static final int STATE_RUNNING = 4;

        /** What to draw for the Lander when it has crashed */
        private Drawable mBall;
        private Bitmap mBackgroundImage;
       // private Bitmap mForegroundImage;
        private boolean mRun = false;
        private SurfaceHolder mSurfaceHolder;
        private boolean mRunning = false;

        
        private static final float PI_F = (float) Math.PI;
        private static final float HALF_PI_F = PI_F/2f;
        
        int mNumberOfBalls = 5;
        final int mFixedBallWidth = 56;
        //rot centers should be calculated based on number of balls, currently hardcoded = bad
        private final int[] mCenterOfRotationX=new int[]{240-mFixedBallWidth*2,240-mFixedBallWidth,240,240+mFixedBallWidth,240+mFixedBallWidth*2};
        private int mCenterOfRotationY=39;//
        private int[] mBallCenterX=new int[mNumberOfBalls];//{100};
        private int[] mBallCenterY=new int[mNumberOfBalls];//{220};
    	private float angleOfGravityVelocity = PI_F;
        private static final float BALL_WEIGHT = 0.3f;
       	private boolean mObjectTouched = false;
    	private int mObjectTouchedId =-1;
    	private boolean isSoundOn = false;
    	private boolean isOrientNormal=true; //rotate around so we can stand device on surface

        private float[] mBallVelocity=new float[mNumberOfBalls];//velocity at step i 
        private float[] mBallAngle=new float[mNumberOfBalls];// angular displacement at step i 
     	final float g=SensorManager.STANDARD_GRAVITY;  // gravitational acceleration parameter
		public int mStringLength=180; //default 180 pixels
        
        private int mBallWidth;
        private int mBallHeight;
        private int mBallHalfWidth;
        private Paint mLinePaint;
        private Paint mTextPaint;
        private Paint mBallTextPaint;
        private SoundPool soundPool; 
        public static final int SOUND_BALL_CLINK = 1;
        private HashMap<Integer, Integer> soundPoolMap; 
        private int clockState = 0; //0=off, 1=on, 2=onballs
        private Context mContext;
        private NewtonsBalls mApp;

        
        public BallsThread(SurfaceHolder surfaceHolder, Context app) {
            // get handles to some important objects
            mSurfaceHolder = surfaceHolder;
            mContext = app;

            Resources res = mContext.getResources();
            mBall = mContext.getResources().getDrawable(R.drawable.ball0);
            mBackgroundImage = BitmapFactory.decodeResource(res,R.drawable.background);
            mBallWidth = mBall.getIntrinsicWidth();
            mBallHeight = mBall.getIntrinsicHeight();
            mBallHalfWidth = mBallWidth/2;

            //the Paint object to paint the strings
            mLinePaint = new Paint();
            mLinePaint.setAntiAlias(true);
            mLinePaint.setARGB(255,167, 167, 167);
            mLinePaint.setStrokeWidth(2);
            
            mTextPaint = new Paint();
            mTextPaint.setAntiAlias(true);
            mTextPaint.setTextSize(50);
            mTextPaint.setARGB(255,255, 0, 0);
            
            mBallTextPaint = new Paint();
            mBallTextPaint.setAntiAlias(true);
            mBallTextPaint.setTextSize(40);
            mBallTextPaint.setARGB(255,255, 255, 255);
            
            initBalls();
            
            soundPool = new SoundPool(10, AudioManager.STREAM_MUSIC, 100);
            soundPoolMap = new HashMap<Integer, Integer>();
            soundPoolMap.put(SOUND_BALL_CLINK, soundPool.load(getContext(), R.raw.clink, 1));
        }

        public void setApp(NewtonsBalls app){this.mApp=app;}
        
        private void initBalls()
        {
            //setup all the balls
            for(int i=0;i<mNumberOfBalls;i++)
            {
            	mBallCenterX[i] = mCenterOfRotationX[i];
            	mBallCenterY[i] = mCenterOfRotationY+mStringLength;
            	mBallVelocity[i]=0;
            	mBallAngle[i]=PI_F; //start angle of balls
            }
        }
        
        
        /**
         * Starts the game, setting parameters for the current difficulty.
         */
        public void doStart() {
            synchronized (mSurfaceHolder) { mRunning=true; }
        }
        
        public void doPause() {
            synchronized (mSurfaceHolder) { mRunning=false;  }
        }

        @Override
        public void run() {
            while (mRun) {
            	if(mRunning) //allow pause without loss of thread
            	{
	                Canvas c = null;
	                try {
		                    c = mSurfaceHolder.lockCanvas(null);
		                    synchronized (mSurfaceHolder) {
			                    	updatePhysics();
			                    	//if(!isOrientNormal) c.rotate(180,240,160);
			                        doDraw(c);
		                    }
	                } finally {
	                    // do this in a finally so that if an exception is thrown
	                    // during the above, we don't leave the Surface in an
	                    // inconsistent state
	                    if (c != null) {
	                        mSurfaceHolder.unlockCanvasAndPost(c);
	                    }
	                }
            	}
            }
        }
        /**
         * Used to signal the thread whether it should be running or not.
         * Passing true allows the thread to run; passing false will shut it
         * down if it's already running. Calling start() after this was most
         * recently called with false will result in an immediate shutdown.
         * 
         * @param b true to run, false to shut down
         */
        public void setRunning(boolean b) {
            mRun = b;
        }

        /* Callback invoked when the surface dimensions change. */
        public void setSurfaceSize(int width, int height) {}

        /**
         * Draws the ship, fuel/speed bars, and background to the provided
         * Canvas.
         */
        long lastClockUpdate = 0L;
        String calTime="";
        String[] ballTexts = new String[5];
        boolean showColon = true;
        int clockXPos = 0;
        private void updateClock()
        {       
        	if(lastClockUpdate < System.currentTimeMillis()-1000)
        	{
            	String calHour = "";
            	String calMin = "";
            	Calendar mCal = Calendar.getInstance();
	        	calHour = mCal.get(Calendar.HOUR)+"";
	        	if(calHour.equals("0") && mCal.get(Calendar.AM_PM)==Calendar.PM) calHour ="12";
	        	
	        	calMin = mCal.get(Calendar.MINUTE)+"";
	        	if(calMin.length()==1) calMin = "0"+calMin;
	        	
	        	calTime = calHour+(showColon?":":" ")+calMin + (mCal.get(Calendar.AM_PM)==Calendar.AM?"am":"pm");

	        	if(calHour.length()==1) calHour = " " +calHour;
	        	if(isOrientNormal)
	            {
		        	ballTexts[0]=calHour.substring(0,1);
		            ballTexts[1] = calHour.substring(1,2);
		            ballTexts[2]= " :";
		            ballTexts[3] = calMin.substring(0,1);
		            ballTexts[4] = calMin.substring(1,2);
	            }else
	            {
		        	ballTexts[4]=calHour.substring(0,1);
		            ballTexts[3] = calHour.substring(1,2);
		            ballTexts[2]= " :";
		            ballTexts[1] = calMin.substring(0,1);
		            ballTexts[0] = calMin.substring(1,2);
	            }

	        	lastClockUpdate = System.currentTimeMillis();
	        	showColon = !showColon;
	        	
	        	float[] charWidths = new float[]{0,0,0,0,0,0,0,0,0};
	        	mTextPaint.getTextWidths(calTime, charWidths);
	        	float textW = 0;
	        	for(int i=0;i<charWidths.length;i++)
	        	{
	        		textW+=charWidths[i];
	        	}
	        	clockXPos = (480-((int) textW))/2;
        	}
        }
        
        private void doDraw(Canvas canvas) {
            // Draw the background image. Operations on the Canvas accumulate
            // so this is like clearing the screen.
        	canvas.drawBitmap(mBackgroundImage, 0, 0, null);
        	if(clockState>0) updateClock();
        	int extraRotation = isOrientNormal?180:0;
    		for(int i=0;i<mNumberOfBalls;i++)
    		{
    			int xCentre = mBallCenterX[i];
    			int yCentre = mBallCenterY[i];
    			
    			int xLeft = (int) (xCentre - mBallHalfWidth);
	            int yTop = (int) (yCentre -mBallHalfWidth);
	            
	            canvas.drawLine(mCenterOfRotationX[i], mCenterOfRotationY,  xCentre, yCentre, mLinePaint);
	            
	            canvas.save();
	            mBall.setBounds(xLeft, yTop, xLeft + mBallWidth, yTop + mBallHeight);
	            //roate the canvas the opposite way to the balls, so when rotated back it is right.
	            canvas.rotate(extraRotation+(float) Math.toDegrees(mBallAngle[i]),xCentre, yCentre);
	            mBall.draw(canvas);
	            if(clockState==2) canvas.drawText(ballTexts[i], xLeft+17, yTop+43, mBallTextPaint);
	            canvas.restore();
    		}
    		
    		if(clockState==1)
    		{
    			if(isOrientNormal) canvas.drawText(calTime, clockXPos, 305, mTextPaint);
    			else
    			{
    	            canvas.save();
    	            canvas.rotate(180, 240,160);
    	            canvas.drawText(calTime, clockXPos, 305, mTextPaint);
     	            canvas.restore();
    			}
    		}
        }
        
        public void setSoundState(boolean soundState)
        {
        	this.isSoundOn = soundState;
        }
        
        private float hitOccured=-1; 
        private void updatePhysics() {

        		//do the collision detection
        		for(int i=0;i<mNumberOfBalls;i++)
        		{
		        	//physics of the balls for ones not being controlled by touch
	        		boolean objectControlledByTouch = (mObjectTouched && mObjectTouchedId==i);
	    		    if(!objectControlledByTouch)
	    			{
			            float elapsed = 0.025f;//(now-mLastTime);
			            
			            //velocity affected by angle of gravity
			            mBallVelocity[i] = mBallVelocity[i] - BALL_WEIGHT*g*FloatMath.sin(mBallAngle[i]-angleOfGravityVelocity)*elapsed;
						
			            //0.995 for constant drag, should be related to 0.5*sq(v)
			            mBallVelocity[i]=mBallVelocity[i]*0.998f; //this should be related to square of velocity
						
			            mBallAngle[i] = mBallAngle[i] + mBallVelocity[i]*elapsed;
	    			}
	    		    
		        	mBallCenterX[i] = calcXCoordOfBall(i); 
		        	mBallCenterY[i] = calcYCoordOfBall(i);
		        	
        			hitTestBall(i);
        		}
        		
        		if(hitOccured!=-1)  //hitoccured is set during hittest
        		{
        			playSound(SOUND_BALL_CLINK, hitOccured);
        			hitOccured=-1;
        		}
        }
        
        public void hitTestBall(int testBall) //only need to test 2 surrounding balls really
        {
        	//hit testing the balls in left to right order
        	// can use this to optimize as unlikely collisions are not checked 
        	//int endPoint = testBall<mNumberOfBalls-1?testBall+2:mNumberOfBalls;
        	for(int j=0;j<mNumberOfBalls;j++)
        	{
        		if(testBall==j) continue; //skip hit test with self
        		
        		//if distance between the 2 centers of the balls is <= to ball width then switch their momentums
        		double mDistBetweenBalls = Math.sqrt((Math.pow(mBallCenterX[j]-mBallCenterX[testBall],2)
        										+Math.pow(mBallCenterY[j]-mBallCenterY[testBall], 2)));
        		
        		//tranfer momentum between collided and moving balls
        		if(mDistBetweenBalls<mBallWidth)
        		{

        			if(isSoundOn)
        			{        				
        				hitOccured = (float) (Math.abs(mBallVelocity[j])+Math.abs(mBallVelocity[testBall]))/8;
        				if(hitOccured >1) hitOccured=1;
        			}
        			
        			//for 2 balls to collide they must be at the same angle, fix overlaps by doing this.
        			if(mObjectTouched && mObjectTouchedId==testBall) //ball being touch dragged overrides angles
        			{
        				mBallAngle[j] = mBallAngle[testBall];
        				mBallVelocity[testBall]=0;
        				mBallVelocity[j]=0;
        				       				
    		        	mBallCenterX[j] = calcXCoordOfBall(j); 
    		        	mBallCenterY[j] = calcYCoordOfBall(j);
        			}else if(mObjectTouched && (mObjectTouchedId==j || (mObjectTouchedId>testBall && j>testBall) ))
        			{
	        			float velJ = mBallVelocity[j];
	        			mBallVelocity[j] = mBallVelocity[testBall];
	        			mBallVelocity[testBall] = velJ;
        				mBallAngle[testBall] = mBallAngle[j];
        				
    		        	mBallCenterX[testBall] = calcXCoordOfBall(testBall); 
    		        	mBallCenterY[testBall] = calcYCoordOfBall(testBall);
        			}else
        			{
	        			float velJ = mBallVelocity[j];
	        			mBallVelocity[j] = mBallVelocity[testBall];
	        			mBallVelocity[testBall] = velJ;
        				mBallAngle[j] = mBallAngle[testBall];
    		        	
    		        	mBallCenterX[j] = calcXCoordOfBall(j); 
    		        	mBallCenterY[j] = calcYCoordOfBall(j);
        			}
        		}
        	}
        }
        
        public void playSound(int sound, float vol) {
            AudioManager mgr = (AudioManager)getContext().getSystemService(Context.AUDIO_SERVICE);
            float streamVolume = mgr.getStreamVolume(AudioManager.STREAM_MUSIC)*vol;
            soundPool.play(soundPoolMap.get(sound), streamVolume, streamVolume, 1, 0, 1f);
        } 
        
        //calculates balls x coordinate based on its angle and string hanging point
        private int calcXCoordOfBall(int ballId)
        {
        	return (int) (FloatMath.sin(mBallAngle[ballId])*mStringLength + mCenterOfRotationX[ballId]);
        }
        
        //calculates balls y coordinate based on its angle and string hanging point
        private int calcYCoordOfBall(int ballId)
        {
        	return (int) (-FloatMath.cos(mBallAngle[ballId])*mStringLength + mCenterOfRotationY);
        }
        
        public void setClockState(int clockState){this.clockState=clockState;}
        public void setBallsGraphic(int ballResource)  {mBall = mContext.getResources().getDrawable(ballResource);}
        public void setOrientation(boolean isOrientNormal)
        {
        	this.isOrientNormal=isOrientNormal;
            Matrix matrix = new Matrix();
            matrix.postRotate(180);
            mBackgroundImage = Bitmap.createBitmap(mBackgroundImage, 0, 0,480, 320, matrix, false); 
            if(isOrientNormal)
            {
            	mCenterOfRotationY = 39;
            	mStringLength=180;
            }else
            {
            	mCenterOfRotationY = 320-39;
            	mStringLength=-180;
            }
        }
        
        public void setAccelerometer(boolean on)
        {
        	if(!on) angleOfGravityVelocity=PI_F;
        }
        
    	public void onAccuracyChanged(int arg0, int arg1) {}
    	//see if sensor has changed
    	public void onSensorChanged(int sensor, float[] values) {
    		//accelermetion sensor
    		if(sensor == SensorManager.SENSOR_ACCELEROMETER)
    		{
    			//had to switch these round after going landscape
    			float gravityOffsetX = (isOrientNormal?-values[3]:values[3]); //use non orientation effected values
    			float gravityOffsetY = (isOrientNormal?values[4]:-values[4]);

    			//sort out the changed angle of gravity, the Y Offset switch is due to the graph of tan(theta) jumping
    			if(gravityOffsetY>0) angleOfGravityVelocity = ((float) (-Math.atan(gravityOffsetX/gravityOffsetY))-HALF_PI_F);
    			else angleOfGravityVelocity = ((float) (-Math.atan(gravityOffsetX/gravityOffsetY))+HALF_PI_F);
     		}
    	}

     	
		public boolean onTouch(View v, MotionEvent event) {
			if(event.getAction() == MotionEvent.ACTION_DOWN)
			{
				float touchX = event.getX();
				float touchY = event.getY();
				
				if((isOrientNormal && touchY>290) || (!isOrientNormal&&touchY<30)) 
				{
					clockState = (clockState+1)%3;
					if(mApp!=null)mApp.saveClockState(clockState);
				}
				else
				{
					//has the user touched a ball?
					for(int i=0;i<mNumberOfBalls;i++)
					{
						//the ball is currently covers mBallX, mBallY to mBallX+mBallWidth, mBallY+mBallHeight
						if(touchX>=mBallCenterX[i]-mBallHalfWidth && touchX<=(mBallCenterX[i]+mBallHalfWidth)
							&& touchY>=mBallCenterY[i]-mBallHalfWidth && touchY <= (mBallCenterY[i]+mBallHalfWidth))
						{
							//if so set flag and id of ball touched
							mObjectTouched=true;
							mObjectTouchedId = i;
							return true;
						}
					}
				}
			}else if(event.getAction() == MotionEvent.ACTION_MOVE)
			{
				if(mObjectTouched)
				{
					mBallVelocity[mObjectTouchedId]=0;
					
					//translate the roational origin
					float transX = event.getX()-mCenterOfRotationX[mObjectTouchedId];
					float transY = event.getY()-mCenterOfRotationY;
					if(!isOrientNormal) {transX=-transX; transY=-transY;}
					
					//figure out the angle the ball is now at
	    			if(transY>0) mBallAngle[mObjectTouchedId] = ((float) (-Math.atan(transX/transY))+PI_F);
	    			else mBallAngle[mObjectTouchedId] = (float) -Math.atan((transX/transY));

	    			return true;
				}
			}else if(event.getAction()==MotionEvent.ACTION_UP)
			{
				if(mObjectTouched)
				{
					mObjectTouched=false;
					return true;
				}
			}
			return true;
		}
    }

    /** Handle to the application context, used to e.g. fetch Drawables. */
    private BallsThread thread;

    public BallsView(Context app, AttributeSet attrs) {
        super(app, attrs);

        // register our interest in hearing about changes to our surface
        SurfaceHolder holder = getHolder();
        holder.addCallback(this);

        // create thread only; it's started in surfaceCreated()
        thread = new BallsThread(holder, app);
        setOnTouchListener(thread);
    }

    /**
     * @return the animation thread
     */
    public BallsThread getThread() {
        return thread;
    }

    /* Callback invoked when the surface dimensions change. */
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        thread.setSurfaceSize(width, height);
    }

    /*
     * Callback invoked when the Surface has been created and is ready to be
     * used.
     */
    public void surfaceCreated(SurfaceHolder holder) {
        // start the thread here so that we don't busy-wait in run()
        // waiting for the surface to be created
        thread.setRunning(true);
        thread.start();
    }

    /*
     * Callback invoked when the Surface has been destroyed and must no longer
     * be touched. WARNING: after this method returns, the Surface/Canvas must
     * never be touched again!
     */
    public void surfaceDestroyed(SurfaceHolder holder) {
        // we have to tell thread to shut down & wait for it to finish, or else
        // it might touch the Surface after we return and explode
        boolean retry = true;
        thread.setRunning(false);
        while (retry) {
            try {
                thread.join();
                retry = false;
            } catch (InterruptedException e) {}
        }
    }
    
}