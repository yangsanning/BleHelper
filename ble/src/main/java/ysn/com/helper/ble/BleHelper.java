package ysn.com.helper.ble;

import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @Author yangsanning
 * @ClassName BleHelper
 * @Description 一句话概括作用
 * @Date 2019/8/30
 * @History 2019/8/30 author: description:
 */
public class BleHelper {

    /**
     * 打开蓝牙的请求码
     */
    public static final int REQUEST_ENABLE_BLE = 1;

    /**
     * 蓝牙默认扫描时间
     */
    private static final long SCAN_DEFAULT_MILLIS = 15 * 1000;

    private Activity activity;
    private BluetoothAdapter bluetoothAdapter;
    private HashMap<String, BluetoothGatt> bleGattMap = new LinkedHashMap<>();
    private byte[][] datas;

    /**
     * 是否正在扫描
     */
    private boolean isScanning = false;
    private Handler scanHandler;
    private Runnable scanRunnable = new Runnable() {
        @Override
        public void run() {
            activity.runOnUiThread(() -> stopScan());
        }
    };

    /**
     * 服务和特征值
     */
    private UUID write_UUID_service;
    private UUID write_UUID_chara;
    private UUID read_UUID_service;
    private UUID read_UUID_chara;
    private UUID notify_UUID_service;
    private UUID notify_UUID_chara;
    private UUID indicate_UUID_service;
    private UUID indicate_UUID_chara;

    private OnBleScanStateListener onBleScanListener;
    private OnBleWriteInterceptListener onBleWriteInterceptListener;
    private HashMap<String, OnBleConnectStateListener> onBleConnectStateListenerMap = new LinkedHashMap<>();

    public static BleHelper instance;

    private static BleHelper create(Activity activity) {
        instance = new BleHelper(activity);
        return instance;
    }

    public static synchronized BleHelper get(Activity activity) {
        if (instance == null) {
            synchronized (BleHelper.class) {
                if (instance == null) {
                    instance = create(activity);
                }
            }
        }
        return instance;
    }

    public void destroy() {
        close();
        instance = null;
    }

    private BleHelper(Activity activity) {
        this.activity = activity;
        scanHandler = new Handler(activity.getMainLooper());
        BluetoothManager bluetoothManager = (BluetoothManager) this.activity.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
    }

    /**
     * 打开蓝牙
     */
    public BleHelper openBluetooth(Runnable runnable) {
        if (isEnabled()) {
            runnable.run();
        } else {
            activity.startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE_BLE);
        }
        return this;
    }

    /**
     * 用于判断蓝牙是否打开
     *
     * @return 蓝牙是否开启
     */
    public boolean isEnabled() {
        return isInitBluetoothAdapter() && bluetoothAdapter.isEnabled();
    }

    /**
     * 检查适配器是否初始化
     */
    private boolean isInitBluetoothAdapter() {
        if (bluetoothAdapter == null) {
            if (onBleScanListener != null) {
                onBleScanListener.onError("bluetoothAdapter is null");
            }
            return false;
        } else {
            return true;
        }
    }

    /**
     * 开始扫描
     */
    public void startScan() {
        startScan(SCAN_DEFAULT_MILLIS);
    }

    /**
     * 开始扫描
     *
     * @param scanMillis 扫描时间
     */
    public void startScan(long scanMillis) {
        stopScan();
        if (isInitBluetoothAdapter() && !isScanning) {
            isScanning = true;
            if (onBleScanListener != null) {
                onBleScanListener.onStartScan();
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                bluetoothAdapter.getBluetoothLeScanner().startScan(highScanCallback);
            } else {
                bluetoothAdapter.startLeScan(scanCallback);
            }
            scanHandler.removeCallbacks(scanRunnable);
            scanHandler.postDelayed(scanRunnable, scanMillis);
        }
    }

    /**
     * 结束扫描
     */
    public void stopScan() {
        if (isInitBluetoothAdapter()) {
            isScanning = false;
            if (onBleScanListener != null) {
                onBleScanListener.onStopScan();
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                BluetoothLeScanner bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
                if (bluetoothLeScanner != null) {
                    bluetoothLeScanner.stopScan(highScanCallback);
                }
            } else {
                bluetoothAdapter.stopLeScan(scanCallback);
            }
            scanHandler.removeCallbacks(scanRunnable);
        }
    }

    /**
     * 蓝牙扫描回调
     */
    private BluetoothAdapter.LeScanCallback scanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            if (onBleScanListener != null) {
                onBleScanListener.onLeScanCallback(device, rssi);
            }
        }
    };

    /**
     * 高版本蓝牙扫描回调
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private ScanCallback highScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (onBleScanListener != null) {
                onBleScanListener.onLeScanCallback(result.getDevice(), result.getRssi());
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
        }
    };

    /**
     * 蓝牙连接
     */
    public synchronized void connect(String address) {
        for (Map.Entry<String, OnBleConnectStateListener> entry : onBleConnectStateListenerMap.entrySet()) {
            if (address.equals(entry.getKey())) {
                entry.getValue().onConnectStart("蓝牙正在连接");
            }
        }

        final BluetoothDevice bluetoothDevice = bluetoothAdapter.getRemoteDevice(address);
        BluetoothGatt bluetoothGatt;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            bluetoothGatt = bluetoothDevice.connectGatt(activity, true, new BleGattCallback(address),
                BluetoothDevice.TRANSPORT_LE);
        } else {
            bluetoothGatt = bluetoothDevice.connectGatt(activity, true, new BleGattCallback(address));
        }
        bleGattMap.put(address, bluetoothGatt);
    }

    /**
     * 断开蓝牙连接
     */
    public synchronized void disConnect(String address) {
        BluetoothGatt bluetoothGatt = bleGattMap.get(address);
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
        }
        bleGattMap.remove(address);

        for (Map.Entry<String, OnBleConnectStateListener> entry : onBleConnectStateListenerMap.entrySet()) {
            if (address.equals(entry.getKey())) {
                entry.getValue().onDisConnect(("蓝牙断开连接"));
            }
        }
    }

    /**
     * 断开所有蓝牙连接
     */
    public synchronized void disConnect() {
        for (Map.Entry<String, BluetoothGatt> entry : bleGattMap.entrySet()) {
            entry.getValue().disconnect();
            // todo 监听
        }
    }

    /**
     * 关闭蓝牙连接
     */
    public synchronized void close(String address) {
        BluetoothGatt bluetoothGatt = bleGattMap.get(address);
        if (bluetoothGatt != null) {
            bluetoothGatt.close();
        }
        bleGattMap.remove(address);
    }

    /**
     * 关闭所有蓝牙连接
     */
    public synchronized void close() {
        for (Map.Entry<String, BluetoothGatt> entry : bleGattMap.entrySet()) {
            BluetoothGatt bluetoothGatt = entry.getValue();
            bluetoothGatt.close();
        }
        bleGattMap.clear();
    }

    private class BleGattCallback extends BluetoothGattCallback {

        private String address;

        public BleGattCallback(String address) {
            this.address = address;
        }

        /**
         * 断开或连接 状态发生变化时调用
         */
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            switch (status) {
                case BluetoothGatt.GATT_SUCCESS:
                    // 连接成功
                    if (newState == BluetoothGatt.STATE_CONNECTED) {
                        //发现服务
                        gatt.discoverServices();
                    }
                    break;
                default:
                    // 连接失败
                    disConnect(address);
                    activity.runOnUiThread(() -> {
                        for (Map.Entry<String, OnBleConnectStateListener> entry : onBleConnectStateListenerMap.entrySet()) {
                            if (address.equals(entry.getKey())) {
                                entry.getValue().onConnectFailure(("蓝牙连接失败"));
                            }
                        }

                    });
                    break;
            }

            if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                close(address);
            }
        }

        /**
         * 发现设备（真正建立连接）
         */
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            // 获取初始化服务和特征值
            initServiceAndChara(address);
            // 订阅通知
            BluetoothGatt bluetoothGatt = bleGattMap.get(address);
            if (bluetoothGatt != null) {
                bluetoothGatt.setCharacteristicNotification(bluetoothGatt
                    .getService(notify_UUID_service).getCharacteristic(notify_UUID_chara), true);

            }
            activity.runOnUiThread(() -> {
                for (Map.Entry<String, OnBleConnectStateListener> entry : onBleConnectStateListenerMap.entrySet()) {
                    if (address.equals(entry.getKey())) {
                        entry.getValue().onConnectSuccess(("蓝牙连接成功"));
                        write(address, datas);
                    }
                }
            });
        }

        /**
         * 读操作的回调
         */
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
        }

        /**
         * 写操作的回调
         */
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
        }

        /**
         * 接收到硬件返回的数据
         */
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
        }
    }

    private void initServiceAndChara(String address) {
        BluetoothGatt bluetoothGatt = bleGattMap.get(address);
        if (bluetoothGatt == null) {
            return;
        }
        List<BluetoothGattService> bluetoothGattServices = bluetoothGatt.getServices();
        for (BluetoothGattService bluetoothGattService : bluetoothGattServices) {
            List<BluetoothGattCharacteristic> characteristics = bluetoothGattService.getCharacteristics();
            for (BluetoothGattCharacteristic characteristic : characteristics) {
                int charaProp = characteristic.getProperties();
                if ((charaProp & BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                    read_UUID_chara = characteristic.getUuid();
                    read_UUID_service = bluetoothGattService.getUuid();
                }
                if ((charaProp & BluetoothGattCharacteristic.PROPERTY_WRITE) > 0) {
                    write_UUID_chara = characteristic.getUuid();
                    write_UUID_service = bluetoothGattService.getUuid();
                }
                if ((charaProp & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) > 0) {
                    write_UUID_chara = characteristic.getUuid();
                    write_UUID_service = bluetoothGattService.getUuid();
                }
                if ((charaProp & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                    notify_UUID_chara = characteristic.getUuid();
                    notify_UUID_service = bluetoothGattService.getUuid();
                }
                if ((charaProp & BluetoothGattCharacteristic.PROPERTY_INDICATE) > 0) {
                    indicate_UUID_chara = characteristic.getUuid();
                    indicate_UUID_service = bluetoothGattService.getUuid();
                }
            }
        }
    }

    /**
     * 发送数据
     */
    public void write(String address, byte[]... datas) {
        if (skipWrite(address)) {
            return;
        }
        if (datas == null) {
            return;
        }
        BluetoothGatt bluetoothGatt = bleGattMap.get(address);
        if (bluetoothGatt == null) {
            this.datas = datas;
            connect(address);
            return;
        }
        BluetoothGattService service = bluetoothGatt.getService(write_UUID_service);
        if (service == null) {
            return;
        }
        BluetoothGattCharacteristic charaWrite = service.getCharacteristic(write_UUID_chara);
        for (byte[] data : datas) {
            charaWrite.setValue(data);
            bluetoothGatt.writeCharacteristic(charaWrite);
        }
        this.datas = null;
    }

    /**
     * 发送数据
     */
    public void write(String address, String... datas) {
        if (skipWrite(address)) {
            return;
        }
        BluetoothGatt bluetoothGatt = bleGattMap.get(address);
        if (bluetoothGatt == null) {
            return;
        }
        BluetoothGattService service = bluetoothGatt.getService(write_UUID_service);
        if (service == null) {
            return;
        }
        BluetoothGattCharacteristic charaWrite = service.getCharacteristic(write_UUID_chara);
        for (String data : datas) {
            charaWrite.setValue(data);
            bluetoothGatt.writeCharacteristic(charaWrite);
        }
    }

    private boolean skipWrite(String address) {
        return onBleWriteInterceptListener != null && onBleWriteInterceptListener.skipWrite(address);
    }

    public void setOnBleScanListener(OnBleScanStateListener onBleScanListener) {
        this.onBleScanListener = onBleScanListener;
    }

    public void addBleConnectStateListenerList(String address, OnBleConnectStateListener onBleConnectStateListener) {
        if (!onBleConnectStateListenerMap.containsKey(address)) {
            onBleConnectStateListenerMap.put(address, onBleConnectStateListener);
        }
    }

    public void removeBleConnectStateListenerList(String address) {
        onBleConnectStateListenerMap.remove(address);
    }

    public void setOnBleWriteInterceptListener(OnBleWriteInterceptListener onBleWriteInterceptListener) {
        this.onBleWriteInterceptListener = onBleWriteInterceptListener;
    }

    /**
     * 蓝牙扫描监听器
     */
    public interface OnBleScanStateListener {

        /**
         * 开始扫描
         */
        void onStartScan();

        /**
         * 结束扫描
         */
        void onStopScan();

        /**
         * 蓝牙扫描回调
         *
         * @param device 扫描到的设备
         * @param rssi   信号值
         */
        void onLeScanCallback(BluetoothDevice device, int rssi);

        /**
         * 错误信息回调
         */
        void onError(String msg);
    }

    /**
     * 蓝牙连接状态监听器
     */
    public interface OnBleConnectStateListener {

        /**
         * 连接开始
         */
        void onConnectStart(String msg);

        /**
         * 连接成功
         */
        void onConnectSuccess(String msg);

        /**
         * 连接失败
         */
        void onConnectFailure(String msg);

        /**
         * 断开连接
         */
        void onDisConnect(String msg);
    }

    /**
     * 蓝牙写入拦截监听器
     */
    public interface OnBleWriteInterceptListener {

        /**
         * @param address 蓝牙地址
         * @return true: 跳过写入, false: 继续写入
         */
        boolean skipWrite(String address);
    }
}