package com.video.main;

import java.util.ArrayList;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;
import android.widget.Toast;

import com.video.R;
import com.video.data.PreferData;
import com.video.data.Value;
import com.video.main.FragmentTabAdapter.OnMyTabChangedListener;
import com.video.service.BackstageService;
import com.video.socket.HandlerApplication;
import com.video.socket.ZmqCtrl;
import com.video.socket.ZmqHandler;
import com.video.user.LoginActivity;
import com.video.utils.TabFactory;
import com.video.utils.UpdateAPK;
import com.video.utils.Utils;

public class MainActivity extends FragmentActivity {
	
	public ArrayList<Fragment> fragments = new ArrayList<Fragment>();
	
	public static Context mContext;
	private PreferData preferData = null;
	private TabHost mTabHost;
	private TextView app_exit;
	
	private String userName = "";
	private String userPwd = "";
	private boolean isAppFirstTime = true;
	private boolean isAutoLogin = false;
	private static boolean isPlayAlarmMusic = true;
	private boolean isTextViewShow = false;
	private static TextView tv_alarm_msg = null;
	
	private static Dialog mDialog = null;
	private final static int IS_LOGINNING = 1;
	private final static int LOGIN_TIMEOUT = 2;
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        mContext = MainActivity.this;
        preferData = new PreferData(mContext);
        ZmqCtrl.getInstance().init();
        
        if (!Value.isLoginSuccess) {
        	setFullScreen();
			setContentView(R.layout.startup);
			ZmqHandler.setHandler(handler);
			if (preferData.isExist("AppFirstTime")) {
				isAppFirstTime = preferData.readBoolean("AppFirstTime");
			}
			if (preferData.isExist("AutoLogin")) {
				isAutoLogin = preferData.readBoolean("AutoLogin");
			}
			new Handler().postDelayed(new Runnable(){   
			    public void run() {
			    	quitFullScreen();
			    	if (isAppFirstTime) {
			    		//第一次使用该软件的帮助图片
			    		isAppFirstTime = true;
			    		preferData.writeData("AppFirstTime", isAppFirstTime);
			    		startActivity(new Intent(mContext, HelpActivity.class));
			    		MainActivity.this.finish();
			    	} else {
						if (isAutoLogin) {
							//自动登录，不进入登录界面
							if (Utils.isNetworkAvailable(mContext)) {
								if (preferData.isExist("UserName")) {
									userName = preferData.readString("UserName");
								}
								
								if (preferData.isExist("UserPwd")) {
									userPwd = preferData.readString("UserPwd");
								}
								Handler sendHandler = HandlerApplication.getInstance().getMyHandler();
								String data = generateLoginJson(userName, userPwd);
								sendHandlerMsg(IS_LOGINNING);
								handler.sendEmptyMessageDelayed(LOGIN_TIMEOUT, Value.requestTimeout);
								sendHandlerMsg(sendHandler, R.id.zmq_send_data_id, data);
							} else {
								//无可用网络
								Toast.makeText(mContext, "没有可用的网络连接，请确认后重试！", Toast.LENGTH_SHORT).show();
								startActivity(new Intent(mContext, LoginActivity.class));
								MainActivity.this.finish();
							}
						} else {
							//非自动登录
							startActivity(new Intent(mContext, LoginActivity.class));
							MainActivity.this.finish();
						}
			    	}
			    } 
			 }, 2500); 
		} else {
			//已登录成功
			setContentView(R.layout.main);
	        initData();
	        initView();
		}
    }
	
	/**
	 * 设置全屏
	 */
	private void setFullScreen() {
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
	}
	
	/**
	 * 退出全屏
	 */
	private void quitFullScreen() {
		final WindowManager.LayoutParams attrs = getWindow().getAttributes();
		attrs.flags &= (~WindowManager.LayoutParams.FLAG_FULLSCREEN);
		getWindow().setAttributes(attrs);
		getWindow().clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
	}

	
	/**
	 * 生成JSON的登录字符串
	 */
	private String generateLoginJson(String username, String pwd) {
		String result = "";
		JSONObject jsonObj = new JSONObject();
		try {
			jsonObj.put("type", "Client_Login");
			jsonObj.put("UserName", username);
			jsonObj.put("Pwd", Utils.CreateMD5Pwd(pwd));
		} catch (JSONException e) {
			e.printStackTrace();
		}
		result = jsonObj.toString();
		return result;
	}
	
	private void initData() {
		DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        Utils.screenWidth = dm.widthPixels;
        Utils.screenHeight = dm.heightPixels;
        
        if (preferData.isExist("PlayAlarmMusic")) {
			isPlayAlarmMusic = preferData.readBoolean("PlayAlarmMusic");
		}
        
        app_exit = (TextView) this.findViewById(R.id.tv_exit_application);
        app_exit.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				Intent intent = new Intent(HandlerApplication.getInstance(), BackstageService.class);
		    	HandlerApplication.getInstance().stopService(intent);
		    	finish();
			}
		});
	}
	
	private void initView() {
		mTabHost = (TabHost)findViewById(android.R.id.tabhost);
		mTabHost.setup();
		
		TabHost.TabSpec tabSpec = mTabHost.newTabSpec("tab1");
		tabSpec.setIndicator("实时",
				getResources().getDrawable(R.drawable.tab_own_xml))
		.setContent(new TabFactory(mContext));
		mTabHost.addTab(tabSpec);
		
		tabSpec = mTabHost.newTabSpec("tab2");
		tabSpec.setIndicator("历史",
				getResources().getDrawable(R.drawable.tab_video_xml));
		tabSpec.setContent(new TabFactory(mContext));
		mTabHost.addTab(tabSpec);
		
		tabSpec = mTabHost.newTabSpec("tab3");
		tabSpec.setIndicator("消息",
				getResources().getDrawable(R.drawable.tab_msg_xml));
		tabSpec.setContent(new TabFactory(mContext));
		mTabHost.addTab(tabSpec);
		
		tabSpec = mTabHost.newTabSpec("tab4");
		tabSpec.setIndicator("更多",
				getResources().getDrawable(R.drawable.tab_more_xml));
		tabSpec.setContent(new TabFactory(mContext));
		mTabHost.addTab(tabSpec);
		
		mTabHost.setCurrentTab(0);
		
		TabWidget tabWidget = mTabHost.getTabWidget();  
		tabWidget.setStripEnabled(false);
        for (int i = 0; i < tabWidget.getChildCount(); i++) { 
        	tabWidget.getChildAt(i).setBackgroundDrawable(getResources().getDrawable(R.color.title_bg_black));
            TextView tv = (TextView) tabWidget.getChildAt(i).findViewById(android.R.id.title);  
            ImageView iv = (ImageView) tabWidget.getChildAt(i).findViewById(android.R.id.icon); 
            
            RelativeLayout.LayoutParams paramsImage = new RelativeLayout.LayoutParams(  
                    RelativeLayout.LayoutParams.WRAP_CONTENT,  
                    RelativeLayout.LayoutParams.WRAP_CONTENT);  
            paramsImage.addRule(RelativeLayout.CENTER_HORIZONTAL);  
            paramsImage.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);  
            iv.setLayoutParams(paramsImage);  
              
            RelativeLayout.LayoutParams paramsText = new RelativeLayout.LayoutParams(  
                    RelativeLayout.LayoutParams.WRAP_CONTENT,  
                    RelativeLayout.LayoutParams.WRAP_CONTENT);  
            paramsText.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);  
            paramsText.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE);  
            tv.setLayoutParams(paramsText);  
            tv.setTextColor(Color.WHITE); 
            tv.setTextSize(16);
        }  
        updateTab(mTabHost);
        if (preferData.isExist("AlarmCount")) {
        	setAlarmIconAndText(preferData.readInt("AlarmCount"));
        }
        
        fragments.add(new OwnFragment());
        fragments.add(new LocalFragment());
        fragments.add(new MsgFragment());
        fragments.add(new MoreFragment());
        
        FragmentTabAdapter tabAdapter = new FragmentTabAdapter(this, fragments, android.R.id.tabcontent, mTabHost);
        tabAdapter.setOnMyTabChangedListener(new OnMyTabChangedListener() {
			@Override
			public void MyTabChanged(FragmentTabAdapter adapter) {
				// TODO Auto-generated method stub
				updateTab(mTabHost);
			}
		});
	}
	
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		// TODO Auto-generated method stub
		super.onSaveInstanceState(outState);
	}

	private void updateTab(final TabHost _TabHost) {
		for (int i = 0; i < _TabHost.getTabWidget().getChildCount(); i++) {
			TextView tv = (TextView) _TabHost.getTabWidget().getChildAt(i).findViewById(android.R.id.title);
			if (_TabHost.getCurrentTab() == i) {				
				tv.setTextColor(this.getResources().getColorStateList(R.color.tab_text_color));
			} else {
				tv.setTextColor(this.getResources().getColorStateList(R.color.white));
			}
			if (i ==2) {
				tv_alarm_msg = (TextView) _TabHost.getTabWidget().getChildAt(i).findViewById(android.R.id.title);
			}
		}
	}
	
	/**
	 * 设置消息tab的报警显示
	 * @param msg 多少条消息
	 */
	public static void setAlarmIconAndText(int msg) {
		if (msg <= 0) {
			tv_alarm_msg.setText("消息");
		} else {
			if (msg < 100) {
				tv_alarm_msg.setText("消息("+msg+")");
			} else {
				tv_alarm_msg.setText("消息99+");
			}
		}
	}
	
	public static Handler mainHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			// TODO Auto-generated method stub
			super.handleMessage(msg);
			switch (msg.what) {
				case 0:
					setAlarmIconAndText(msg.arg1);
					
					//播放报警语音
					if (!Value.isPlayMp3 && isPlayAlarmMusic) {
						Value.isPlayMp3 = true;
						MediaPlayer mediaPlayer = null;
						if (mediaPlayer == null) {
							mediaPlayer = MediaPlayer.create(mContext, R.raw.alarm);
							mediaPlayer.stop();
						}
						mediaPlayer.setOnCompletionListener(new OnCompletionListener() {
							@Override
							public void onCompletion(MediaPlayer mp) {
								mp.release();
								mp = null;
								Value.isPlayMp3 = false;
							}
						});
						try {
							mediaPlayer.prepare();
							mediaPlayer.start();
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
					break;
			}
		}
	};
	
	private Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			// TODO Auto-generated method stub
			super.handleMessage(msg);
			switch (msg.what) {
				//正在登录
				case IS_LOGINNING:
					mDialog = Utils.createLoadingDialog(mContext, "正在登录...");
					mDialog.show();
					break;
				//登录超时
				case LOGIN_TIMEOUT:
					if (mDialog != null)
						mDialog.dismiss();
					if (handler.hasMessages(LOGIN_TIMEOUT)) {
						handler.removeMessages(LOGIN_TIMEOUT);
					}
					Toast.makeText(mContext, "登录超时，请重试！", Toast.LENGTH_SHORT).show();
					startActivity(new Intent(mContext, LoginActivity.class));
	    			MainActivity.this.finish();
					break;
				//接收登录响应
				case R.id.login_id:
					if (handler.hasMessages(LOGIN_TIMEOUT)) {
						handler.removeMessages(LOGIN_TIMEOUT);
						if (mDialog != null)
							mDialog.dismiss();
						int resultCode = msg.arg1;
						if (resultCode == 0) {
							Value.isLoginSuccess = true;
							setContentView(R.layout.main);
					        initData();
					        initView();
					        new UpdateAPK(mContext).startCheckUpgadeThread();
						} else {
							Toast.makeText(mContext, "登录失败，"+Utils.getErrorReason(resultCode), Toast.LENGTH_SHORT).show();
							startActivity(new Intent(mContext, LoginActivity.class));
			    			MainActivity.this.finish();
						}
					} else {
						handler.removeMessages(R.id.login_id);
					}
					break;
			}
		}
	};	
	
	/**
	 * 发送Handler消息
	 */
	private void sendHandlerMsg(int what) {
		Message msg = new Message();
		msg.what = what;
		handler.sendMessage(msg);
	}
	private void sendHandlerMsg(Handler handler, int what, String obj) {
		Message msg = new Message();
		msg.what = what;
		msg.obj = obj;
		handler.sendMessage(msg);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		// TODO Auto-generated method stub
		if (mDialog != null)
			mDialog.dismiss();
		if (handler.hasMessages(LOGIN_TIMEOUT)) {
			handler.removeMessages(LOGIN_TIMEOUT);
		}
		if (handler.hasMessages(R.id.login_id)) {
			handler.removeMessages(R.id.login_id);
		}
		if (keyCode == KeyEvent.KEYCODE_MENU  && event.getRepeatCount() == 0) {
			if (isTextViewShow == false) {
				isTextViewShow = true;
				app_exit.setVisibility(View.VISIBLE);
				Animation animation = AnimationUtils.loadAnimation(mContext, R.anim.popupwindow_menu_in);  
				app_exit.startAnimation(animation); 
			} else {
				isTextViewShow = false;
				app_exit.setVisibility(View.INVISIBLE);
				Animation animation = AnimationUtils.loadAnimation(mContext, R.anim.popupwindow_menu_out);  
				app_exit.startAnimation(animation); 
			}
		} else if (keyCode == KeyEvent.KEYCODE_BACK  && event.getRepeatCount() == 0) {
			if (isTextViewShow) {
				isTextViewShow = false;
				app_exit.setVisibility(View.INVISIBLE);
				Animation animation = AnimationUtils.loadAnimation(mContext, R.anim.popupwindow_menu_out);  
				app_exit.startAnimation(animation); 
				return true;
			}
		}
		return super.onKeyDown(keyCode, event);
	}
	
	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
		Value.isManulLogout = false;
	}
	
}

