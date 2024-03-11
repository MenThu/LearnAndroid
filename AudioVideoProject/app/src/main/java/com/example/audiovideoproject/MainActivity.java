package com.example.audiovideoproject;

import android.Manifest;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;

import com.example.mylibrary.Utils;
import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Environment;
import android.util.Log;
import android.view.View;

import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.audiovideoproject.databinding.ActivityMainBinding;

import android.view.Menu;
import android.view.MenuItem;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;
import com.example.mylibrary.Utils;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;


    private boolean isRecording = false;
    private static final int SAMPLE_RATE_IN_HZ = 8000; // 设置音频采样率
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO; // 设置单声道输入
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT; // 音频编码格式
    private static final String RECORDING_FILE_PATH = "menthuugan_learn_record.wav";//存储音频文件的路径
    private static final String LOG_TAG = "Menthuguan-Audio-Debug";
    private static final int RC_PERMISSION = 0x1;


    private AudioRecord mAudioRecord;
    private Thread recordingThread;


    protected void onCreate(Bundle savedInstanceState) {
        Utils.DebugLog();

        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

        binding.fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (false == isRecording){
                    //开始录制音频
                    methodRequiresPermission();
                }else{
                    //停止录制
                    stopRecording();
                }
            }
        });
    }

    @AfterPermissionGranted(RC_PERMISSION)
    private void methodRequiresPermission() {
        Log.d(LOG_TAG, "methodRequiresPermission");
        String[] perms = {Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.INTERNET, Manifest.permission.ACCESS_NETWORK_STATE};
        if (EasyPermissions.hasPermissions(this, perms)) {
            Log.d(LOG_TAG, "hasPermissions");
            startRecording();
        } else {
            Log.d(LOG_TAG, "do not have, quest");
            EasyPermissions.requestPermissions(this, getString(R.string.app_name), RC_PERMISSION, perms);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        Log.d(LOG_TAG, "onRequestPermissionsResult, requestCode=["+requestCode+"] permissions=["+permissions+"] grantResults=["+grantResults+"]");
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    private void startRecording(){
        Log.d(LOG_TAG, "startRecording isRecording=["+isRecording+"]");
        if (isRecording){
            return;
        }
        int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE_IN_HZ,
                CHANNEL_CONFIG, AUDIO_FORMAT);
        mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE_IN_HZ,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize);
        if (mAudioRecord == null){
            Log.d(LOG_TAG, "new AudioRecord failed");
            return;
        }

        try {
            mAudioRecord.startRecording();
        } catch (IllegalStateException e) {
            e.printStackTrace();
            Log.d(LOG_TAG, "startRecording error");
        }


        recordingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                writeRecordData2File();
            }
        }, "Menthuguan Record Thread");

        recordingThread.start();

        isRecording = true;
    }

    private void stopRecording(){
        String audioRecordIsNull = mAudioRecord == null ? "null" : "has value";
        Log.d(LOG_TAG, "stopRecording, audioRecordIsNull=["+audioRecordIsNull+"]");
        if (mAudioRecord != null){
            isRecording = false;
            mAudioRecord.stop();
            mAudioRecord.release();
            mAudioRecord = null;
            recordingThread = null;
        }
    }

    private void writeRecordData2File(){
        String dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).getPath() + "/";
        File fileDir = new File(dir);
        if (fileDir.exists() == false){
            try {
                fileDir.mkdirs();
            }catch (Exception e){
                e.printStackTrace();
                Log.d(LOG_TAG, "fileDir mkdir error");
                return;
            }
        }
        String fullPathName = dir + RECORDING_FILE_PATH;
        File audioFile = new File(fullPathName);
        if (audioFile.exists()) {
            boolean succ = audioFile.delete();
            Log.d(LOG_TAG, "delete file=["+succ+"]");
        }

        try {
            audioFile.createNewFile();
        } catch (IOException e){
            e.printStackTrace();
            Log.d(LOG_TAG, "createNewFile error");
        }

        byte[] data = new byte[1024];
        FileOutputStream fos = null;

        try {
            fos = new FileOutputStream(fullPathName);

            Log.d(LOG_TAG, "open file to write=["+fullPathName+"]");

            //写入WAVE头
            byte[] header = new byte[44];
            byte bitPerSample = 16;
            byte channelNum = CHANNEL_CONFIG == AudioFormat.CHANNEL_IN_MONO ? 1 : 2;
            int byteRate = channelNum * SAMPLE_RATE_IN_HZ * bitPerSample/8;
            int totalDataLen = 0;
            int sampleRate = SAMPLE_RATE_IN_HZ;
            int totalAudioLen = 0;

            header[0] = 'R'; // RIFF/WAVE header
            header[1] = 'I';
            header[2] = 'F';
            header[3] = 'F';
            header[4] = (byte) (totalDataLen & 0xff);
            header[5] = (byte) ((totalDataLen >> 8) & 0xff);
            header[6] = (byte) ((totalDataLen >> 16) & 0xff);
            header[7] = (byte) ((totalDataLen >> 24) & 0xff);
            header[8] = 'W'; //WAVE
            header[9] = 'A';
            header[10] = 'V';
            header[11] = 'E';
            header[12] = 'f'; // 'fmt ' chunk
            header[13] = 'm';
            header[14] = 't';
            header[15] = ' ';
            header[16] = 16; // 4 bytes: size of 'fmt ' chunk
            header[17] = 0;
            header[18] = 0;
            header[19] = 0;
            header[20] = 1; // format = 1
            header[21] = 0;
            header[22] = channelNum;
            header[23] = 0;
            header[24] = (byte) (sampleRate & 0xff);
            header[25] = (byte) ((sampleRate >> 8) & 0xff);
            header[26] = (byte) ((sampleRate >> 16) & 0xff);
            header[27] = (byte) ((sampleRate >> 24) & 0xff);
            header[28] = (byte) (byteRate & 0xff);
            header[29] = (byte) ((byteRate >> 8) & 0xff);
            header[30] = (byte) ((byteRate >> 16) & 0xff);
            header[31] = (byte) ((byteRate >> 24) & 0xff);
            header[32] = (byte) (channelNum * bitPerSample / 8); // block align
            header[33] = 0;
            header[34] = bitPerSample; // bits per sample
            header[35] = 0;
            header[36] = 'd'; //data
            header[37] = 'a';
            header[38] = 't';
            header[39] = 'a';
            header[40] = (byte) (totalAudioLen & 0xff);
            header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
            header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
            header[43] = (byte) ((totalAudioLen >> 24) & 0xff);
            fos.write(header, 0, 44);

            Log.d(LOG_TAG, "write wave head");

            while (isRecording){
                int read = mAudioRecord.read(data, 0, data.length);
                if (AudioRecord.ERROR_INVALID_OPERATION != read){
                    fos.write(data);
                }
            }
        } catch (IOException e){
            e.printStackTrace();
            Log.d(LOG_TAG, "new FileOutputStream error");
        } finally {
            try {
                if(fos != null){
                    fos.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
                Log.d(LOG_TAG, "close FileOutputStream error");
            }
        }
        Log.d(LOG_TAG, "record finish");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }
}