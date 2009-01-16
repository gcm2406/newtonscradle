package com.geekyouup.android.newton;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.hardware.SensorListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.util.AttributeSet;
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
        private boolean mRun = false;
        private SurfaceHolder mSurfaceHolder;
        private boolean mRunning = false;

        
        int mNumberOfBalls = 5;
        final int mFixedBallWidth = 56;
        //rot centers should be calculated based on number of balls, currently hardcoded = bad
        private final int[] mCenterOfRotationX=new int[]{240-mFixedBallWidth*2,240-mFixedBallWidth,240,240+mFixedBallWidth,240+mFixedBallWidth*2};
        private final int mCenterOfRotationY=35;//
        private int[] mBallCenterX=new int[mNumberOfBalls];//{100};
        private int[] mBallCenterY=new int[mNumberOfBalls];//{220};
        private double[] angularVelocity=new double[mNumberOfBalls]; // POSITIVE = CLOCKWISE, NEG=ANTICLOCK
    	private double angleOfGravityVelocity = -3.2;
        private static final float BALL_WEIGHT = 0.3f;
       	private boolean mObjectTouched = false;
    	private int mObjectTouchedId =-1;
    	private boolean isSoundOn = true;

        private double[] mBallVelocity=new double[mNumberOfBalls];//, approximate velocity at step i 
        private double[] mBallAngle=new double[mNumberOfBalls];//0.4;//, approximate angular displacement at step i 
     	final double g=SensorManager.STANDARD_GRAVITY;  // set gravitational acceleration parameter
     	final double L=1;//, length of pendulum (input variable) 
		public static final int mStringLength=180; //pixels
        
        private int mBallWidth;
        private int mBallHeight;
        private int mBallHalfWidth;
        private Paint mLinePaint;
        private MediaPlayer[] mBallSounds = new MediaPlayer[mNumberOfBalls];
        
        public BallsThread(SurfaceHolder surfaceHolder, Context app) {
            // get handles to some important objects
            mSurfaceHolder = surfaceHolder;
            mContext = app;

            Resources res = mContext.getResources();
            mBall = mContext.getResources().getDrawable(R.drawable.ball);
            mBackgroundImage = BitmapFactory.decodeResource(res,R.drawable.background);
            mBallWidth = mBall.getIntrinsicWidth();
            mBallHeight = mBall.getIntrinsicHeight();
            mBallHalfWidth = mBallWidth/2;

            //the Paint object to paint the strings
            mLinePaint = new Paint();
            mLinePaint.setAntiAlias(true);
            mLinePaint.setARGB(255,167, 167, 167);
            mLinePaint.setStrokeWidth(2);
            
            //setup all the balls
            for(int i=0;i<mNumberOfBalls;i++)
            {
            	angularVelocity[i]=0;
            	mBallCenterX[i] = mCenterOfRotationX[i];
            	mBallCenterY[i] = mCenterOfRotationY+mStringLength;
            	mBallVelocity[i]=0;
            	mBallAngle[i]=-3.2; //start angle of balls
            	mBallSounds[i] = MediaPlayer.create(app, R.raw.clink);
            }
        }

        /**
         * Starts the game, setting parameters for the current difficulty.
         */
        public void doStart() {
            synchronized (mSurfaceHolder) {
                //mLastTime = System.currentTimeMillis() + 100;
            	mRunning=true;
            }
        }
        
        public void doPause() {
            synchronized (mSurfaceHolder) {
                //mLastTime = System.currentTimeMillis() + 100;
            	mRunning=false;
            }
        }

        @Override
        public void run() {
            while (mRun) {
            	if(mRunning)
            	{
	                Canvas c = null;
	                try {
		                    c = mSurfaceHolder.lockCanvas(null);
		                    synchronized (mSurfaceHolder) {
			                    	updatePhysics();
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
        private void doDraw(Canvas canvas) {
            // Draw the background image. Operations on the Canvas accumulate
            // so this is like clearing the screen.
           
        	canvas.drawBitmap(mBackgroundImage, 0, 0, null);
    		for(int i=0;i<mNumberOfBalls;i++)
    		{
	            int yTop = (int) (mBallCenterY[i] - mBallHalfWidth);
	            int xLeft = (int) (mBallCenterX[i] - mBallHalfWidth);
	
	            canvas.drawLine(mCenterOfRotationX[i], mCenterOfRotationY,  xLeft + mBallHalfWidth, yTop+mBallHalfWidth, mLinePaint);
	
	            mBall.setBounds(xLeft, yTop, xLeft + mBallWidth, yTop + mBallHeight);
	            mBall.draw(canvas);
    		}
        }
        
        public void setSoundState(boolean soundState)
        {
        	this.isSoundOn = soundState;
        }
        
        private void updatePhysics() {

        		//do the collision detection
        		for(int i=0;i<mNumberOfBalls;i++)
        		{
		        	//physics of the balls for ones not being controlled by touch
	        		boolean objectControlledByTouch = (mObjectTouched && mObjectTouchedId==i);
	    		    if(!objectControlledByTouch)
	    			{
			            double elapsed = 0.025;//(now-mLastTime);
			            
			            //velocity affected by angle of gravity
			            mBallVelocity[i] = mBallVelocity[i] - BALL_WEIGHT*g*Math.sin(mBallAngle[i]-angleOfGravityVelocity)*elapsed;
						
			            //0.995 for constant drag, should be related to 0.5*sq(v)
			            mBallVelocity[i]=mBallVelocity[i]*0.997; //this should be related to square of velocity
						
			            mBallAngle[i] = mBallAngle[i] + mBallVelocity[i]*elapsed/L;
	    			}
	    		    
		        	mBallCenterX[i] = calcXCoordOfBall(i); 
		        	mBallCenterY[i] = calcYCoordOfBall(i);
		        	
        			hitTestBall(i);
        		}
        }
        
        public void hitTestBall(int testBall) //only need to test 2 surrounding balls really
        {
        	//int startPoint = testBall>1?testBall-1:0;
        	//for(int j=startPoint;j<testBall+1;j++)
        	for(int j=0;j<mNumberOfBalls;j++)
        	{
        		if(testBall==j) continue; //skip hit test with self
        		
        		//if distance between the 2 centers of the balls is <= to ball width then switch their momentums
        		double mDistBetweenBalls = Math.sqrt((Math.pow(mBallCenterX[j]-mBallCenterX[testBall],2)
        										+Math.pow(mBallCenterY[j]-mBallCenterY[testBall], 2)));
        		
        		//tranfer momentum between collided and moving balls
        		if(mDistBetweenBalls<mBallWidth)
        		{
        			if(isSoundOn && mBallSounds[j]!=null && !mBallSounds[j].isPlaying())
        			{
        				//mSound.seekTo(0);
        				float vol = (float) (Math.abs(mBallVelocity[j])+Math.abs(mBallVelocity[testBall]));
        				if(vol >1) vol=1;
        				mBallSounds[j].setVolume(vol, vol);
        				mBallSounds[j].start();
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
        				mBallAngle[testBall] = mBallAngle[j];
        				mBallVelocity[testBall]=0;
        				mBallVelocity[j]=0;
        				
    		        	mBallCenterX[testBall] = calcXCoordOfBall(testBall); 
    		        	mBallCenterY[testBall] = calcYCoordOfBall(testBall);
        			}else
        			{
	        			double velJ = mBallVelocity[j];
	        			mBallVelocity[j] = mBallVelocity[testBall];
	        			mBallVelocity[testBall] = velJ;
        				mBallAngle[j] = mBallAngle[testBall];
    		        	
    		        	mBallCenterX[j] = calcXCoordOfBall(j); 
    		        	mBallCenterY[j] = calcYCoordOfBall(j);
        			}
        		}
        	}
        }
        
        //calculates balls x coordinate based on its angle and string hanging point
        public int calcXCoordOfBall(int ballId)
        {
        	return (int) (Math.sin(mBallAngle[ballId])*mStringLength + mCenterOfRotationX[ballId]);
        }
        
        //calculates balls y coordinate based on its angle and string hanging point
        public int calcYCoordOfBall(int ballId)
        {
        	return (int) (-Math.cos(mBallAngle[ballId])*mStringLength + mCenterOfRotationY);
        }
        
    	public void onAccuracyChanged(int arg0, int arg1) {}
    	//see if sensor has changed
    	public void onSensorChanged(int sensor, float[] values) {
    		//accelermetion sensor
    		if(sensor == SensorManager.SENSOR_ACCELEROMETER)
    		{
    			//had to switch these round after going landscape
    			float gravityOffsetX = -values[3]; //use non orientation effected values
    			float gravityOffsetY = values[4];

    			//sort out the changed angle of gravity, the Y Offset switch is due to the graph of tan(theta) jumping
    			if(gravityOffsetY>0) angleOfGravityVelocity = -Math.atan(gravityOffsetX/gravityOffsetY)-Math.PI/2;
    			else angleOfGravityVelocity = -Math.atan(gravityOffsetX/gravityOffsetY)+(Math.PI/2);
     		}
    	}

     	
		public boolean onTouch(View v, MotionEvent event) {
			if(event.getAction() == MotionEvent.ACTION_DOWN)
			{
				float touchX = event.getX();
				float touchY = event.getY();
				
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
			}else if(event.getAction() == MotionEvent.ACTION_MOVE)
			{
				if(mObjectTouched)
				{
					mBallVelocity[mObjectTouchedId]=0;
					
					//translate the roational origin
					double transX = event.getX()-mCenterOfRotationX[mObjectTouchedId];
					double transY = event.getY()-mCenterOfRotationY;
					//figure out the angle
	    			if(transY>0) mBallAngle[mObjectTouchedId] = -Math.atan(transX/transY)+Math.PI;
	    			else mBallAngle[mObjectTouchedId] = -Math.atan(transX/transY);

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
			return false;
		}
    }

    /** Handle to the application context, used to e.g. fetch Drawables. */
    private Context mContext;
    private BallsThread thread;

    public BallsView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // register our interest in hearing about changes to our surface
        SurfaceHolder holder = getHolder();
        holder.addCallback(this);

        // create thread only; it's started in surfaceCreated()
        thread = new BallsThread(holder, context);
        setOnTouchListener(thread);
    }

    /**
     * Fetches the animation thread corresponding to this LunarView.
     * 
     * @return the animation thread
     */
    public BallsThread getThread() {
        return thread;
    }

    /**
     * Standard window-focus override. Notice focus lost so we can pause on
     * focus lost. e.g. user switches to take a call.
     */
    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        //if (!hasWindowFocus) thread.doPause();
       // else thread.resume();
    }

    /* Callback invoked when the surface dimensions change. */
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
            int height) {
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
            } catch (InterruptedException e) {
            }
        }
    }
    
}
