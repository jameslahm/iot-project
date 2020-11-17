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
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Calendar;
import java.util.Date;

public class MainActivity extends AppCompatActivity {
    // current File used to save wav file before send text
    File file;
    // current mediaPlayer used to play wav
    MediaPlayer mediaPlayer;

    // Start Stop recv text button
    Button startRecvButton, stopRecvButton;

    // send text button
    Button sendTextButton;

    // send text input
    EditText sendTextInput;

    // show recv text
    EditText recvTextInput;

    // send text using samplingRate and frequency
    int samplingRate = 44100;
    int encodeFrequencyForZero = 20000;
    int encodeFrequencyForOne = 10000;
    double windowTime = 0.010;
    int windowWidth = (int)(windowTime * samplingRate);

    // single channel
    int channelConfiguration = AudioFormat.CHANNEL_IN_MONO;
    // 16Bit
    int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
    // isRecording
    boolean isRecving = false;
    // audio record buffer size
    int bufferSize = 4096;

    // frame args
    int PREAMBLE_BYTE_LENGTH = 1;
    byte[] PREAMBLE_BYTES =  new byte[]{(byte)0xaa};
    byte[] PREAMBLE_BITS = new byte[]{1,0,1,0,1,0,1,0};

    int HEADER_BYTE_LENGTH = 1;

    FFT fft;

    int FFT_LEN = 512;

    // status
    boolean isPreamble = false;
    int payloadLength = -1;
    byte[] payload;
    int payloadBase=0;


    // get permission
    private void GetPermission() {
        /*在此处插入运行时权限获取的代码*/
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                        PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) !=
                        PackageManager.PERMISSION_GRANTED
        ) {

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

//        fft = new FFT((int)(windowTime * samplingRate));

        fft = new FFT(FFT_LEN);

        // init button and input
        startRecvButton = (Button) findViewById(R.id.start_recv_button);
        stopRecvButton = (Button) findViewById(R.id.stop_recv_button);

        sendTextButton = (Button) findViewById(R.id.send_text_button);
        sendTextInput = (EditText) findViewById(R.id.send_text_input);

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
                    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
                    @Override
                    public void run() {
                        // start recv text
                        startRecv();
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

        sendTextButton.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.KITKAT)
            @Override
            public void onClick(View v) {
                // get input text
                String text = sendTextInput.getText().toString();

                // send input text
                try {
                    sendText(text);
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }

            }
        });
    }

    void debugBytes(byte[] buffer,boolean flag){
        for(int i=0;i<buffer.length;i++){
            if(flag) {
                System.out.println(i+String.format(" 0x%2x", buffer[i]));
                System.out.println(i+String.format(" %8s", Integer.toBinaryString(buffer[i] & 0xFF)).replace(' ', '0'));
            }
            else{
                System.out.println(i+String.format(" 0x%2x", buffer[i]));
            }
        }
    }

    // TODO: support text segment
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public void sendText(String text) throws UnsupportedEncodingException {
        // first get utf-8 bytes
        byte[] textBytes = text.getBytes("UTF8");

        // add preamble and payload length
        byte[] dataBytes = new byte[textBytes.length+PREAMBLE_BYTE_LENGTH+HEADER_BYTE_LENGTH];

        // copy payload to dataBytes
        System.arraycopy(textBytes,0,dataBytes,PREAMBLE_BYTE_LENGTH+HEADER_BYTE_LENGTH,textBytes.length);
        // add preamble
        for (int i=0;i<PREAMBLE_BYTE_LENGTH;i++){
            dataBytes[i]=PREAMBLE_BYTES[i];
        }
        // add header
        for (int i=PREAMBLE_BYTE_LENGTH;i<HEADER_BYTE_LENGTH+PREAMBLE_BYTE_LENGTH;i++){
            dataBytes[i]=(byte) textBytes.length;
        }

        debugBytes(dataBytes,true);

        // construct bit data
        byte[] dataBits = new byte[dataBytes.length*8];
        for (int i=0; i<dataBytes.length*8; i++) {
            if ((dataBytes[i/8]&(1<<(7-(i%8)))) > 0) {
                dataBits[i]=1;
            }
            else{
                dataBits[i]=0;
            }
        }
        // save bits to wav file
        generateWav(dataBits);

        // start play (send data)
        mediaPlayer.start();
        sendTextButton.setEnabled(false);
    }

    // save bits to wav file
    public void generateWav(byte[] dataBits) {
        // FSK
        int totalLength = (int)(dataBits.length*windowTime*samplingRate);
        byte[] wave= new byte[totalLength*2];
        for(int i=0;i<dataBits.length;i++) {
            int frequency;
            if (dataBits[i]==1){
                frequency = encodeFrequencyForOne;
            }
            else{
                frequency = encodeFrequencyForZero;
            }
            int base = (int)(i*windowTime*samplingRate)*2;
            for(int j=0;j<windowTime*samplingRate;j++){
                double normalizedWaveValue = Math.sin(2 * Math.PI * frequency * ((double)j /(samplingRate)));
                short waveValue = (short) (Short.MAX_VALUE * normalizedWaveValue);
                wave[base+j*2]=(byte) (waveValue & 0xFF);
                wave[base+j*2+1]=(byte) ((waveValue >> 8) & 0xFF);
            }
        }

        // save temp file
        String filename=getExternalFilesDir("").getAbsolutePath()+"/raw.wav";
        System.out.println(filename);
        file = new File(filename);
        if (file.exists())
            file.delete();
        try {
            file.createNewFile();
        } catch (IOException e) {
            System.out.println(e);
            throw new IllegalStateException("未能创建" + file.toString());
        }
        try {
            OutputStream os = new FileOutputStream(file);
            BufferedOutputStream bos = new BufferedOutputStream(os);
            DataOutputStream dos = new DataOutputStream(bos);
            System.out.println(wave.length);
            for(int i=0;i<wave.length;i++){
                dos.write(wave[i]);
            }
            dos.close();
        } catch (Throwable t) {
            Log.e("MainActivity", "保存失败");
        }
        Date now = Calendar.getInstance().getTime();
        System.out.println(now);
        // using time as file name
        String filepath =getExternalFilesDir("").getAbsolutePath()+"/"+now.toString()+".wav";
        // write to .wav
        copyWaveFile(filename,filepath,samplingRate,bufferSize);
        if(mediaPlayer!=null){
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer=null;
        }
        // setup mediaPlayer
        mediaPlayer=new MediaPlayer();
        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                sendTextButton.setEnabled(true);
            }

        });
        try {
            File file = new File(filepath);
            FileInputStream inputStream = new FileInputStream(file);
            mediaPlayer.setDataSource(inputStream.getFD());
            mediaPlayer.prepare();
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    // start record
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public void startRecv() {
        try {
            // get buffer size
            bufferSize = AudioRecord.getMinBufferSize(samplingRate, channelConfiguration, audioEncoding);
            AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, samplingRate, channelConfiguration, audioEncoding, bufferSize);

            byte[] buffer = new byte[bufferSize];
            int base = 0;

            // start record
            audioRecord.startRecording();

            isRecving = true;

            // continue reading data
            while (isRecving) {
                int bufferReadResult = audioRecord.read(buffer, base, bufferSize -base);
                base += bufferReadResult;

                if(isPreamble){
                    byte[] temp = Arrays.copyOfRange(buffer,0,base);
                    int checkIndex = processSignal(temp);
                    byte[] totalBuffer = new byte[bufferSize];
                    System.arraycopy(buffer,checkIndex,totalBuffer,0,base-checkIndex);
                    base -= checkIndex;
                    buffer = totalBuffer;
                    continue;
                }
                else{
                    byte[] temp = Arrays.copyOfRange(buffer,0,base);
                    // check if preamble
                    int checkIndex = checkPreamble(temp);

                    byte[] totalBuffer = new byte[bufferSize];
                    System.arraycopy(buffer,checkIndex,totalBuffer,0,base-checkIndex);
                    base -= checkIndex;

                    buffer = totalBuffer;
                }
            }
            audioRecord.stop();
        } catch (Throwable t) {
            Log.e("MainActivity", "录音失败");
        }
    }

    byte[] signal2DataBits(byte[] buffer){
        // transfer buffer to signal data
        double[] signalData = new double[buffer.length/2];
        for(int i=0;i<buffer.length/2;i++){
            int temp = buffer[2 * i];
            temp = ((int)buffer[2*i+1]) << 8 + temp;
            signalData[i] = (double)temp / Short.MAX_VALUE;
        }


        int bitsLength = signalData.length / windowWidth;
        byte[] dataBits = new byte[bitsLength];

        for(int i=0;i<bitsLength;i++){
            double[] x =new double[FFT_LEN];
            System.arraycopy(signalData,i*windowWidth,x,0,windowWidth);
            for(int j=windowWidth;j<FFT_LEN;j++){
                x[j]=0;
            }
            double[] y = new double[FFT_LEN];
            for(int j=0;j<FFT_LEN;j++) {
                y[j] = 0;
            }
            fft.fft(x,y);

            int indexForOne= (int) ((double)encodeFrequencyForOne/samplingRate * FFT_LEN);
            int indexForZero= (int) ((double)encodeFrequencyForZero/samplingRate * FFT_LEN);

//            System.out.println(indexForOne);
//            System.out.println(indexForZero);

            double[] z =new double[FFT_LEN];

            for(int j=0;j<FFT_LEN;j++){
                z[j]=Math.pow(x[j],2)+Math.pow(y[j],2);
//                System.out.println(z[j]);
            }

            int argMaxIndex = argMax(z);
//            System.out.println(argMaxIndex);

            if (Math.abs(argMaxIndex-indexForOne)<=2){
                dataBits[i]=1;
                System.out.println(dataBits[i]);
            }
            else if (Math.abs(argMaxIndex-indexForZero)<=2){
                dataBits[i]=0;
                System.out.println(dataBits[i]);
            }
            else{
                dataBits[i]=(byte) 0xff;
            }
//            System.out.println(dataBits[i]);
        }

        return dataBits;
    }

    byte[] bits2Byte(byte[] bits){
        byte[] dataBytes = new byte[bits.length/ 8];
        int byteValue = 0;
        for (int index = 0; index < bits.length; index++) {

            byteValue = (byteValue << 1) | bits[index];

            if (index %8 == 7) {
                dataBytes[index / 8] = (byte) byteValue;
                byteValue = 0;
            }
        }
        return dataBytes;
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    int processSignal(byte[] buffer){
        byte[] dataBits = signal2DataBits(buffer);
        int bitsLength = dataBits.length;

        // init payloadLength and payloadBase
        if(payloadBase==-1) {
            if (bitsLength >= 8) {
                byte[] dataBytes = bits2Byte(dataBits);
                payloadLength = dataBytes[0];
                payload = new byte[payloadLength];
                payloadBase = 0;
                if (dataBytes.length - 1 < payloadLength) {
                    System.arraycopy(dataBytes, 1, payload, payloadBase, dataBytes.length - 1);
                    payloadBase += dataBytes.length - 1;
                    return dataBytes.length * 8 * windowWidth * 2;
                } else {
                    System.arraycopy(dataBytes, 1, payload, payloadBase, payloadLength);
                    payloadBase = -1;
                    isPreamble = false;
                    showRecvText();
                    return (payloadLength + 1) * 8 * windowWidth * 2;
                }
            }
        }
        else{
            byte[] dataBytes = bits2Byte(dataBits);
            if (dataBytes.length < payloadLength - payloadBase) {
                System.arraycopy(dataBytes, 0, payload, payloadBase, dataBytes.length);
                payloadBase += dataBytes.length ;
                return dataBytes.length * 8 * windowWidth * 2;
            } else {
                System.arraycopy(dataBytes, 0, payload, payloadBase, payloadLength-payloadBase);
                payloadBase = -1;
                isPreamble = false;
                showRecvText();
                return (payloadLength-payloadBase) * 8 * windowWidth * 2;
            }
        }

        return -1;
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    void showRecvText(){
        String text = new String(payload, StandardCharsets.UTF_8);
        recvTextInput.setText(text);
    }

    int argMax(double [] arr) {
        double max = arr[0];
        int maxIdx = 0;
        for(int i = 1; i < arr.length; i++) {
            if(arr[i] > max) {
                max = arr[i];
                maxIdx = i;
            }
        }
        return maxIdx;
    }

    public int checkPreamble(byte[] buffer){
        byte[] dataBits = signal2DataBits(buffer);
        int bitsLength = dataBits.length;

//        debugBytes(dataBits,false);

//        int currentDataBitsIndex = 0;
        int currentPreambleBitsIndex= 0;
        for(int i=0;i<bitsLength;i++){
            if(dataBits[i]==PREAMBLE_BITS[currentPreambleBitsIndex]){
                currentPreambleBitsIndex++;
            }else{
                currentPreambleBitsIndex=0;
            }
        }

        if(currentPreambleBitsIndex == 8){
            isPreamble=true;
        }

        return (bitsLength-currentPreambleBitsIndex)*windowWidth*2;
    }

    private void copyWaveFile(String inFileName, String outFileName, int samplingRate, int bufferSize) {
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
        try {
            in = new FileInputStream(inFileName);
            out = new FileOutputStream(outFileName);
            // get real data length
            totalAudioLen = in.getChannel().size();
            totalDataLen = totalAudioLen + 36;
            // write .wav header
            writeWaveFileHeader(out, totalAudioLen, totalDataLen, longSampleRate, channels, byteRate);
            // write raw data to .wav
            while (in.read(data) != -1) {
                out.write(data);
            }
            in.close();
            out.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
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