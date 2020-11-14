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

    // Start Stop record button
    Button startRecordButton, stopRecordButton;
    // Start Pause Reset Button
    Button startPlayButton, pausePlayButton, resetPlayButton;
    // Record sample frequency
    EditText recordSamplingRateInput;
    // Generate sample frequency
    EditText samplingRateInput;
    // Generate time
    EditText timeInput;
    // Generate frequency
    EditText frequencyInput;
    // Generate phase
    EditText phaseInput;

    int generateSamplingRate =1000;
    int generateFrequency =44100;
    int generateTime =10;
    double generatePhase =0;

    // Generate Button;
    Button generateButton;
    // Select File Button
    Button selectFileButton;

    // Record
    // 48K sample frequency default;
    int samplingRate = 48000;
    // single channel
    int channelConfiguration = AudioFormat.CHANNEL_IN_MONO;
    // 16Bit
    int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
    // isRecording
    boolean isRecording = false;
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
        startRecordButton = (Button)findViewById(R.id.start_record);
        stopRecordButton = (Button)findViewById(R.id.stop_record);
        selectFileButton =(Button)findViewById(R.id.select_file);
        startPlayButton =(Button)findViewById(R.id.start);
        pausePlayButton =(Button)findViewById(R.id.pause);
        resetPlayButton =(Button)findViewById(R.id.reset);
        generateButton =(Button)findViewById(R.id.generate);
        recordSamplingRateInput =(EditText)findViewById(R.id.record_sample_rate);
        samplingRateInput =(EditText)findViewById(R.id.sample_rate);
        timeInput=(EditText)findViewById(R.id.time);
        frequencyInput=(EditText)findViewById(R.id.frequency);
        phaseInput=(EditText)findViewById(R.id.phase);


        // disable stopRecord
        stopRecordButton.setEnabled(false);

        // setup onClick
        startRecordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // enable stop record
                stopRecordButton.setEnabled(true);
                startRecordButton.setEnabled(false);

                String recordSampleFrequencyString= recordSamplingRateInput.getText().toString();
                if(!recordSampleFrequencyString.isEmpty()){
                    samplingRate =Integer.parseInt(recordSampleFrequencyString);
                }

                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        // get temp file name
                        String name = Environment.getExternalStorageDirectory().getAbsolutePath()+"/raw.wav";
                        System.out.println(name);
                        // start record
                        startRecord(name);
                        // get time
                        Date now = Calendar.getInstance().getTime();
                        System.out.println(now);
                        // using time as file name
                        String filepath =Environment.getExternalStorageDirectory().getAbsolutePath()+"/"+now.toString()+".wav";
                        // write to .wav
                        copyWaveFile(name, filepath,samplingRate,bufferSize);
                    }
                });
                // start run
                thread.start();
            }
        });

        stopRecordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // stop record
                isRecording = false;
                // enable start record
                stopRecordButton.setEnabled(false);
                startRecordButton.setEnabled(true);
            }
        });

        // select file button
        selectFileButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSystemFile();
            }
        });

        // start play button
        startPlayButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                startPlay();
            }
        });

        // pause play button
        pausePlayButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                pausePlay();
            }
        });

        // reset play button
        resetPlayButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                resetPlay();
            }
        });

        // generate button
        generateButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        generateWav();
                    }
                });
                thread.start();
            }
        });

        // disable play buttons
        startPlayButton.setEnabled(false);
        pausePlayButton.setEnabled(false);
        resetPlayButton.setEnabled(false);
    }

    // generate .wav
    public void generateWav(){
        // get params
        String generateSamplingRateString = samplingRateInput.getText().toString();
        if(!generateSamplingRateString.isEmpty()){
            generateSamplingRate =Integer.parseInt(generateSamplingRateString);
        }
        String generateTimeString=timeInput.getText().toString();
        if(!generateTimeString.isEmpty()){
            generateTime =Integer.parseInt(generateTimeString);
        }
        String generateFrequencyString=frequencyInput.getText().toString();
        if(!generateFrequencyString.isEmpty()){
            generateFrequency =Integer.parseInt(generateFrequencyString);
        }
        String generatePhaseString=phaseInput.getText().toString();
        if(!generatePhaseString.isEmpty()){
            generatePhase =Integer.parseInt(generatePhaseString)/180*Math.PI;
        }

        int length= generateSamplingRate * generateTime;
        System.out.println(length);
        byte[] wave= new byte[length*2];
        bufferSize=length*2;
        // generate data
        for(int i=0;i<length;i++) {
            double normalizedWaveValue = Math.sin(2 * Math.PI * generateFrequency * ((double)i / length) * generateTime + generatePhase);
            short waveValue = (short) (Short.MAX_VALUE * normalizedWaveValue);
            wave[i*2]=(byte) (waveValue & 0xFF);
            wave[i*2+1]=(byte) ((waveValue >> 8) & 0xFF);
        }

        String filename=Environment.getExternalStorageDirectory().getAbsolutePath()+"/raw.wav";
        // create temp file
        file = new File(filename);
        //如果文件已经存在，就先删除再创建
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
            for(int i=0;i<wave.length;i++){
                dos.write(wave[i]);
            }
            dos.close();
        } catch (Throwable t) {
            Log.e("MainActivity", "录音失败");
        }
        Date now = Calendar.getInstance().getTime();
        System.out.println(now);
        // using time as file name
        String filepath =Environment.getExternalStorageDirectory().getAbsolutePath()+"/"+now.toString()+".wav";
        // write to .wav
        copyWaveFile(filename,filepath,generateSamplingRate,bufferSize);
        if(mediaPlayer!=null){
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer=null;
        }
        // setup mediaPlayer
        mediaPlayer=new MediaPlayer();
        try {
            File file = new File(filepath);
            FileInputStream inputStream = new FileInputStream(file);
            mediaPlayer.setDataSource(inputStream.getFD());
            mediaPlayer.prepare();
            startPlayButton.setEnabled(true);
            pausePlayButton.setEnabled(true);
            resetPlayButton.setEnabled(true);
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    // start play
    public void startPlay(){
        mediaPlayer.start();
        System.out.println("Start Play");
    }

    // pause play
    public  void pausePlay() {
        mediaPlayer.pause();
//        try{
//            mediaPlayer.prepare();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

        System.out.println("Stop Play");
    }

    // reset play
    public  void resetPlay(){
        mediaPlayer.seekTo(0);
        System.out.println("Reset Play");
    }


    // select file
    public void openSystemFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(Intent.createChooser(intent, "请选择文件"), 1);
        } catch (ActivityNotFoundException e) {
            e.printStackTrace();
            Toast.makeText(MainActivity.this, "请安装文件管理器", Toast.LENGTH_SHORT).show();
        }
    }

    // handle select file result
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && resultCode == Activity.RESULT_OK) {
            //Get the Uri of the selected file
            Uri uri = data.getData();
            if (null != uri) {
                if(mediaPlayer!=null){
                    mediaPlayer.stop();
                    mediaPlayer.release();
                    mediaPlayer=null;
                }
                System.out.println(uri);
                // setup mediaPlayer
                mediaPlayer= MediaPlayer.create(getApplicationContext(),uri);
                startPlayButton.setEnabled(true);
                pausePlayButton.setEnabled(true);
                resetPlayButton.setEnabled(true);
            }
        }
    }

    // start record
    public void startRecord(String name) {

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

            isRecording = true;

            // continue reading data
            while (isRecording) {
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