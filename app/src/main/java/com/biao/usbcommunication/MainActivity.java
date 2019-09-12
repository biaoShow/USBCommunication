package com.biao.usbcommunication;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity implements UsbHelper.OpenDeviceInterface {

    private static final String TAG = "MainActivity";
    private UsbHelper usbHelper;
    private boolean isOpenSucceed = false;
    private HandlerThread handlerThread;
    private Handler handler;

    private Runnable sendRunnable = new Runnable() {
        @Override
        public void run() {
//            byte[] readData = new byte[64];
            String s = "dshflkasdj考虑到斯洛伐克是劳动法了了首付款dlkhsldfjlksdjlksdj 13215464894213214854651\n"
                    + "jhdlkfsd合理就是啦打开就lsdhfkldlkj\n"
                    + "kldshlsdldhgsid哈萨克了高哈萨克\n";
//                    + "dlfkhsldafdfl索拉卡杜绝浪费收到了是点击父类控件\n";
            try {
                usbHelper.sendData(s.getBytes("GBK"));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
//            usbHelper.readData(readData);
//            Log.i(TAG, Arrays.toString(readData));
        }
    };
    private Runnable receiveRunnable = new Runnable() {
        @Override
        public void run() {
            byte[] readData = new byte[64];
            int readResult = usbHelper.readData(readData);
            Log.i(TAG, Arrays.toString(readData) + "---" + readResult);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        usbHelper = UsbHelper.getInstance(this);
        usbHelper.setOpenDeviceInterface(this);
        handlerThread = new HandlerThread("openUsbHandler");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
        usbHelper.openDevice(1155, 30016);
    }

    /**
     * 打开usb设备成功
     */
    @Override
    public void openSucceed() {
        isOpenSucceed = true;
    }

    public void send(View view) {
        if (isOpenSucceed) {
            handler.post(sendRunnable);
        } else {
            Toast.makeText(this, "设备尚未成功打开", Toast.LENGTH_SHORT).show();
        }
    }

    public void receive(View view) {
        handler.post(receiveRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        usbHelper.setOpenDeviceInterface(null);
        handler.removeCallbacks(sendRunnable);
        handler.removeCallbacks(receiveRunnable);
    }
}
