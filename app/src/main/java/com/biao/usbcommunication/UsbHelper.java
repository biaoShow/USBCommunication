package com.biao.usbcommunication;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**
 * usb帮助类
 * Created by benxiang on 2019/9/2.
 */

public class UsbHelper {
    private static final String TAG = "UsbHelper";
    public static final String ACTION_USB_PERMISSION = "com.lcitschan.devicesbrowser.receiver.USB_PERMISSION";
    private static final int NO_PERMISSION = -1;
    private static final int GET_DEVICE_INTERFACE_FAIL = -2;
    private static final int OPEN_DEVICE_FAIL = -3;
    private static final int NO_ENDPPINT = -4;
    private static final int CLAIM_FAIL = -5;
    private static final int STREAM_FAIL = -6;
    private static final int NO_DEVICE = -7;
    private static final int SUCCEED = 1;
    private OpenDeviceInterface openDeviceInterface;
    private static UsbHelper usbHelper;
    private UsbManager usbManager;
    private Context context;
    private UsbInterface usbInterface;//usb接口
    private UsbDeviceConnection usbDeviceConnection;//usb连接对象
    private UsbEndpoint usbInputStream;//usb输入流
    private UsbEndpoint usbOutputStream;//usb输出流
    private int sendTimeout = 5000;
    private int readTimeout = 5000;

    private UsbHelper(Context context) {
        this.context = context;
        usbManager = (UsbManager) context.getApplicationContext().getSystemService(Context.USB_SERVICE);
    }

    public static UsbHelper getInstance(Context context) {
        if (null == usbHelper) {
            usbHelper = new UsbHelper(context);
        }
        return usbHelper;
    }

    public void setOpenDeviceInterface(OpenDeviceInterface openDeviceInterface) {
        this.openDeviceInterface = openDeviceInterface;
    }

    /**
     * 获取所有usb设备
     *
     * @return
     */
    private List<UsbDevice> getUsbDevices() {
        List<UsbDevice> devices = new ArrayList<>();
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        Iterator<UsbDevice> devicesIterator = deviceList.values().iterator();
        while (devicesIterator.hasNext()) {
            devices.add(devicesIterator.next());
        }
        return devices;
    }

    /**
     * 选择指定Vid和Pid的usb设备
     */
    private UsbDevice selectDevice(int vid, int pid) {
        UsbDevice usbDevice = null;
        List<UsbDevice> usbDevices = getUsbDevices();
        for (UsbDevice u : usbDevices) {
            if (vid == u.getVendorId() && pid == u.getProductId()) {
                usbDevice = u;
            }
        }
        return usbDevice;
    }

    /**
     * 打开指定usb设备
     *
     * @param vid
     * @param pid
     */
    public void openDevice(int vid, int pid) {
        UsbDevice usbDevice = selectDevice(vid, pid);
        if (!usbManager.hasPermission(usbDevice)) {
            Log.e(TAG, "没有USB权限--正在获取");
            IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
            context.registerReceiver(mUsbPermissionReceiver, filter);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context.getApplicationContext(),
                    10, new Intent(ACTION_USB_PERMISSION), 0);
            usbManager.requestPermission(usbDevice, pendingIntent);
        } else {
            int result = openDevice(usbDevice);
            Log.i(TAG, "打开USB设备结果：" + result);
        }
    }

    private int openDevice(UsbDevice usbDevice) {
        if (null != usbDevice) {
            int interfaceNum = usbDevice.getInterfaceCount();
            if (interfaceNum <= 0) {
                Log.e(TAG, "获取设备接口失败");
                return GET_DEVICE_INTERFACE_FAIL;
            }
            usbInterface = usbDevice.getInterface(0);
            usbDeviceConnection = usbManager.openDevice(usbDevice);
            if (usbDeviceConnection == null) {
                Log.e(TAG, "打开USB设备失败");
                return OPEN_DEVICE_FAIL;
            }
            int endpointCount = usbInterface.getEndpointCount();
            if (usbInterface.getEndpointCount() <= 0) {
                Log.e(TAG, "设备无端口可用");
                return NO_ENDPPINT;
            }
            for (int i = 0; i < endpointCount; i++) {
                UsbEndpoint endpoint = usbInterface.getEndpoint(i);
                if (endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (endpoint.getDirection() == UsbConstants.USB_DIR_OUT) {
                        usbOutputStream = endpoint;
                    } else if (endpoint.getDirection() == UsbConstants.USB_DIR_IN) {
                        usbInputStream = endpoint;
                    }
                }
            }
            if (usbOutputStream != null && usbInputStream != null) {
                if (!usbDeviceConnection.claimInterface(usbInterface, true)) {
                    Log.e(TAG, "获取操作权限失败");
                    return CLAIM_FAIL;
                }
            } else {
                usbDeviceConnection.close();
                Log.e(TAG, "获取流失败");
                return STREAM_FAIL;
            }
        } else {
            Log.e(TAG, "检测不到此USB设备");
            return NO_DEVICE;
        }
        if (null != openDeviceInterface) {
            openDeviceInterface.openSucceed();
        }
        return SUCCEED;
    }


    /**
     * 发送数据
     */
    public void sendData(byte[] data) {
        if (usbDeviceConnection == null || usbOutputStream == null) return;
        int offset = 0;//开始copy标识位
        int length = data.length;//发送数据剩余长度
        while (length > 0) {
            byte[] buffer = new byte[Math.min(512, length)];
            System.arraycopy(data, offset, buffer, 0, buffer.length);
            usbDeviceConnection.bulkTransfer(usbOutputStream, buffer, buffer.length, sendTimeout);
            offset += buffer.length;
            length -= buffer.length;
        }
    }

    /**
     * 读取数据
     *
     * @param data
     * @return
     */
    public int readData(byte[] data) {
        if (usbDeviceConnection == null || usbInputStream == null) return -1;
        return usbDeviceConnection.bulkTransfer(usbInputStream, data, data.length, readTimeout);
    }

    private BroadcastReceiver mUsbPermissionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                if (granted) {
                    //获得了usb使用权限
                    Log.i(TAG, "获取到了USB权限");
                    int result = openDevice(device);
                    Log.i(TAG, "打开USB设备结果：" + result);
                }
            }
        }
    };


    interface OpenDeviceInterface {
        void openSucceed();
    }
}
