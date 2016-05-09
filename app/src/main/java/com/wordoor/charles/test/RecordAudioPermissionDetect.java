package com.wordoor.charles.test;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.Settings;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.util.Log;

/**
 * 根据分贝值判断当前应用的录音权限是否被第三方应用禁止了
 *
 * Created by zhuyifei on 2016/5/6.
 */
public class RecordAudioPermissionDetect {

    public static final int TIME_INTERVAL = 5;//每5秒提醒一次用户

    private final int audioSource = MediaRecorder.AudioSource.MIC;
    // 设置音频采样率，44100是目前的标准，但是某些设备仍然支持22050,16000,11025
    private final int sampleRateInHz = 16000;
    // 设置音频的录制的声道CHANNEL_IN_STEREO为双声道，CHANNEL_CONFIGURATION_MONO为单声道
    private final int channelConfig = AudioFormat.CHANNEL_IN_STEREO;
    // 音频数据格式:PCM 16位每个样本。保证设备支持。PCM 8位每个样本。不一定能得到设备支持。
    private final int audioFormat = AudioFormat.ENCODING_PCM_16BIT;

    private int mInBufSize = 0;

    private AudioRecord mAudioRecord;

    private boolean mIsRecord = false;
    private boolean mIsFilterModel = false;
    private boolean mIsPermissionPermit = false;
    private boolean mIsOnSDK23 = false;
    private Context mContext;

    public RecordAudioPermissionDetect(Context context, onPermitRecordListener onPermitRecordListener){
        mContext = context;
        mIsFilterModel = setIsFilterMode();
        mOnPermitRecordListener = onPermitRecordListener;
        mIsOnSDK23 = (getTargetVersion() >= 23) && (Build.VERSION.SDK_INT >= 23);
    }

    /**
     * TODO 对特定机型适配，需要的通过 或 操作 增加，如以下对小米，华为手机进行适配处理
     * @return
     */
    private boolean setIsFilterMode() {
        return DeviceInfoUtil.isXiaoMi() || DeviceInfoUtil.isHUAWEI();
    }

    /**
     * 初始化对象
     */
    private void initAudioRecord(){

        mInBufSize = AudioRecord.getMinBufferSize(
                sampleRateInHz,
                channelConfig,
                audioFormat);

        mAudioRecord = new AudioRecord(
                audioSource,
                sampleRateInHz,
                channelConfig,
                audioFormat,
                mInBufSize);
    }

    private AudioRecordTask mAudioRecordTask;
    private onPermitRecordListener mOnPermitRecordListener;
    /**
     * 开始检查
     */
    public void startCheckRecordPermission(){
        mIsRecord = false;
        //mIsFilterModel 对特定机型进行判断
        if (mIsFilterModel) {
            if (!mIsPermissionPermit) { //mIsPermissionPermit = true时，表示 检测到录音权限允许，或者被用户主动打开禁用的录音权限，这时不需要再进行检测了
                initAudioRecord();
                mAudioRecordTask = new AudioRecordTask();
                mAudioRecordTask.execute();
            }
        }
    }

    class AudioRecordTask extends AsyncTask<Void, Void, Boolean>{

        @Override
        protected Boolean doInBackground(Void... params) {
            if(mAudioRecord == null){
                initAudioRecord();
            }
            if (mIsOnSDK23) {
                mIsRecord = true;
                if (lacksPermission(Manifest.permission.RECORD_AUDIO)) {
                    mIsPermissionPermit = true;
                    return false;
                } else {
                    return true;
                }
            } else {
                try {
                    byte[] b = new byte[mInBufSize /4];
                    //开始录制音频
                    try{
                        // 防止某些手机崩溃，例如联想
                        mAudioRecord.startRecording();
                    }catch (IllegalStateException e){
                        e.printStackTrace();
                    }
                    //判断是否正在录制
                    mIsRecord = true;
                    long wait = 0;
                    long maxWait = 10;
                    while(mIsRecord){
                        //r是实际读取的数据长度，一般而言r会小于buffersize
                        int r = mAudioRecord.read(b, 0, b.length);
                        long v = 0;
                        // 将 buffer 内容取出，进行平方和运算
                        for (int i = 0; i < b.length; i++) {
                            v += b[i] * b[i];
                        }
                        // 平方和除以数据总长度，得到音量大小。
                        double mean = v / (double) r;
                        double volume = 10 * Math.log10(mean);
                        wait++;
                        if(wait > maxWait){
                            Log.e(this.getClass().getName(), "分贝值:" + volume + " " + (volume > 0));
                            stopRecord();
                            if(volume > 0){
                                mIsPermissionPermit = true;
                                return true;
                            } else {
                                return false;
                            }
                        }
                    }
                    //停止录制
                    stopRecord();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return false;
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            mOnPermitRecordListener.isPermit(aBoolean);
        }
    }

    private void stopRecord() {
        //停止录制
        try {
            // 防止某些手机崩溃，例如联想
            mAudioRecord.stop();
            // 彻底释放资源
            mAudioRecord.release();
            mAudioRecord = null;
        }catch (IllegalStateException e){
            e.printStackTrace();
        }
    }

    public void stopCheck() {
        mIsRecord = false;
    }

    public interface onPermitRecordListener {
        void isPermit(boolean flag);
    }

    private boolean isAlertShowing = false;
    // 显示缺失权限提示
    public void showMissingPermissionDialog(final Context context, int srcMessageId) {
        if (mIsRecord && !isAlertShowing) {
            isAlertShowing = true;
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(R.string.permission_hint);
            builder.setMessage(srcMessageId);
            builder.setPositiveButton(R.string.permission_btn_ok, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                    isAlertShowing = false;
                }
            });
            builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    isAlertShowing = false;
                }
            });
            builder.show();
        }
    }

    private static final String PACKAGE_URL_SCHEME = "package:"; // 方案
    // 启动应用的设置
    private void startAppSettings(Context context) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse(PACKAGE_URL_SCHEME + context.getPackageName()));
        context.startActivity(intent);
    }

    // 判断是否缺少权限
    private boolean lacksPermission(String permission) {
        return ContextCompat.checkSelfPermission(mContext, permission) ==
                PackageManager.PERMISSION_DENIED;
    }

    private int getTargetVersion() {
        int targetSdkVersion = -1;
        try {
            PackageInfo packageInfo = mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0);
            targetSdkVersion = packageInfo.applicationInfo.targetSdkVersion;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return targetSdkVersion;
    }

}
