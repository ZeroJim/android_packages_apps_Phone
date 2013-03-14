package com.android.phone;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.android.phone.R;

import android.content.ContentUris;
import android.content.Context;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.util.TypedValue;
import android.net.Uri;

public class ImageRing extends View {
	private static final String TAG = "ImageRing";
	
	private static final int OK = 200;
	private static boolean init = false;
	private boolean isImageMove = false;
	
	private Vibrator mVibrator;
	private Paint mPaint;
	private OnTriggerListener mOnTriggerListener;
	private ExecutorService exec;
	
	private int mImageRadius;
	private int defaultImageId;
	private int imageBgId;
	private int imageBgLightId;
	private int imageTopId;
	private int answerId;
	private int refuseId;
	
	private int mAlpha = 255;
	private int mVibrateDuration = 20;
	
	private Bitmap imageBg;
	private Bitmap imageBgLight;
	private Bitmap imageTop;
	private Bitmap holderImage;
	private Bitmap answer;
	private Bitmap refuse;
	
	private RectF imageRectF;
	private RectF headBgRectF;
	private RectF headBgLightRectF;
	private RectF headTopRectF;
	private RectF answerRectF;
	private RectF refuseRectF;
	
	private float[] centPoint	= new float[2];
	private float[] posHold 	= new float[2];
	private float[] posFix		= new float[2];
	private float ringOffset=30;
	private float alphaSelect;
	
	ArrayList<RingItem>				itemList;		// 外环item
	
    public interface OnTriggerListener {
    	public static final int REFUSE = 100;
    	public static final int ANSWER = 101;
        
        public void onTrigger(int target);
    }
    public void setOnTriggerListener(OnTriggerListener mOnTriggerListener){
    	this.mOnTriggerListener = mOnTriggerListener;
    }

	public ImageRing(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		TypedArray a = context.obtainStyledAttributes(attrs,R.styleable.ImageRing);
		init(a);
		a.recycle();
	}
	public ImageRing(Context context, AttributeSet attrs) {
		this(context, attrs,0);
	}
	
	private Handler mHander = new Handler(){
		@Override
		public void handleMessage(Message msg) {
			switch(msg.what){
			case OK:
				log("mHander handleMessage init ok");
				init = true;
				reset();
				ImageRing.this.invalidate();
				exec.execute(bgRunnable);
			}
			super.handleMessage(msg);
		}
	};

	private void init(TypedArray mTypeArray) {
		log("init()");
		isImageMove = false;
		posHold[0] = posHold[1] = 0;
		posFix[0] = posFix[1] = 0;
		
		exec = Executors.newCachedThreadPool();
		mImageRadius 		= (int) mTypeArray.getDimension(R.styleable.ImageRing_radius, mImageRadius);
		ringOffset = mTypeArray.getDimension(R.styleable.ImageRing_ringOffset, ringOffset);
		
		centPoint[0] = centPoint[1] = ringOffset * 2;
		
		mVibrateDuration = mTypeArray.getInt(R.styleable.ImageRing_vibrateDuration, mVibrateDuration);
		
		defaultImageId = getResourceId(mTypeArray,R.styleable.ImageRing_defaultImage);
		imageTopId = getResourceId(mTypeArray,R.styleable.ImageRing_imageTop);
		imageBgId = getResourceId(mTypeArray,R.styleable.ImageRing_imageBg);
		imageBgLightId = getResourceId(mTypeArray,R.styleable.ImageRing_imageBgLight);
		answerId = getResourceId(mTypeArray,R.styleable.ImageRing_answer);
		refuseId = getResourceId(mTypeArray,R.styleable.ImageRing_refuse);
		
		alphaSelect = 255/ringOffset;
		
		mPaint = new Paint();
		mPaint.setColor(Color.GREEN);
		mPaint.setStrokeWidth(10);
		mPaint.setStyle(Paint.Style.FILL_AND_STROKE);
		
		mVibrator = (Vibrator) this.getContext().getSystemService(Context.VIBRATOR_SERVICE);
		exec.execute(backgroundLoad);
	}
	
	private Runnable backgroundLoad = new Runnable(){
		@Override
		public void run() {
		
			imageRectF = new RectF();
			headTopRectF = new RectF();
			answerRectF = new RectF();
			refuseRectF = new RectF();
			headBgRectF = new RectF();
			headBgLightRectF = new RectF();
			
			loadAllBitmap();
			
			itemList = new ArrayList<RingItem>();
			itemList.add(new RingItem(answer	,answer	,1,-1,OnTriggerListener.ANSWER));		// right
			itemList.add(new RingItem(refuse	,refuse	,-1	,-1,OnTriggerListener.REFUSE));		// left
			mHander.sendEmptyMessage(OK);
			init = true;
			reset();
			ImageRing.this.postInvalidate();
			exec.execute(bgRunnable);
			log("mHander sendEmptyMessage(OK)");
		}
	};
	private Runnable bitmapLoad = new Runnable(){
		public void run(){
			loadAllBitmap();
		}
	};
	
	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		boolean needLoad = false;
		if(!init){
			canvas.drawText("please wait", this.getWidth()>>1, this.getHeight()>>1, mPaint);
			log("err!!    init fail   ===== return");
			return;
		}
		if(answer!=null){
			canvas.drawBitmap(answer, answerRectF.left, answerRectF.top, mPaint);// 接听
		}else{
			needLoad = true;
		}
		
		if(refuse!=null){
			canvas.drawBitmap(refuse, refuseRectF.left, refuseRectF.top, mPaint);//挂断
		}else{
			needLoad = true;
		}
		
		if(isImageMove){
			//mPaint.setAlpha(mAlpha);
			if(imageBg!=null){
				canvas.drawBitmap(imageBg, headBgRectF.left+posFix[0], headBgRectF.top+posFix[1], mPaint);	// 白色背景
			}
			if(holderImage!=null){
				canvas.drawBitmap(holderImage, imageRectF.left+posFix[0], imageRectF.top+posFix[1], mPaint);	// 头像
			}
			if(imageTop!=null){
				canvas.drawBitmap(imageTop, headTopRectF.left+posFix[0], headTopRectF.top+posFix[1], mPaint);//半透明层
			}
			//mPaint.setAlpha(255);
		}else{
			if(imageBgLight!=null){
				int alpha = mPaint.getAlpha();
				mPaint.setAlpha(bgAlpha);
				canvas.drawBitmap(imageBgLight, headBgLightRectF.left, headBgLightRectF.top, mPaint);	// 背景
				mPaint.setAlpha(alpha);
			}else{
				needLoad = true;
				log("====err! imageBackgroundLight is null!!!");
			}
			if(imageBg!=null){
				canvas.drawBitmap(imageBg, headBgRectF.left, headBgRectF.top, mPaint);	// 白色背景
			}else{
				needLoad = true;
				log("====err! imageBackgroundWidth is null!!!");
			}
			if(holderImage!=null){
				canvas.drawBitmap(holderImage, imageRectF.left, imageRectF.top, mPaint);		// 头像
			}else{
				needLoad = true;
				log("====err! imageHolder is null!!!");
			}
			if(imageTop!=null){
				canvas.drawBitmap(imageTop, headTopRectF.left, headTopRectF.top, mPaint);//半透明层
			}else{
				needLoad = true;
				log("====err! imageTop is null!!!");
			}
		}
		
		if(needLoad && exec!=null)
			exec.execute(bitmapLoad);
		
	}
	

	
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		boolean holder = false;
		switch(event.getAction()){
		case MotionEvent.ACTION_DOWN:
			isInRing(event);
			holder = true;
			break;
		case MotionEvent.ACTION_UP:
		case MotionEvent.ACTION_CANCEL:
			actionUp(event);
			holder = true;
			break;
		case MotionEvent.ACTION_MOVE:
			actionMove(event);
			holder = true;
			break;
		}
		this.invalidate();
		return holder;
	}
	private boolean isInRing(MotionEvent event){
		float x=event.getX();
		float y=event.getY();
		if((imageRectF.left < x && imageRectF.right > x) && (imageRectF.top<y && imageRectF.bottom>y)){
			isImageMove = true;
			posHold[0] = x;
			posHold[1] = y;
			return true;
		}else
		return false;
	}
	//----------------------------//
	private void actionUp(MotionEvent event) {
		log("actionUp()");
		boolean isActive = false;
		if(mOnTriggerListener!=null){
			RingItem holder = null;
			for(RingItem ri: itemList){
				if(ri.isActive){
					holder = ri;
					isActive = true;
					break;
				}
			}
			if(holder!=null && mOnTriggerListener!=null){
				mOnTriggerListener.onTrigger(holder.funType);
				holder.isActive = false;
				//release();
			}
		}
		//if(!isActive){
			isImageMove = false;
			posHold[0] = posHold[1] = 0;
			posFix[0] = posFix[1] = 0;
		//}
	}
	//----------------------------//
	private void actionMove(MotionEvent event){
		if(!isImageMove) return;			// 超出可移动范围
		
		float eventX =  event.getX();
       //float eventY =  event.getY();
		
//		float rx=event.getRawX();
//		float ry=event.getRawY();
        // 位置修正
       /* {
			float tx = eventX - posHold[0] ;		// 圆心移动的距离x
			float ty = eventY - posHold[1] ;
			float tRadius = (float) Math.sqrt((tx*tx)+(ty*ty));
			if(tRadius>=moveOffset){
			    float scale = bgRadius / tRadius;
			    eventX = posHold[0]+tx*scale;
			    eventY = posHold[1]+ty*scale;
			}
        }*/
		posFix[0] = eventX - posHold[0];
		//posFix[1] = eventY - posHold[1];
		
		for(int i=0;i<itemList.size();i++){
			RingItem ri = itemList.get(i);
			ri.checkPosition(eventX,centPoint[1]);
			if(ri.isActive){
				if(ri.isActive!=ri.oldActive)
					vibrate();
				posFix[0] = ri.position * ringOffset;
				log("active: "+ri.funType);
			}
		}
		
		
		// 透明度
		//float distance = alphaSection * FloatMath.sqrt(posFix[0]*posFix[0]+posFix[1]*posFix[1]);
		float distance = alphaSelect * Math.abs(posFix[0]);
		mAlpha = (int)(distance>255? 0: 255-distance);
		//logd("distance="+distance+" || ["+posFix[0]+","+posFix[1]+"]");
		//distance = distance>255? 255:distance;
		//imagePaint.setAlpha((int)(255-distance));
	}
	
	public void reset(){
		log(" on reset ("+this.getWidth()+","+this.getHeight()+")");
		centPoint[0] = (this.getWidth()>0)? (this.getWidth()>>1) : (centPoint[0]);
		centPoint[1] = (this.getHeight()>0 && this.getHeight()<500)? (this.getHeight()>>1) : (centPoint[1]);
		
		if(itemList!=null && itemList.size()>0)
		for(RingItem ri:itemList){
			ri.setCent(centPoint[0] + (ringOffset*ri.position), centPoint[1]);
		}
		if(!init) return;
		if(holderImage!=null)
			imageRectF.set(centPoint[0]-(holderImage.getWidth()>>1),
					centPoint[1]-(holderImage.getHeight()>>1), 
					centPoint[0]+(holderImage.getWidth()>>1),
					centPoint[1]+(holderImage.getHeight()>>1));
		if(imageBg!=null)
			headBgRectF.set(centPoint[0]-(imageBg.getWidth()>>1),
					centPoint[1]-(imageBg.getHeight()>>1),
					centPoint[0]+(imageBg.getWidth()>>1),
					centPoint[1]+(imageBg.getHeight()>>1)
				);
		if(imageBgLight!=null)
			headBgLightRectF.set(centPoint[0]-(imageBgLight.getWidth()>>1),
					centPoint[1]-(imageBgLight.getHeight()>>1),
					centPoint[0]+(imageBgLight.getWidth()>>1),
					centPoint[1]+(imageBgLight.getHeight()>>1)
				);
		if(imageTop!=null)
			headTopRectF.set(centPoint[0]-(imageTop.getWidth()>>1),
					centPoint[1]-(imageTop.getHeight()>>1),
					centPoint[0]+(imageTop.getWidth()>>1),
					centPoint[1]+(imageTop.getHeight()>>1)
				);
		if(answer!=null)
			answerRectF.set(centPoint[0]+ringOffset-(answer.getWidth()>>1),
					centPoint[1]-(answer.getHeight()>>1),
					centPoint[0]+ringOffset+(answer.getWidth()>>1),
					centPoint[1]+(answer.getHeight()>>1)
				);
		if(refuse!=null)
			refuseRectF.set(centPoint[0]-ringOffset-(refuse.getWidth()>>1),
					centPoint[1]-(refuse.getHeight()>>1),
					centPoint[0]-ringOffset+(refuse.getWidth()>>1),
					centPoint[1]+(refuse.getHeight()>>1)
				);
	}
	
	private void loadAllBitmap(){
		log("loadAllBitmap()");
		if(imageBgLight==null)	imageBgLight = loadDrawable(imageBgLightId);
		if(imageBg==null)	imageBg = loadDrawable(imageBgId);
		if(imageTop==null)	imageTop = loadDrawable(imageTopId);
		
		
		if(holderImage==null)	holderImage = loadDrawable(defaultImageId>0? defaultImageId:R.drawable.default_header);
		if(answer==null)	answer = loadDrawable(answerId);
		if(refuse==null)	refuse = loadDrawable(refuseId);
		reset();
	}
	private Bitmap loadDrawable(int rId){
		Bitmap bitmap = BitmapFactory.decodeResource(this.getResources(), rId);
		bitmap.prepareToDraw();
		return bitmap;
	}
	private int getResourceId(TypedArray mTypeArray, int id) {
        TypedValue tv = null;
        if(mTypeArray!=null)
        	tv = mTypeArray.peekValue(id);
        return tv == null ? 0 : tv.resourceId;
    }
	
	private void release(){
		log("release()");
		if(imageBg!=null){
			imageBg.recycle();
			imageBg = null;
		}
		if(imageBgLight!=null){
			imageBgLight.recycle();
			imageBgLight = null;
		}
		if(imageTop!=null){
			imageTop.recycle();
			imageTop = null;
		}
		if(holderImage!=null){
			holderImage.recycle();
			holderImage = null;
		}
		if(answer!=null){
			answer.recycle();
			answer = null;
		}
		if(refuse!=null){
			refuse.recycle();
			refuse = null;
		}
	}
	///////////////////////////////////////////////////sure //////////////////////////////////////////////////////////
	private void vibrate(){
		if(mVibrator!=null){
			mVibrator.vibrate(mVibrateDuration);
		}
	}
	public void setInComingNumber(String number){
		this.number = number;
		log("on setInComingNumber: "+number);
		//if(isChecked)
			exec.execute(loadHolderImageRunnable);
	}
	void setInComingUri(Uri uri){
		this.uri = uri;
		log("on setInComingUri: "+uri);
		//if(isChecked)
			exec.execute(loadHolderImageRunnable);
	}
	private String number = null;
	private Uri		uri = null;
	private boolean isChecked = true;
	private Runnable loadHolderImageRunnable = new Runnable(){
		public void run(){
			isChecked = false;
			if(number!=null){
				Bitmap temp = holderImage;
				if(uri!=null){
					holderImage = getHolderImage(uri);
				}else{
					holderImage = null;
					log("url==null will use number:"+number);
				}
				
				if(holderImage==null)
					holderImage = getHolderImage(number);
					
				if(holderImage!=null){
					imageRectF.set(centPoint[0]-(holderImage.getWidth()>>1),
								centPoint[1]-(holderImage.getHeight()>>1), 
								centPoint[0]+(holderImage.getWidth()>>1),
								centPoint[1]+(holderImage.getHeight()>>1));
				}else{
					log("err!!!holderImage == null!");
				}
				if(temp!=null)	temp.recycle();
				number = null;
			}
		}
	};
	/**
	 * 圆形框内显示图
	 */
	private static final String[]  project = new String[]{Phone._ID, Phone.PHOTO_THUMBNAIL_URI, Phone.NUMBER};
									
	private Bitmap getHolderImage(String number){
		Bitmap bitmap = null;
		if(number!=null){
			Cursor c = this.getContext().getContentResolver()
								.query(Phone.CONTENT_URI, project, Phone.NUMBER+"='"+number+"'",null,null);
			if(c!=null){
				if(c.moveToFirst()&&c.getString(1)!=null){
					Uri uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, c.getLong(0)); 
					InputStream input = ContactsContract.Contacts.openContactPhotoInputStream(this.getContext().getContentResolver(), uri,true); 
					bitmap = BitmapFactory.decodeStream(input);
				}
				c.close();
			}
		}
		if(bitmap!=null)
			return toRoundBitmap(bitmap,mImageRadius);
		else
			log("bitmap==null  number: "+number);	
		return loadDrawable(defaultImageId>0? defaultImageId:R.drawable.default_header);
	}
	private Bitmap getHolderImage(Uri uri){
		Bitmap bitmap = null;
		if(uri!=null){
			InputStream input = ContactsContract.Contacts.openContactPhotoInputStream(this.getContext().getContentResolver(), uri,true); 
			bitmap = BitmapFactory.decodeStream(input);
		}
		
		if(bitmap!=null)
			return toRoundBitmap(bitmap,mImageRadius);
		else
			log("bitmap==null uri: "+uri);
			
		return loadDrawable(defaultImageId>0? defaultImageId:R.drawable.default_header);
	}
	
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		log("onMeasure");
		int minimumHeight = (mImageRadius+25)<<1;
		int minimumWidth = minimumHeight + 100;
		int computedWidth = resolveMeasured(widthMeasureSpec, minimumWidth);
       int computedHeight = resolveMeasured(heightMeasureSpec, minimumHeight);
        log("onMeasure["+widthMeasureSpec+","+heightMeasureSpec+"]:["+computedWidth+","+computedHeight+"]");
		setMeasuredDimension(computedWidth, computedHeight);
		reset();
	}
	
	private int resolveMeasured(int measureSpec, int desired){
        int result = 0;
        int specSize = MeasureSpec.getSize(measureSpec);
        switch (MeasureSpec.getMode(measureSpec)) {
            case MeasureSpec.UNSPECIFIED:
                result = desired;
                break;
            case MeasureSpec.AT_MOST:
                result = Math.min(specSize, desired);
                break;
            case MeasureSpec.EXACTLY:
            default:
                result = specSize;
        }
        return result;
    }
	
	static PorterDuffXfermode porterDuffXfermode = new PorterDuffXfermode(Mode.SRC_IN);
	
	public static Bitmap toRoundBitmap(Bitmap bitmap, float radius) {
		if (radius <= 0){
			Log.e(VIEW_LOG_TAG, "radius="+radius);
			return null;
		}
		int width = bitmap.getWidth();
		int height = bitmap.getHeight();
		float roundPx;
		float left, top, right, bottom, dst_left, dst_top, dst_right, dst_bottom;
		if (width <= height) {
			roundPx = width / 2;
			top = 0;
			bottom = width;
			left = 0;
			right = width;
			height = width;
			dst_left = 0;
			dst_top = 0;
			dst_right = width;
			dst_bottom = width;
		} else {
			roundPx = height / 2;
			float clip = (width - height) / 2;
			left = clip;
			right = width - clip;
			top = 0;
			bottom = height;
			width = height;
			dst_left = 0;
			dst_top = 0;
			dst_right = height;
			dst_bottom = height;
		}

		Bitmap output = Bitmap.createBitmap(width, height, Config.ARGB_8888);
		Canvas canvas = new Canvas(output);
		final int color = 0xff424242;
		final Paint paint = new Paint();
		final Rect src = new Rect((int) left, (int) top, (int) right,
				(int) bottom);
		final Rect dst = new Rect((int) dst_left, (int) dst_top,
				(int) dst_right, (int) dst_bottom);
		final RectF rectF = new RectF(dst);
		paint.setAntiAlias(true);
		paint.setMaskFilter(new BlurMaskFilter(3, BlurMaskFilter.Blur.NORMAL));
		canvas.drawARGB(0, 0, 0, 0);
		paint.setColor(color);
		canvas.drawRoundRect(rectF, roundPx, roundPx, paint);
		paint.setXfermode(porterDuffXfermode);
		paint.setAntiAlias(true);
		canvas.drawBitmap(bitmap, src, dst, paint);
		bitmap.recycle();

		int w_h = (int) (radius * 2);
		bitmap = Bitmap.createScaledBitmap(output, w_h, w_h, true);
		bitmap.prepareToDraw();
		return bitmap;
	}
	
	public static void log(String msg){
		Log.d(TAG, msg);
	}
	/**
     * Interface definition for a callback to be invoked when the dial
     * is "triggered" by rotating it one way or the other.
     */
	int bgAlpha;
	boolean bgFlag = true;
	Runnable bgRunnable = new Runnable(){
		public void run(){
			while(true){
				if(bgFlag)
					bgAlpha ++;
				else
					bgAlpha --;
				if(bgAlpha==0 || bgAlpha==255) bgFlag = !bgFlag; 
				try {
					if(ImageRing.this!=null)
						ImageRing.this.postInvalidate();
					Thread.sleep(2);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	};

}
