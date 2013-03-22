package com.android.phone;

import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.android.phone.R;

import android.content.ContentUris;
import android.content.Context;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
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
import android.os.SystemProperties;
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
	private static final boolean debug = 
			(PhoneApp.DBG_LEVEL >= 1) && (SystemProperties.getInt("ro.debuggable", 0) == 1);
	
	private static final int OK = 200;
	private static boolean init = false;
	private boolean isImageMove = false;
	
	private boolean isShow = false;
	private boolean isMeasure = false;
	private boolean loadOver = false;
	private boolean isAnmiRuning = false;
	
	private Vibrator mVibrator;
	private Paint mPaint;
	private OnTriggerListener mOnTriggerListener;
	private ExecutorService exec;
	private String number = null;
	private Uri		uri = null;
	
	private int mImageRadius;
	
	private int mAlpha = 255;
	private int mVibrateDuration = 20;

	private Drawable defautlImage;
	private Drawable imageBg;
	private Drawable imageBgLight;
	private Drawable imageTop;
	private Drawable holderImage;
	private Drawable answer;
	private Drawable refuse;
	
	private Rect imageRect = new Rect();
	private Rect headBgRect = new Rect();
	private Rect headBgLightRect = new Rect();
	private Rect headTopRect = new Rect();
	private Rect answerRect = new Rect();
	private Rect refuseRect = new Rect();
	
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
    
	private void init(TypedArray mTypeArray) {
		logd("=================init()");
		long t1 = System.currentTimeMillis();
		isImageMove = false;
		posHold[0] = posHold[1] = 0;
		posFix[0] = posFix[1] = 0;
		
		exec = Executors.newFixedThreadPool(3);
		mImageRadius 		= (int) mTypeArray.getDimension(R.styleable.ImageRing_radius, mImageRadius);
		ringOffset = mTypeArray.getDimension(R.styleable.ImageRing_ringOffset, ringOffset);
		
		centPoint[0] = ringOffset + mImageRadius;
		centPoint[1] = mImageRadius;
		
		mVibrateDuration = mTypeArray.getInt(R.styleable.ImageRing_vibrateDuration, mVibrateDuration);
		
		defautlImage = mTypeArray.getDrawable(R.styleable.ImageRing_defaultImage);
		imageBgLight = mTypeArray.getDrawable(R.styleable.ImageRing_imageBgLight);
		imageBg = mTypeArray.getDrawable(R.styleable.ImageRing_imageBg);
		imageTop = mTypeArray.getDrawable(R.styleable.ImageRing_imageTop);
		answer = mTypeArray.getDrawable(R.styleable.ImageRing_answer);
		refuse = mTypeArray.getDrawable(R.styleable.ImageRing_refuse);
		
		alphaSelect = 255/ringOffset;
		
		mPaint = new Paint();
		mPaint.setColor(Color.GREEN);
		mPaint.setStrokeWidth(10);
		mPaint.setStyle(Paint.Style.FILL_AND_STROKE);
		
		mVibrator = (Vibrator) this.getContext().getSystemService(Context.VIBRATOR_SERVICE);
		
		itemList = new ArrayList<RingItem>();
		itemList.add(new RingItem(answer	,answer	,1,-1,OnTriggerListener.ANSWER));		// right
		itemList.add(new RingItem(refuse	,refuse	,-1	,-1,OnTriggerListener.REFUSE));		// left
		
		init = true;
		reset();
		loge("init time="+(System.currentTimeMillis()-t1));
	}
	
	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		if(!init){
			canvas.drawText("please wait", this.getWidth()>>1, this.getHeight()>>1, mPaint);
			logd("err!!   onDraw init fail   ===== return");
			return;
		}
		if(answer!=null){	answer.draw(canvas);	}else{	loge("====err! onDraw answer=null");}
		
		if(refuse!=null){	refuse.draw(canvas);	}else{	loge("====err!  onDraw refuse=null");}
		
		if(isImageMove){
			if(imageBg!=null){												// 白色背景
				imageBg.setBounds((int)(headBgRect.left + posFix[0]),
								(int)(headBgRect.top + posFix[1]),
								(int)(headBgRect.right + posFix[0]),
								(int)(headBgRect.bottom + posFix[1]));
				imageBg.draw(canvas);
			}else{
				loge("====err!  onMoveDraw imageBackgroundWidth=null");
			}
			if(holderImage!=null){											// 头像
				holderImage.setBounds((int)(imageRect.left + posFix[0]),
								(int)(imageRect.top + posFix[1]),
								(int)(imageRect.right + posFix[0]),
								(int)(imageRect.bottom + posFix[1]));
				holderImage.draw(canvas);
			}else{
				loge("====err!  onMoveDraw imageHolder=null");
			}
			if(imageTop!=null){												//半透明层
				imageTop.setBounds((int)(headTopRect.left + posFix[0]),
								(int)(headTopRect.top + posFix[1]),
								(int)(headTopRect.right + posFix[0]),
								(int)(headTopRect.bottom + posFix[1]));
				imageTop.draw(canvas);
			}else{
				loge("====err!  onMoveDraw imageTop=null");
			}
		}else{
			if(imageBgLight!=null){
				imageBgLight.setAlpha(bgAlpha);
				imageBgLight.draw(canvas);
			}else{
				loge("====err!  onDraw imageBackgroundLight=null");
			}
			if(imageBg!=null){												// 白色背景
				imageBg.draw(canvas);
			}else{
				loge("====err!  onDraw imageBackgroundWidth=null");
			}
			if(holderImage!=null){											// 头像
				holderImage.draw(canvas);
			}else{
				loge("====err!  onDraw imageHolder=null");
			}
			if(imageTop!=null){												//半透明层
				imageTop.draw(canvas);
			}else{
				loge("====err!  onDraw imageTop=null");
			}
		}
		
	}
	

	
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		boolean holder = false;
		switch(event.getAction()){
		case MotionEvent.ACTION_DOWN:
			if(isInRing(event)){
				logd("in ImageRect ");
			}
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
		if((imageRect.left < x && imageRect.right > x) && (imageRect.top<y && imageRect.bottom>y)){
			isImageMove = true;
			posHold[0] = x;
			posHold[1] = y;
			return true;
		}else
		return false;
	}
	//----------------------------//
	private void actionUp(MotionEvent event) {
		logd("actionUp()");
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
			}
		}
		if(!isActive){
			isImageMove = false;
			posHold[0] = posHold[1] = 0;
			posFix[0] = posFix[1] = 0;
			holderImage.setBounds(imageRect);
			imageBg.setBounds(headBgRect);
			imageTop.setBounds(headTopRect);
		}
	}
	//----------------------------//
	private void actionMove(MotionEvent event){
		if(!isImageMove) return;			// 超出可移动范围
		
		float eventX =  event.getX();
		
		{
			float tx = eventX - posHold[0] ;		// 圆心移动的距离x
			if(tx>ringOffset){
				eventX = posHold[0] + ringOffset;
			}else if(tx<-ringOffset){
				eventX = posHold[0] - ringOffset;
			}
        }
		
		posFix[0] = eventX - posHold[0];
		
		for(int i=0;i<itemList.size();i++){
			RingItem ri = itemList.get(i);
			ri.checkPosition(eventX);
			if(ri.isActive){
				if(ri.isActive!=ri.oldActive)
					vibrate();
				posFix[0] = ri.position * ringOffset;
				logd("active: "+ri.funType);
			}
		}
		
	}
	
	public void reset(){
		logd(" on reset view	("+this.getWidth()+","+this.getHeight()+")");
		centPoint[0] = (this.getWidth()>0)? (this.getWidth()>>1) : (centPoint[0]);
		centPoint[1] = (this.getHeight()>0 && this.getHeight()<500)? (this.getHeight()>>1) : (centPoint[1]);
		logd("on reset center ("+centPoint[0]+","+centPoint[1]+")");
		
		if(itemList!=null && itemList.size()>0)
		for(RingItem ri:itemList){
			ri.setCent(centPoint[0] + (ringOffset*ri.position), centPoint[1]);
		}
		if(itemList!=null)
			logd("item reset: size="+itemList.size()+" ");
		if(!init) return;
		logd("reset()");
		if(holderImage!=null){
			imageRect.set((int)(centPoint[0]-(holderImage.getIntrinsicWidth()>>1)),
					(int)(centPoint[1]-(holderImage.getIntrinsicHeight()>>1)), 
					(int)(centPoint[0]+(holderImage.getIntrinsicWidth()>>1)),
					(int)(centPoint[1]+(holderImage.getIntrinsicHeight()>>1)));
			holderImage.setBounds(imageRect);
		}else
			loge("reset holderImage=null");
		if(imageBg!=null){
			headBgRect.set((int)(centPoint[0]-(imageBg.getIntrinsicWidth()>>1)),
					(int)(centPoint[1]-(imageBg.getIntrinsicHeight()>>1)),
					(int)(centPoint[0]+(imageBg.getIntrinsicWidth()>>1)),
					(int)(centPoint[1]+(imageBg.getIntrinsicHeight()>>1))
				);
			imageBg.setBounds(headBgRect);
		}else
			loge("reset imageBg=null");
		if(imageBgLight!=null){
			headBgLightRect.set((int)(centPoint[0]-(imageBgLight.getIntrinsicWidth()>>1)),
					(int)(centPoint[1]-(imageBgLight.getIntrinsicHeight()>>1)),
					(int)(centPoint[0]+(imageBgLight.getIntrinsicWidth()>>1)),
					(int)(centPoint[1]+(imageBgLight.getIntrinsicHeight()>>1))
				);
			imageBgLight.setBounds(headBgLightRect);
		}else
			loge("reset imageBgLight=null");
		if(imageTop!=null){
			headTopRect.set((int)(centPoint[0]-(imageTop.getIntrinsicWidth()>>1)),
					(int)(centPoint[1]-(imageTop.getIntrinsicHeight()>>1)),
					(int)(centPoint[0]+(imageTop.getIntrinsicWidth()>>1)),
					(int)(centPoint[1]+(imageTop.getIntrinsicHeight()>>1))
				);
			imageTop.setBounds(headTopRect);
		}
		if(answer!=null){
			answerRect.set((int)(centPoint[0]+ringOffset-(answer.getIntrinsicWidth()>>1)),
					(int)(centPoint[1]-(answer.getIntrinsicHeight()>>1)),
					(int)(centPoint[0]+ringOffset+(answer.getIntrinsicWidth()>>1)),
					(int)(centPoint[1]+(answer.getIntrinsicHeight()>>1))
				);
			answer.setBounds(answerRect);
		}else
			loge("reset answer=null");
		if(refuse!=null){
			refuseRect.set((int)(centPoint[0]-ringOffset-(refuse.getIntrinsicWidth()>>1)),
					(int)(centPoint[1]-(refuse.getIntrinsicHeight()>>1)),
					(int)(centPoint[0]-ringOffset+(refuse.getIntrinsicWidth()>>1)),
					(int)(centPoint[1]+(refuse.getIntrinsicHeight()>>1))
				);
			refuse.setBounds(refuseRect);
		}else
			loge("reset refuse=null");
			
		loge("onrest left,right,top,right");
		logd("imageRect ["+imageRect.left+","+imageRect.right+","+imageRect.top+","+imageRect.bottom+"]");
		logd("headBgRect ["+headBgRect.left+","+headBgRect.right+","+headBgRect.top+","+headBgRect.bottom+"]");
		logd("headBgLightRect ["+headBgLightRect.left+","+headBgLightRect.right+","+headBgLightRect.top+","+headBgLightRect.bottom+"]");
		logd("headTopRect ["+headTopRect.left+","+headTopRect.right+","+headTopRect.top+","+headTopRect.bottom+"]");
		logd("answerRect ["+answerRect.left+","+answerRect.right+","+answerRect.top+","+answerRect.bottom+"]");
		logd("refuseRect ["+refuseRect.left+","+refuseRect.right+","+refuseRect.top+","+refuseRect.bottom+"]");
		
		logd("isShow["+isShow+"]	isMeasure:"+isMeasure);
		logd("number["+number+"]	uri:"+uri);
	}
	///////////////////////////////////////////////////sure //////////////////////////////////////////////////////////
	private void vibrate(){
		if(mVibrator!=null){
			mVibrator.vibrate(mVibrateDuration);
		}
	}
	public void setInComingNumber(String number){
		this.number = number;
		logd("on setInComingNumber: "+number);
		if(loadOver && holderImage==null )
			exec.execute(loadHolderImageRunnable);
	}
	void setInComingUri(Uri uri){
		this.uri = uri;
		logd("on setInComingUri: "+uri);
		if(loadOver && holderImage==null )
			exec.execute(loadHolderImageRunnable);
	}

	private Runnable loadHolderImageRunnable = new Runnable(){
		public void run(){
			loge("shut not be there,that may case unknow err. this is for fix holderImage");
			
			if(number!=null || uri!=null){
				holderImage = getHolderImage(uri,number);
					
				if(holderImage!=null){
					imageRect.set((int)(centPoint[0]-(holderImage.getIntrinsicWidth()>>1)),
							(int)(centPoint[1]-(holderImage.getIntrinsicHeight()>>1)), 
							(int)(centPoint[0]+(holderImage.getIntrinsicWidth()>>1)),
							(int)(centPoint[1]+(holderImage.getIntrinsicHeight()>>1)));
					holderImage.setBounds(imageRect);
				}else{
					logd("err!!!holderImage == null!");
				}
			}
		}
	};
	
	private void loadHolderInRunnable(){
			if(number!=null|| uri!=null){
				holderImage = getHolderImage(uri,number);
					
				if(holderImage!=null){
					imageRect.set((int)(centPoint[0]-(holderImage.getIntrinsicWidth()>>1)),
								(int)(centPoint[1]-(holderImage.getIntrinsicHeight()>>1)), 
								(int)(centPoint[0]+(holderImage.getIntrinsicWidth()>>1)),
								(int)(centPoint[1]+(holderImage.getIntrinsicHeight()>>1)));
					holderImage.setBounds(imageRect);
				}else{
					logd("err!!!load holderImage == null!");
				}
			}
	}
	/**
	 * 圆形框内显示图
	 */
	private static final String[]  project = new String[]{Phone._ID, Phone.PHOTO_THUMBNAIL_URI, Phone.NUMBER};
	
	private Drawable getHolderImage(Uri uri,String number){
		Bitmap bitmap = null;
		if(uri!=null){
			InputStream input = ContactsContract.Contacts.openContactPhotoInputStream(this.getContext().getContentResolver(), uri,true); 
			bitmap = BitmapFactory.decodeStream(input);
			logd("load image from uri :"+(bitmap!=null));
		}
		if(bitmap==null && (number!=null && !number.isEmpty())){
			Cursor c = this.getContext().getContentResolver()
								.query(Phone.CONTENT_URI, project, Phone.NUMBER+"='"+number+"'",null,null);
			if(c!=null){
				try{
					if(c.moveToFirst()&&c.getString(1)!=null){
						Uri u = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, c.getLong(0)); 
						InputStream input = ContactsContract.Contacts.openContactPhotoInputStream(this.getContext().getContentResolver(), u,true); 
						bitmap = BitmapFactory.decodeStream(input);
						input.close();
					}
				}catch(IOException e){
					e.printStackTrace();
				}finally{
					c.close();
				}
			}
			logd("load image from number :"+(bitmap!=null));
		}
		
		if(bitmap!=null){
			Drawable drawable = new BitmapDrawable(this.getResources(),toRoundBitmap(bitmap,mImageRadius));
			if(drawable!=null){
				drawable.setBounds(imageRect);
			 	return drawable;
			 }
		}
		return defautlImage;
	
	}
	
	@Override
	protected void onWindowVisibilityChanged(int visibility) {
		switch(visibility){
		case View.VISIBLE:
			logd("onVisibilityChange: VISIBLE	===========:"+System.currentTimeMillis());
			vinit();
			if(exec!=null && !isAnmiRuning)
				exec.execute(anmiRunnable);
			break;
		case View.INVISIBLE:
			logd("onVisibilityChange: INVISIBLE");
		case View.GONE:
			logd("onVisibilityChange: GONE");
			clean();
			break;
		default:
			loge("onWindowVisibilityChanged: err!!! 	should not be there	visibility="+visibility);
			break;
		}
		super.onWindowVisibilityChanged(visibility);
	}
	private void vinit(){
		isShow = true;
		if(holderImage!=null)	holderImage.setBounds(imageRect);
		if(imageBg!=null)	imageBg.setBounds(headBgRect);
		if(imageTop!=null)	imageTop.setBounds(headTopRect);
	}
	
	private void clean(){
		loadOver = isMeasure = isShow = isImageMove = false;
		posHold[0] = posHold[1] = posFix[0] = posFix[1] = 0;
		
		uri = null;
		number = null;
		holderImage = null;
	}
	
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		logd("onMeasure");
		int minimumHeight = (mImageRadius+40)<<1;
		int minimumWidth = minimumHeight + 50;
		int computedWidth = resolveMeasured(widthMeasureSpec, minimumWidth);
       int computedHeight = resolveMeasured(heightMeasureSpec, minimumHeight);
        logd("onMeasure["+widthMeasureSpec+","+heightMeasureSpec+"]:["+computedWidth+","+computedHeight+"]");
		setMeasuredDimension(computedWidth, computedHeight);
		centPoint[0] = (computedWidth>0)? (computedWidth>>1) : (centPoint[0]);
		centPoint[1] = (computedHeight>0 && computedHeight<500)? (computedHeight>>1) : (centPoint[1]);
		isMeasure = true;
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
	
	public static void logd(String msg){
		if(debug)	Log.d(TAG, msg);
	}
	public static void loge(String msg){
		Log.e(TAG,msg);
	}
	/**
     * Interface definition for a callback to be invoked when the dial
     * is "triggered" by rotating it one way or the other.
     */
	int bgAlpha;
	boolean bgFlag = true; 
	
	public void backgroundRunnable(){
		try {
				while(isShow){
					if(bgFlag)
						bgAlpha ++;
					else
						bgAlpha --;
						
					if(bgAlpha==0 || bgAlpha==255) bgFlag = !bgFlag; 
					if(ImageRing.this!=null)	ImageRing.this.postInvalidate();
					Thread.sleep(2);
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
	}
	
	public Runnable anmiRunnable = new Runnable(){
		public void run(){
			try {
				logd("on anmiRunnable ["+number+","+uri+"]");
				if(number!=null || uri!=null)
					loadHolderInRunnable();
				loadOver = true;
				isAnmiRuning = true;
				while(isShow){
					if(bgFlag)
						bgAlpha ++;
					else
						bgAlpha --;
						
					if(bgAlpha==0 || bgAlpha==255) bgFlag = !bgFlag; 
					if(ImageRing.this!=null)	ImageRing.this.postInvalidate();
					Thread.sleep(2);
				}
				isAnmiRuning = false;
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	};

}
