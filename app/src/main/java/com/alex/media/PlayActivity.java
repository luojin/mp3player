package com.alex.media;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TreeMap;
import java.util.Vector;

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
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.SeekBar.OnSeekBarChangeListener;

import com.alex.media.util.LyricsUtil;

public class PlayActivity extends Activity implements MediaPlayer.OnCompletionListener{
    private static final String TAG = PlayActivity.class.getSimpleName();

    private ImageButton playBtn = null;//播放、暂停
	private TextView lrcText = null;		//歌词文本
	private TextView playtime = null;		//已播放时间
	private TextView durationTime = null;	//歌曲时间
	private SeekBar seekbar = null;			//歌曲进度

    private int[] songIds;
    private int position;
    private int currentPosition;			//当前播放位置
    private MediaPlayer mediaPlayer =null;
	private Handler handler = null;			//用于进度条
    private DBHelper dbHelper = null;
    private Vector<LyricBean> lyricList = new Vector<>();

    private static final int UPDATE_VIEW = 1;
    private static final int PAUSE = 2;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.play);

		initData();
        getView();
        setView();

		prepare();  //准备播放
		play();     //开始播放
	}

    private void initData(){
        Intent intent = this.getIntent();
        Bundle bundle = intent.getExtras();
        songIds = bundle.getIntArray("_ids");
        position = bundle.getInt("position");

        handler = new msgHandler();
    }

    private void getView(){
        lrcText = (TextView)findViewById(R.id.lrc);
        playtime = (TextView)findViewById(R.id.playtime);//已经播放的时间
        durationTime = (TextView)findViewById(R.id.duration);//歌曲总时间
		/*播放、暂停、停止按钮设置*/
        playBtn = (ImageButton)findViewById(R.id.playBtn);//开始播放
        /*SeekBar进度条*/
        seekbar = (SeekBar)findViewById(R.id.seekbar);
    }

    private void setView(){
        playBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mediaPlayer==null)return;

                if (mediaPlayer.isPlaying()){
                    pause();
                    playBtn.setBackgroundResource(R.drawable.play_selecor);
                } else{
                    play();
                    playBtn.setBackgroundResource(R.drawable.pause_selecor);
                }
            }
        });

        seekbar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                pause();
                removeUpdateView();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                play();
                sendUpdateView();
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,
                                          boolean fromUser) {
                if(fromUser){
                    for (int k=0; k<lyricList.size(); k++) {
                        LyricBean item = lyricList.get(k);
                        if(item.getBeginTime()<=progress &&
                                progress<item.getBeginTime()+item.getSleepTime()){
                            lyricI=k;
                            break;
                        }
                    }
                }
            }
        });
    }

	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			if (mediaPlayer != null) {
				mediaPlayer.reset();
				mediaPlayer.release();
				mediaPlayer = null;
			}

			pause();

            removeUpdateView();
			handler = null;
			dbHelper.close();
			Intent intent = new Intent(this, ListActivity.class);
			startActivity(intent);
			finish();
		}
		return false;
	}
	
	private void prepare(){
		readLyric();
		loadMediaSource();

		try {
			mediaPlayer.prepare();
			mediaPlayer.setOnPreparedListener(new OnPreparedListener() {

				@Override
				public void onPrepared(final MediaPlayer mp) {
					seekbar.setMax(mp.getDuration());//设置播放进度条最大值
                    sendUpdateView(); //向handler发送消息，启动播放进度条
					playtime.setText(toTime(mp.getCurrentPosition()));//初始化播放时间
					durationTime.setText(toTime(mp.getDuration()));//设置歌曲总时间

					mp.seekTo(currentPosition);//初始化MediaPlayer播放位置
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public void onCompletion(MediaPlayer mp) {  //播放结束，循环播放
		int num = songIds.length;
		if (position==num-1){
			position=0;
		}else{
			position+=1;
		}

		readLyric();
		prepare();
		play();
	}

    private int lyricI = 0;
	private void play(){
        removePause();

        mediaPlayer.start();
        if(lyricList.size()>0){
            mediaPlayer.seekTo((int) lyricList.get(lyricI).getBeginTime());
            sendPause(lyricList.get(lyricI).getSleepTime());
            lrcText.setText(lyricList.get(lyricI).getLrcBody());
            lyricI++;
        }
        playBtn.setBackgroundResource(R.drawable.pause_selecor);
	}
	
	private void pause(){
        if (mediaPlayer!=null)
		    mediaPlayer.pause();
        removePause();
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

                    sendUpdateView();
                    break;

                case PAUSE:
                    playBtn.performClick();
                    break;

				default:
					break;
			}

		}
	}

    private void sendUpdateView(){
        removeUpdateView();
        handler.sendEmptyMessage(UPDATE_VIEW);
    }

    private void removeUpdateView(){
        handler.removeMessages(UPDATE_VIEW);
    }

    private void sendPause(long delayMillis){
        removePause();

        Message message = Message.obtain();
        message.what= PAUSE;
        handler.sendMessageDelayed(message, delayMillis);
    }

    private void removePause(){
        handler.removeMessages(PAUSE);
    }
	
	public String toTime(int time) {
		int milliSecond = time%1000;
        time /= 1000;
		int minute = time / 60;
		int second = time % 60;
		minute %= 60;
		return String.format("%02d:%02d.%03d", minute, second, milliSecond);
	}
	
	private void dbSave(int pos){
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
	
	public void readLyric() {
        Cursor myCur = getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Audio.Media.TITLE,
                        MediaStore.Audio.Media.DURATION,
                        MediaStore.Audio.Media.ARTIST,
                        MediaStore.Audio.Media.ALBUM,
                        MediaStore.Audio.Media.DISPLAY_NAME,
                        MediaStore.Audio.Media.DATA}, "_id=?",
                new String[]{songIds[position] + ""}, null);

        if (myCur != null) {
            myCur.moveToFirst();
            String name = myCur.getString(5).replace("MP3","lrc").replace("mp3","lrc");
            lyricList.clear();
            lyricList = LyricsUtil.readLyricFile( name, LyricsUtil.GetCharset(new File(name)) );
            lyricList=lyricList==null?new Vector<LyricBean>():lyricList;
        }
	}

    private void loadMediaSource(){
        if (mediaPlayer != null) {
            mediaPlayer.reset();
            mediaPlayer.release();
            mediaPlayer = null;
        }

        mediaPlayer = new MediaPlayer();//创建多媒体对象
        mediaPlayer.setOnCompletionListener(this);

        int pos = songIds[position];
        dbSave(pos);
        Uri uri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, "" + pos);
        try {
            mediaPlayer.setDataSource(this, uri);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
