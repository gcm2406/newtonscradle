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
import android.os.Bundle;
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
        //rot centers
        private final float[] mCenterOfRotationX=new float[]{240-mFixedBallWidth*2,240-mFixedBallWidth,240,240+mFixedBallWidth,240+mFixedBallWidth*2};
        private final float mCenterOfRotationY=35;//
        private double[] mBallCenterX=new double[mNumberOfBalls];//{100};
        private double[] mBallCenterY=new double[mNumberOfBalls];//{220};
        private double[] angularVelocity=new double[mNumberOfBalls]; // POSITIVE = CLOCKWISE, NEG=ANTICLOCK
    	private double angleOfGravityVelocity = -3.2;
        private static final float BALL_WEIGHT = 0.3f;
       	private boolean mObjectHit = false;
    	private int mObjectHitId =-1;
    	private boolean isSoundOn = true;

        private double[] mBallVelocity=new double[mNumberOfBalls];//, approximate velocity at step i 
        private double[] mBallAngle=new double[mNumberOfBalls];//0.4;//, approximate angular displacement at step i 
     	final double g=SensorManager.STANDARD_GRAVITY;  // set gravitational acceleration parameter
     	final double L=1;//, length of pendulum (input variable) 
		public static final double mStringLength=180; //pixels
        
        private int mBallWidth;
        private int mBallHeight;
        private int mBallHalfWidth;
        private Paint mLinePaint;
       // private MediaPlayer[] mBallSounds = new MediaPlayer[mNumberOfBalls];
        
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
            	//mBallSounds[i] = MediaPlayer.create(app, R.raw.clink);
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

        @Override
        public void run() {
            while (mRun) {
                Canvas c = null;
                try {
                    c = mSurfaceHolder.lockCanvas(null);
                    synchronized (mSurfaceHolder) {
                    	if(mRunning)
                    	{
	                    	updatePhysics();
	                        doDraw(c);
                    	}
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
	            int yTop = (int) mBallCenterY[i] - mBallHalfWidth;
	            int xLeft = (int) mBallCenterX[i] - mBallHalfWidth;
	
	            canvas.drawLine(mCenterOfRotationX[i], mCenterOfRotationY,  xLeft + mBallHalfWidth, yTop+mBallHalfWidth, mLinePaint);
	
	            mBall.setBounds(xLeft, yTop, xLeft + mBallWidth, yTop + mBallHeight);
	            mBall.draw(canvas);
    		}
        }
        
        public void setSoundState(boolean soundOn)
        {
        	this.isSoundOn = soundOn;
        }
        
        private void updatePhysics() {

        		//do the collision detection
        		for(int i=0;i<mNumberOfBalls;i++)
        		{
		        	//hitTest
		        	for(int j=0;j<mNumberOfBalls;j++)
		        	{
		        		if(i==j) continue; //skip hit test with self
		        		
		        		//if distance between the 2 centers of the balls is <= to ball width then switch their momentums
		        		double mDistBetweenBalls = Math.sqrt((Math.pow(mBallCenterX[j]-mBallCenterX[i],2)
		        										+Math.pow(mBallCenterY[j]-mBallCenterY[i], 2)));
		        		//switch between
		        		if(mDistBetweenBalls<mBallWidth)
		        		{
		        			
		        			double velJ = mBallVelocity[j];
		        			mBallVelocity[j] = mBallVelocity[i];
		        			mBallVelocity[i] = velJ;

		        			//for 2 balls to collide they must be at the same angle, fix overlaps by doing this.
		        			mBallAngle[i] = mBallAngle[j];
		        			/*
		        			if(isSoundOn && mBallSounds[j]!=null && !mBallSounds[j].isPlaying())
		        			{
		        				//mSound.seekTo(0);
		        				float vol = (float) (Math.abs(mBallVelocity[j])+Math.abs(mBallVelocity[i]));
		        				if(vol >1) vol=1;
		        				mBallSounds[j].setVolume(vol, vol);
		        				mBallSounds[j].start();
		        			}*/
		        			
		        		}
		        	}

		        	//physics of the balls
		        	if(!mObjectHit || (mObjectHit && mObjectHitId!=i))
		        	{
			            double elapsed = 0.025;//(now-mLastTime);
			            
			            //velocity affected by angle of gravity
			            mBallVelocity[i] = mBallVelocity[i] - BALL_WEIGHT*g*Math.sin(mBallAngle[i]-angleOfGravityVelocity)*elapsed;
						
			            //0.995 for constant drag, should be related to 0.5*sq(v)
			            mBallVelocity[i]=mBallVelocity[i]*0.997; //this should be related to square of velocity
						
			            mBallAngle[i] = mBallAngle[i] + mBallVelocity[i]*elapsed/L;
			
			        	mBallCenterX[i] = Math.sin(mBallAngle[i])*mStringLength + mCenterOfRotationX[i]; 
			        	mBallCenterY[i] = -Math.cos(mBallAngle[i])*mStringLength + mCenterOfRotationY;
		        	}
        		}
        }
        
        
    	public void onAccuracyChanged(int arg0, int arg1) {}

	
    	public void onSensorChanged(int sensor, float[] values) {
    		//accelermetion sensor
    		if(sensor == SensorManager.SENSOR_ACCELEROMETER)
    		{
    			//had to switch these round after going landscape
    			float gravityOffsetX = -values[1];
    			float gravityOffsetY = -values[0];

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
				
				for(int i=0;i<mNumberOfBalls;i++)
				{
					//the ball is currently covers mBallX, mBallY to mBallX+mBallWidth, mBallY+mBallHeight
					if(touchX>=mBallCenterX[i]-mBallHalfWidth && touchX<=(mBallCenterX[i]+mBallHalfWidth)
						&& touchY>=mBallCenterY[i]-mBallHalfWidth && touchY <= (mBallCenterY[i]+mBallHalfWidth))
					{
						mObjectHit=true;
						mObjectHitId = i;
						return true;
					}
				}
			}else if(event.getAction() == MotionEvent.ACTION_MOVE)
			{
				if(mObjectHit)
				{
					mBallCenterX[mObjectHitId] = event.getX();
					mBallCenterY[mObjectHitId] = event.getY();
					
					//translate the roational origin
					double transX = mBallCenterX[mObjectHitId]-mCenterOfRotationX[mObjectHitId];
					double transY = mBallCenterY[mObjectHitId]-mCenterOfRotationY;
					//figure out the angle
	    			if(transY>0) mBallAngle[mObjectHitId] = -Math.atan(transX/transY)+Math.PI;
	    			else mBallAngle[mObjectHitId] = -Math.atan(transX/transY);

		        	mBallCenterX[mObjectHitId] = Math.sin(mBallAngle[mObjectHitId])*mStringLength + mCenterOfRotationX[mObjectHitId]; 
		        	mBallCenterY[mObjectHitId] = -Math.cos(mBallAngle[mObjectHitId])*mStringLength + mCenterOfRotationY;

		        	//handle collision detection on neighbouring balls
	    			if(mObjectHitId<mNumberOfBalls-1 && mBallAngle[mObjectHitId]<mBallAngle[mObjectHitId+1]) 
	    			{
	    				mBallAngle[mObjectHitId+1]=mBallAngle[mObjectHitId];
	    				mBallVelocity[mObjectHitId+1]=0;
	    			}
	    			if(mObjectHitId>0 && mBallAngle[mObjectHitId]>mBallAngle[mObjectHitId-1])
	    			{
	    				mBallAngle[mObjectHitId-1]=mBallAngle[mObjectHitId];
	    				mBallVelocity[mObjectHitId-1]=0;
	    			}
		        	
	    			return true;
				}
			}else if(event.getAction()==MotionEvent.ACTION_UP)
			{
				if(mObjectHit)
				{
					//translate the roational origin
					double transX = mBallCenterX[mObjectHitId]-mCenterOfRotationX[mObjectHitId];
					double transY = mBallCenterY[mObjectHitId]-mCenterOfRotationY;
					//figure out the angle
	    			if(transY>0) mBallAngle[mObjectHitId] = -Math.atan(transX/transY)+Math.PI;
	    			else mBallAngle[mObjectHitId] = -Math.atan(transX/transY);

		        	mBallCenterX[mObjectHitId] = Math.sin(mBallAngle[mObjectHitId])*mStringLength + mCenterOfRotationX[mObjectHitId]; 
		        	mBallCenterY[mObjectHitId] = -Math.cos(mBallAngle[mObjectHitId])*mStringLength + mCenterOfRotationY;
	    			mBallVelocity[mObjectHitId]=0;
		        	
	    			//handle collision detection on neighbouring balls
	    			if(mObjectHitId<mNumberOfBalls-1 && mBallAngle[mObjectHitId]<mBallAngle[mObjectHitId+1])
	    			{
	    				mBallAngle[mObjectHitId+1]=mBallAngle[mObjectHitId];
	    				mBallVelocity[mObjectHitId+1]=0;
	    			}
	    			
	    			//handle collision detection on neighbouring balls
	    			if(mObjectHitId>0 && mBallAngle[mObjectHitId]>mBallAngle[mObjectHitId-1])
	    			{
	    				mBallAngle[mObjectHitId-1]=mBallAngle[mObjectHitId];
	    				mBallVelocity[mObjectHitId-1]=0;
	    			}

					mObjectHit=false;
					return true;
				}
			}
			return false;
		}
    }

    /** Handle to the application context, used to e.g. fetch Drawables. */
    private Context mContext;

    /** The thread that actually draws the animation */
    private BallsThread thread;

    public BallsView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // register our interest in hearing about changes to our surface
        SurfaceHolder holder = getHolder();
        holder.addCallback(this);

        // create thread only; it's started in surfaceCreated()
        thread = new BallsThread(holder, context);
        
        setOnTouchListener(thread);
        setFocusable(true); // make sure we get key events
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
       // if (!hasWindowFocus) thread.pause();
        //else thread.resume();
    }

    /**
     * Installs a pointer to the text view used for messages.
     */
    //public void setTextView(TextView textView) {
    //    mStatusText = textView;
    //}

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
