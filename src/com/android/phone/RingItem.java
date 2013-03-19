package com.android.phone;

import android.graphics.Bitmap;
import android.util.FloatMath;
import android.util.Log;
import android.graphics.drawable.Drawable;

public class RingItem {
	float distance;		// 激活有效距离
	float ltX,ltY;		// 左上角坐标
	float cenX,cenY;	// 中心坐标
	
	int position;		// 在圆环中的位置
	int width,height;	
	
	int funType = ImageRing.OnTriggerListener.REFUSE;
	Drawable iconNormal,iconClick;
	boolean isActive = false;
	boolean oldActive = false;
	
	/**
	 *iconNormal 原始图
	 *iconClick	 点击效果图
	 *position 	 位置
	 *distance	 激活距离，-1 代表图片直径
	 *type		类型，默认 or 自定义
	 */
	public RingItem(Drawable iconNormal, Drawable iconClick, int position, float distance, int type){
		this.iconNormal = iconNormal;
		this.iconClick = iconClick==null? iconNormal : iconClick; 
		this.position = position;
		this.distance = distance;
		if(iconNormal!=null){
			this.width = iconNormal.getIntrinsicWidth();
			this.height = iconNormal.getIntrinsicHeight();
			this.distance = distance>0?	distance : Math.max(iconNormal.getIntrinsicWidth(), iconNormal.getIntrinsicHeight());
		}
		if(type>99 && type<102)
			funType = type;
	}

	public void setCent(float cenX, float cenY) {
		this.cenX = cenX;
		this.cenY = cenY;
		int xx = (int)cenX;
		int yy = (int)cenY;
		if(iconNormal!=null)
			iconNormal.setBounds(xx,yy,xx+iconNormal.getIntrinsicWidth(),yy+iconNormal.getIntrinsicHeight());
		if(iconClick!=null)
			iconClick.setBounds(xx,yy,xx+iconClick.getIntrinsicWidth(),yy+iconClick.getIntrinsicHeight());
		
		this.ltX = cenX - (iconNormal==null? 0 : iconNormal.getIntrinsicWidth()>>1);
		this.ltY = cenY + (iconNormal==null? 0 : iconNormal.getIntrinsicHeight()>>1);
	}

	public void checkPosition(float x/*,float y*/){
		//int xx = (int) Math.abs(x-cenX);
		//int yy = (int) Math.abs(y-cenY);
		
		double distance = Math.abs(x-cenX);//Math.sqrt(xx*xx + yy*yy);
		if(this.distance>distance){
			oldActive = isActive;
			isActive = true;
		}else{
			oldActive = isActive;
			isActive = false;
		}
	}
}
