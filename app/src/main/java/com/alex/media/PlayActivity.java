package com.alex.media;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.TreeMap;


import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.SeekBar.OnSeekBarChangeListener;

public class PlayActivity extends Activity implements MediaPlayer.OnCompletionListener{
	private int[] _ids;
	private int position;
	private MediaPlayer mediaPlayer =null;
	private Uri uri;
	private ImageButton playBtn = null;//播放、暂停

	private TextView lrcText = null;		//歌词文本
	private TextView playtime = null;		//已播放时间
	private TextView durationTime = null;	//歌曲时间
	private SeekBar seekbar = null;			//歌曲进度
	private Handler handler = null;			//用于进度条
    private int currentPosition;			//当前播放位置
    private DBHelper dbHelper = null;

	private TreeMap<Integer, LRCbean> lrc_map = new TreeMap<Integer, LRCbean>();
	private Cursor myCur;

	private static final int UPDATE_VIEW = 1;
    private static final int UPDATE_LRC = 2;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.play);

		Intent intent = this.getIntent();
		Bundle bundle = intent.getExtras();
		_ids = bundle.getIntArray("_ids");
		position = bundle.getInt("position");

		lrcText = (TextView)findViewById(R.id.lrc);
		
		/*歌曲时间*/
		playtime = (TextView)findViewById(R.id.playtime);//已经播放的时间
		durationTime = (TextView)findViewById(R.id.duration);//歌曲总时间
		
		
		/*播放、暂停、停止按钮设置*/
		playBtn = (ImageButton)findViewById(R.id.playBtn);//开始播放
		playBtn.setOnClickListener(new View.OnClickListener() {	
			@Override
			public void onClick(View v) {
				if (mediaPlayer.isPlaying()){
					pause();
					playBtn.setBackgroundResource(R.drawable.play_selecor);
				} else{
					play();
					playBtn.setBackgroundResource(R.drawable.pause_selecor);
					
				}
			}
		});

		/*SeekBar进度条*/
		seekbar = (SeekBar)findViewById(R.id.seekbar);
		
		seekbar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
			
			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				mediaPlayer.start();
			}
			
			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
				mediaPlayer.pause();
			}
			
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress,
					boolean fromUser) {
				if(fromUser){
					mediaPlayer.seekTo(progress);
				}
			}
		});

		setup();//准备播放
		play();//开始播放
		
	}
	
	
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == event.KEYCODE_BACK) {
			if (mediaPlayer != null) {
				mediaPlayer.reset();
				mediaPlayer.release();
				mediaPlayer = null;
			}

			pause();

			handler.removeMessages(UPDATE_VIEW);
			handler = null;
			dbHelper.close();
			Intent intent = new Intent(this, ListActivity.class);
			startActivity(intent);
			finish();
		}
		return false;
	}


	private void loadClip(){
		if (mediaPlayer != null) {
			mediaPlayer.reset();
			mediaPlayer.release();
			mediaPlayer = null;
		}
		mediaPlayer = new MediaPlayer();//创建多媒体对象
		mediaPlayer.setOnCompletionListener(this);
		int pos = _ids[position];
		DBOperate(pos);
	    uri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
				"" + pos);
	    try {
			mediaPlayer.setDataSource(this, uri);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	
	private void setup(){
		refreshView();
		loadClip();
		init();
		try {
			mediaPlayer.prepare();
			mediaPlayer.setOnPreparedListener(new OnPreparedListener() {

				@Override
				public void onPrepared(final MediaPlayer mp) {
					seekbar.setMax(mp.getDuration());//设置播放进度条最大值
					handler.sendEmptyMessage(UPDATE_VIEW);//向handler发送消息，启动播放进度条
					playtime.setText(toTime(mp.getCurrentPosition()));//初始化播放时间
					durationTime.setText(toTime(mp.getDuration()));//设置歌曲时间
					mp.seekTo(currentPosition);//初始化MediaPlayer播放位置
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	
	@Override
	public void onCompletion(MediaPlayer mp) {//循环播放
		int num = _ids.length;
		if (position==num-1){
			position=0;
		}else{
			position+=1;
		}
		System.out.println(position);
		int pos = _ids[position];
		lrc_map.clear();
		refreshView();
		setup();
		play();
	}
	
	private void play(){
		mediaPlayer.start();
		playBtn.setBackgroundResource(R.drawable.pause_selecor);
		
	}
	
	private void pause(){
		mediaPlayer.pause();
	}
	
	private void stop(){
		mediaPlayer.stop();
		playBtn.setBackgroundResource(R.drawable.play_selecor);
		try {
			mediaPlayer.prepare();
			mediaPlayer.seekTo(0);
			seekbar.setProgress(mediaPlayer.getCurrentPosition());
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	 
	private void init() {
		handler = new msgHandler();
	}

	class msgHandler extends Handler{
		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			switch (msg.what) {
				case UPDATE_VIEW:
					if(mediaPlayer !=null)
						currentPosition = mediaPlayer.getCurrentPosition();
					seekbar.setProgress(currentPosition);
					playtime.setText(toTime(currentPosition));
					handler.sendEmptyMessage(UPDATE_VIEW);

                    for (Integer o : lrc_map.keySet()) {
                        LRCbean val = lrc_map.get(o);
                        if (val != null) {
                            if (mediaPlayer.getCurrentPosition() > val.getBeginTime()
                                    && mediaPlayer.getCurrentPosition() < val.getBeginTime() + val.getLineTime()) {
                                lrcText.setText(val.getLrcBody());
                                break;
                            }
                        }
                    }
					break;

                case UPDATE_LRC:
                    break;

				default:
					break;
			}

		}
	}
	
	public String toTime(int time) {
		time /= 1000;
		int minute = time / 60;
		int second = time % 60;
		minute %= 60;
		return String.format("%02d:%02d", minute, second);
	}
	
	private void DBOperate(int pos){
		//数据库操作
		dbHelper = new DBHelper(this, "music.db", null, 2);
		Cursor c = dbHelper.query(pos);
		Date currentTime = new Date();   
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");   
		String dateString = formatter.format(currentTime); 
		if (c==null||c.getCount()==0){//如果查询结果为空			
			ContentValues values = new ContentValues();
			values.put("music_id", pos);
			values.put("clicks", 1);
			values.put("latest", dateString);
			dbHelper.insert(values);
		} else{
			c.moveToNext();
			int clicks = c.getInt(2);
			clicks++;
			ContentValues values = new ContentValues();
			values.put("clicks", clicks);
			values.put("latest", dateString);
			dbHelper.update(values, pos);
			c.close();
		}
	}
	
	private void readLrc(String path){
    	TreeMap<Integer, LRCbean> lrc_read = new TreeMap<Integer, LRCbean>();
    	String data = "";
    	BufferedReader br = null;
    	File file = new File(path);
    	if (!file.exists()){
    		lrcText.setText("歌词文件不存在...");
    		return;
    	}
    	FileInputStream stream = null;
		try {
			stream = new FileInputStream(file);
            br = new BufferedReader(new InputStreamReader(
					stream, "UTF-8"));
		} catch (FileNotFoundException | UnsupportedEncodingException e) {
			e.printStackTrace();
		}
        try {
			while((data=br.readLine())!=null){
				if (data.length()>6){
					if (data.charAt(3)==':'&&data.charAt(6)=='.'){//从歌词正文开始
						data = data.replace("[", "");
						data = data.replace("]", "@");
						data = data.replace(".", ":");
						String lrc[] = data.split("@");
						String lrcContent= null;
						if (lrc.length==2){
						lrcContent = lrc[lrc.length-1];//歌词
						}else{
						lrcContent = "";
						}
						String lrcTime[] = lrc[0].split(":");
						
						int m = Integer.parseInt(lrcTime[0]);//分
						int s = Integer.parseInt(lrcTime[1]);//秒
						int ms = Integer.parseInt(lrcTime[2]);//毫秒
						
						int begintime = (m*60 + s) * 1000 + ms;//转换成毫秒
						LRCbean lrcbean = new LRCbean();
						lrcbean.setBeginTime(begintime);//设置歌词开始时间
						lrcbean.setLrcBody(lrcContent);//设置歌词的主体
						lrc_read.put(begintime,lrcbean);
					}
				}
			}
            if (stream != null) {
                stream.close();
            }
        } catch (IOException e) {
			e.printStackTrace();
		}
		
		//计算每句歌词需要的时间
		lrc_map.clear();
        Iterator<Integer> iterator = lrc_read.keySet().iterator();
		LRCbean oldval = null;
		int i = 0;
		while (iterator.hasNext()){
			Object ob = iterator.next();
			LRCbean val = lrc_read.get(ob);
			if (oldval==null){
				oldval = val;
			} else{
				LRCbean item1 = oldval;
				item1.setLineTime(val.getBeginTime()-oldval.getBeginTime());
				lrc_map.put(i, item1);
				i++;
				oldval = val;
			}
		}
		
    }
	
	public void refreshView() {
		myCur = getContentResolver().query(
				MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
				new String[] { MediaStore.Audio.Media.TITLE,
						MediaStore.Audio.Media.DURATION,
						MediaStore.Audio.Media.ARTIST,
						MediaStore.Audio.Media.ALBUM,
						MediaStore.Audio.Media.DISPLAY_NAME,
						MediaStore.Audio.Media.DATA}, "_id=?",
				new String[] { _ids[position] + "" }, null);
		myCur.moveToFirst();

		String name = myCur.getString(5).substring(0,
				myCur.getString(5).lastIndexOf("."));
		readLrc(name + ".lrc");
	}

	 
			


}
