package com.alex.media.util;

import android.text.TextUtils;
import android.util.Log;

import com.alex.media.LyricBean;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * Created by luo on 2015/12/29.
 */
public class LyricsUtil {
    private static final String TAG = LyricsUtil.class.getSimpleName();

    /**
     * get encode of lyric
     * @param file lyric file
     * @return lyric file encode type
     */
    public static String GetCharset(File file){
        String charset = Constants.GBK;
        if(file==null) return charset;
        if(!file.exists()) return charset;

        byte[] first3Bytes = new byte[3];
        try {
            boolean checked = false;
            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
            bis.mark(0);
            int read = bis.read(first3Bytes, 0, 3);
            if (read == -1)
                return charset;
            if (first3Bytes[0] == (byte) 0xFF && first3Bytes[1] == (byte) 0xFE) {
                charset = Constants.UTF16LE;
                checked = true;
            } else if (first3Bytes[0] == (byte) 0xFE && first3Bytes[1] == (byte) 0xFF) {
                charset = Constants.UTF16BE;
                checked = true;
            } else if (first3Bytes[0] == (byte) 0xEF && first3Bytes[1] == (byte) 0xBB
                    && first3Bytes[2] == (byte) 0xBF) {
                charset = Constants.UTF8;
                checked = true;
            }

            bis.reset();
            if (!checked) {
                while ((read = bis.read()) != -1) {
                    if (read >= 0xF0)
                        break;
                    if (0x80 <= read && read <= 0xBF)
                        break;
                    if (0xC0 <= read && read <= 0xDF) {
                        read = bis.read();
                        if (0x80 <= read && read <= 0xBF)
                            continue;
                        else
                            break;
                    } else if (0xE0 <= read && read <= 0xEF) {
                        read = bis.read();
                        if (0x80 <= read && read <= 0xBF) {
                            read = bis.read();
                            if (0x80 <= read && read <= 0xBF) {
                                charset = Constants.UTF8;
                                break;
                            }
                            else
                                break;
                        }
                        else
                            break;
                    }
                }
            }
            bis.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return charset;
    }

    /**
     * get millisecond
     * @param milliSec input string: [30:59.09]
     * @return millisecond
     */
    private static long getMilliSecond(String milliSec){
        String splitList[] = milliSec.replace(".",":").replace("[", "").replace("]", "").split(":");
        int minute = Integer.parseInt(splitList[0]);//分
        int second = Integer.parseInt(splitList[1]);//秒
        int milliSecond = Integer.parseInt(splitList[2]);//毫秒

        milliSecond = milliSecond<100?milliSecond*10:milliSecond;

        return (minute*60 + second) * 1000 + milliSecond;//转换成毫秒
    }

    /**
     * read in lyric file
     * @param lyricPath lyric file path
     * @param encode lyric file encode type
     * @return Vector<LyricBean> lyric list,
     *          return null while exceptions exist
     */
    public static Vector<LyricBean> readLyricFile(String lyricPath, String encode){
        Log.e(TAG,"path="+lyricPath+" encode="+encode);
        if( TextUtils.isEmpty(lyricPath)) {
            Log.e(TAG, "lyricPath empty");
            return null;
        }

        File lyricFile = new File(lyricPath);
        if (!lyricFile.exists()){
            Log.e(TAG,"lyricFile not exists");
            return null;
        }

        if( TextUtils.isEmpty(encode))
            encode = Constants.UTF8;

        FileInputStream stream;
        BufferedReader bufferedReader;
        try {
            stream = new FileInputStream(lyricFile);
            bufferedReader = new BufferedReader(new InputStreamReader(stream, encode));
        } catch (FileNotFoundException | UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        }

        Vector<LyricBean> lyricList = new Vector<>();
        try {
            String lineItem;
            Pattern lyricPattern = Pattern.compile(Constants.LYRIC_PATTERN);

            while((lineItem=bufferedReader.readLine())!=null){
                Matcher matcher = lyricPattern.matcher(lineItem);

                if ( matcher.find()){
                    String lyricTime = matcher.group();//获取匹配得到的值
                    String lyricContent= lineItem.substring(lyricTime.length());//获取歌词内容
                    Log.e(TAG,lyricTime + " : " + lyricContent);
                    long beginTime = getMilliSecond(lyricTime);//转换成毫秒

                    LyricBean lyricBean = new LyricBean();
                    lyricBean.setBeginTime(beginTime);//设置歌词开始时间
                    lyricBean.setLrcBody(lyricContent);//设置歌词的主体
                    lyricList.add(lyricBean);
                }
            }

            stream.close();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        LyricBean nextItem = null;
        for(int k=lyricList.size()-1; k>=0; k--){
            LyricBean item = lyricList.get(k);
            if(nextItem!=null)
                item.setSleepTime(nextItem.getBeginTime() - item.getBeginTime());

            lyricList.set(k,item);
            nextItem = item;
        }

        return lyricList;
    }
}
