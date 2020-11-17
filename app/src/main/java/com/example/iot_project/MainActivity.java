package com.example.iot_project;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.Date;

public class MainActivity extends AppCompatActivity {
    // current File
    File file;
    // current mediaPlayer
    MediaPlayer mediaPlayer;

    // Start Stop recv text button
    Button startRecvButton, stopRecvButton;

    // send text using samplingRate and frequency
    int generateSamplingRate =1000;
    int generateFrequency =44100;

    // Recv
    // 48K sample frequency default;
    int samplingRate = 48000;
    // single channel
    int channelConfiguration = AudioFormat.CHANNEL_IN_MONO;
    // 16Bit
    int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
    // isRecording
    boolean isRecving = false;
    // audio record buffer size
    int bufferSize = 0;


    // get permission
    private void GetPermission() {
        /*在此处插入运行时权限获取的代码*/
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)!=
                PackageManager.PERMISSION_GRANTED||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)!=
                        PackageManager.PERMISSION_GRANTED||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)!=
                        PackageManager.PERMISSION_GRANTED
        )
        {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
        }
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        GetPermission();

        // init button and input
        startRecvButton = (Button)findViewById(R.id.start_recv_button);
        stopRecvButton = (Button)findViewById(R.id.stop_recv_button);

        // disable stopRecord
        stopRecvButton.setEnabled(false);

        // setup onClick
        startRecvButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // enable stop record
                stopRecvButton.setEnabled(true);
                startRecvButton.setEnabled(false);

                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        // get temp file name
                        String name = Environment.getExternalStorageDirectory().getAbsolutePath()+"/raw.wav";
                        System.out.println(name);
                        // start recv text
                        startRecv(name);
                    }
                });
                // start run
                thread.start();
            }
        });

        stopRecvButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // stop record
                isRecving = false;
                // enable start record
                stopRecvButton.setEnabled(false);
                startRecvButton.setEnabled(true);
            }
        });
    }

    // generate .wav
    public void generateWav(){

//        System.out.println(length);
//        byte[] wave= new byte[length*2];
//        bufferSize=length*2;
//        // generate data
//        for(int i=0;i<length;i++) {
//            double normalizedWaveValue = Math.sin(2 * Math.PI * generateFrequency * ((double)i / length) * generateTime + generatePhase);
//            short waveValue = (short) (Short.MAX_VALUE * normalizedWaveValue);
//            wave[i*2]=(byte) (waveValue & 0xFF);
//            wave[i*2+1]=(byte) ((waveValue >> 8) & 0xFF);
//        }

//        String filename=Environment.getExternalStorageDirectory().getAbsolutePath()+"/raw.wav";
//        // create temp file
//        file = new File(filename);
//        //如果文件已经存在，就先删除再创建
//        if (file.exists())
//            file.delete();
//        try {
//            file.createNewFile();
//        } catch (IOException e) {
//            throw new IllegalStateException("未能创建" + file.toString());
//        }
//        try {
//            OutputStream os = new FileOutputStream(file);
//            BufferedOutputStream bos = new BufferedOutputStream(os);
//            DataOutputStream dos = new DataOutputStream(bos);
//            for(int i=0;i<wave.length;i++){
//                dos.write(wave[i]);
//            }
//            dos.close();
//        } catch (Throwable t) {
//            Log.e("MainActivity", "录音失败");
//        }
//        Date now = Calendar.getInstance().getTime();
//        System.out.println(now);
//        // using time as file name
//        String filepath =Environment.getExternalStorageDirectory().getAbsolutePath()+"/"+now.toString()+".wav";
//        // write to .wav
//        copyWaveFile(filename,filepath,generateSamplingRate,bufferSize);
//        if(mediaPlayer!=null){
//            mediaPlayer.stop();
//            mediaPlayer.release();
//            mediaPlayer=null;
//        }
//        // setup mediaPlayer
//        mediaPlayer=new MediaPlayer();
//        try {
//            File file = new File(filepath);
//            FileInputStream inputStream = new FileInputStream(file);
//            mediaPlayer.setDataSource(inputStream.getFD());
//            mediaPlayer.prepare();
//            inputStream.close();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

    }

    // start record
    public void startRecv(String name) {

        file = new File(name);
        if (file.exists())
            file.delete();
        try {
            file.createNewFile();
        } catch (IOException e) {
            throw new IllegalStateException("未能创建" + file.toString());
        }
        try {
            OutputStream os = new FileOutputStream(file);
            BufferedOutputStream bos = new BufferedOutputStream(os);
            DataOutputStream dos = new DataOutputStream(bos);
            // get buffer size
            bufferSize = AudioRecord.getMinBufferSize(samplingRate,channelConfiguration , audioEncoding);
            AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, samplingRate, channelConfiguration, audioEncoding, bufferSize);

            byte[] buffer = new byte[bufferSize];
            // start record
            audioRecord.startRecording();

            isRecving = true;

            // continue reading data
            while (isRecving) {
                int bufferReadResult = audioRecord.read(buffer, 0, bufferSize);
                for (int i = 0; i < bufferReadResult; i++) {
                    dos.write(buffer[i]);
                }
            }
            audioRecord.stop();
            dos.close();
        } catch (Throwable t) {
            Log.e("MainActivity", "录音失败");
        }
    }

    private void copyWaveFile(String inFileName, String outFileName,int samplingRate,int bufferSize)
    {
        System.out.println(inFileName);
        System.out.println(outFileName);
        FileInputStream in = null;
        FileOutputStream out = null;
        long totalAudioLen = 0;

        long totalDataLen = totalAudioLen + 36;
        long longSampleRate = samplingRate;
        int channels = 1;
        // bytes per minute
        long byteRate = 16 * samplingRate * channels / 8;

        byte[] data = new byte[bufferSize];
        try
        {
            in = new FileInputStream(inFileName);
            out = new FileOutputStream(outFileName);
            // get real data length
            totalAudioLen = in.getChannel().size();
            totalDataLen = totalAudioLen + 36;
            // write .wav header
            writeWaveFileHeader(out, totalAudioLen, totalDataLen, longSampleRate, channels, byteRate);
            // write raw data to .wav
            while(in.read(data) != -1)
            {
                out.write(data);
            }
            in.close();
            out.close();
        } catch (FileNotFoundException e)
        {
            e.printStackTrace();
        } catch (IOException e)
        {
            e.printStackTrace();
        }
    }


    private void writeWaveFileHeader(FileOutputStream out, long totalAudioLen,
                                     long totalDataLen, long longSampleRate, int channels, long byteRate)
            throws IOException {
        byte[] header = new byte[44];
        header[0] = 'R'; // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f'; // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16;
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1; // WAV type format = 1
        header[21] = 0;
        header[22] = (byte) channels; //single channel
        header[23] = 0;
        header[24] = (byte) (longSampleRate & 0xff); //sampling rate
        header[25] = (byte) ((longSampleRate >> 8) & 0xff);
        header[26] = (byte) ((longSampleRate >> 16) & 0xff);
        header[27] = (byte) ((longSampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff); //bytes per minute
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (2 * 16 / 8); // block align
        header[33] = 0;
        header[34] = 16; // bits per sample
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff); //real data length
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);

        out.write(header, 0, 44);
    }



}