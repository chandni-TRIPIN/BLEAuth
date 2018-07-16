package bleauth.satinpod.com.bleauth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends AppCompatActivity {
private final static String TAG = "BLE";
    private BluetoothAdapter mBluetoothAdapter;
    BluetoothDevice mBluetoothDevice;
    private boolean mScanning;
    private Handler mHandler;

    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;
    private BluetoothAdapter.LeScanCallback mLeScanCallback;

    private BluetoothManager mBluetoothManager;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private int mConnectionState = STATE_DISCONNECTED;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.example.bluetooth.le.EXTRA_DATA";
    private BluetoothGattCallback mGattCallback ;

    private BluetoothGattCharacteristic mCharIdentify;
    private BluetoothGattCharacteristic mCharAuthentication;
    private BluetoothGattCharacteristic mCharCommandTx;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Use this check to determine whether BLE is supported on the device. Then
// you can selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "ble_not_supported", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            Log.v(TAG, "Ble Supported devices");
            init();
            scanLeDevice(true);
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter intent = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        registerReceiver(mPairReceiver, intent);
    }

    @Override
    protected void onPause() {
        super.onPause();
        scanLeDevice(false);
    }

    private void init() {
        // Initializes Bluetooth adapter.
        mHandler = new Handler();
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        mLeScanCallback  =  new BluetoothAdapter.LeScanCallback() {
                    @Override
                    public void onLeScan(final BluetoothDevice device, int rssi,
                                         byte[] scanRecord) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {

                                Log.v(TAG, "Bluetooth Device Found : " + device.getAddress());

                                if(device.getAddress().equals("00:0B:57:22:B0:74")) {
                                    mBluetoothDevice = device;
                                    scanLeDevice(false);
                                    mBluetoothGatt = device.connectGatt(MainActivity.this, false, mGattCallback);
                                }

                            }
                        });
                    }
                };

        mGattCallback = new BluetoothGattCallback() {
            @Override
            public void onPhyUpdate(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
                super.onPhyUpdate(gatt, txPhy, rxPhy, status);
                Log.v(TAG, "onPhyUpdate");
            }

            @Override
            public void onPhyRead(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
                super.onPhyRead(gatt, txPhy, rxPhy, status);
                Log.v(TAG, "onPhyRead");

            }

            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                super.onConnectionStateChange(gatt, status, newState);
                Log.v(TAG, "onConnectionStateChange : " + newState );
                String intentAction;
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    intentAction = ACTION_GATT_CONNECTED;
                    mConnectionState = STATE_CONNECTED;
                    Log.i(TAG, "Connected to GATT server.");
                    Log.i(TAG, "Attempting to start service discovery:" +
                            mBluetoothGatt.discoverServices());

                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    intentAction = ACTION_GATT_DISCONNECTED;
                    mConnectionState = STATE_DISCONNECTED;
                    Log.i(TAG, "Disconnected from GATT server.");
                }

            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                super.onServicesDiscovered(gatt, status);
                Log.v(TAG, "onServicesDiscovered");
                displayGattServices(gatt.getServices());
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                super.onCharacteristicRead(gatt, characteristic, status);
                Log.v(TAG, "onCharacteristicRead" + characteristic.getUuid() + " Value : " + characteristic.getValue());
                final byte[] data = characteristic.getValue();
                if (data != null && data.length > 0) {
                    final StringBuilder stringBuilder = new StringBuilder(data.length);
                    for(byte byteChar : data)
                        stringBuilder.append(String.format("%02X ", byteChar));

                    Log.v(TAG, "onCharacteristicRead" +stringBuilder.toString() + "Data : " + data );
                }
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                super.onCharacteristicWrite(gatt, characteristic, status);

                Log.v(TAG, "onCharacteristicWrite : " + characteristic.getUuid() + " Status : " + status);

                boolean statusCharAuthRead = mBluetoothGatt.readCharacteristic(mCharAuthentication);
                Log.v(TAG, "statusCharAuthRead Status : " + statusCharAuthRead);

                    boolean statusNotifiaction = mBluetoothGatt.setCharacteristicNotification(mCharAuthentication, true);
                    Log.v(TAG, "CharAuthentication Notification : " + statusNotifiaction);

                    if (mBluetoothDevice.getBondState() != BluetoothDevice.BOND_BONDED) {
//                        mBluetoothDevice.setPin(new byte[]{ 0x60, 0x00, 0x60, 0x00});
                        boolean statusCreateBound = mBluetoothDevice.createBond();
                        Log.v(TAG, "Create Bound Status: " + statusCreateBound);
                    } else {
                        mBluetoothGatt.discoverServices();
                        Log.v(TAG, "discover service again ");
                    }

            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                super.onCharacteristicChanged(gatt, characteristic);
                Log.v(TAG, "onCharacteristicChanged value : " + characteristic.getValue() );
                byte[] charValue = characteristic.getValue();
                byte flag = charValue[0];
                Log.i(TAG, "Characteristic: " + flag);
            }

            @Override
            public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                super.onDescriptorRead(gatt, descriptor, status);
                Log.v(TAG, "onDescriptorRead");

            }

            @Override
            public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                super.onDescriptorWrite(gatt, descriptor, status);
                Log.v(TAG, "onDescriptorWrite");

            }

            @Override
            public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
                super.onReliableWriteCompleted(gatt, status);
                Log.v(TAG, "onReliableWriteCompleted");

            }

            @Override
            public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
                super.onReadRemoteRssi(gatt, rssi, status);
                Log.v(TAG, "onReadRemoteRssi");

            }

            @Override
            public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
                super.onMtuChanged(gatt, mtu, status);
                Log.v(TAG, "onMtuChanged");

            }
        };
    }

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                }
            }, SCAN_PERIOD);

            mScanning = true;
            Log.v(TAG, "Bluetooth Scanning Start");
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            mScanning = false;
            Log.v(TAG, "Bluetooth Scanning Stop");
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
    }

    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;
        String unknownServiceString = "UnKnownServices";
        String unknownCharaString = "UnKnowCharactristic";


        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData =
                    new HashMap<String, String>();
            uuid = gattService.getUuid().toString();
            Log.v(TAG, "\nService : " + uuid);

            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas =
                    new ArrayList<BluetoothGattCharacteristic>();
            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic :
                    gattCharacteristics) {
                uuid = gattCharacteristic.getUuid().toString();
                Log.v(TAG, "Characteristic : " + uuid);

                if(uuid.equalsIgnoreCase("4D050081-766C-42C4-8944-42BC98FC2D09")) {
                    Log.v(TAG, "CharIdentify Initilize");
                    mCharIdentify = gattCharacteristic;

                    mCharIdentify.setValue(new byte[]{(byte) 0x01});
                    boolean status = mBluetoothGatt.writeCharacteristic(mCharIdentify);
                    Log.v(TAG, "Lock Identify : " + status);
                }

                if(uuid.equalsIgnoreCase("4D050082-766C-42C4-8944-42BC98FC2D09")) {
                    mCharAuthentication = gattCharacteristic;
                    Log.v(TAG, "Authentication char initialize");
                }

                if(uuid.equalsIgnoreCase("4D050017-766C-42C4-8944-42BC98FC2D09")) {
                    mCharCommandTx = gattCharacteristic;
                    Log.v(TAG, "CommandTx char initialize");
                }

            }

        }
    }

    private final BroadcastReceiver mPairReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                final int state        = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
                final int prevState    = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR);

                if (state == BluetoothDevice.BOND_BONDED && prevState == BluetoothDevice.BOND_BONDING) {
                    Log.v(TAG, "Device is Paired");
                    BluetoothGattCharacteristic requestCert = mCharCommandTx;// new BluetoothGattCharacteristic(UUID.fromString("4D050017-766C-42C4-8944-42BC98FC2D09"), 0 , 0);
                    requestCert.setValue(new byte[]{ 0x60, 0x00});
                    requestCert.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                    boolean statusCertificate = mBluetoothGatt.writeCharacteristic(requestCert);
                    Log.v(TAG, "Write commandCertificate into  4D050017 : " + statusCertificate);

                    boolean statusReadCharAuth = mBluetoothGatt.
                            readCharacteristic(mCharAuthentication);
                    Log.v(TAG, "Read Authentication characteristics status : " + statusReadCharAuth);

//                    byte[] commandChallenge = {(byte)61,(byte)00};
//                    mCharCommandTx.setValue(new byte[]{ 0x61, 0x00});
//                    mCharCommandTx.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
//                    boolean statusChallenge = mBluetoothGatt.writeCharacteristic(mCharCommandTx);
//                    Log.v(TAG, "Write commandChallenge into  4D050017 : " + statusChallenge);
//
//
//                    byte[] commandSignatureSend = {0x63,0x20 ,0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x08,0x09,0x0A,0x0B,0x0C,0x0D,0x0E,0x0F,0x10,0x11,0x21,0x31,0x41,0x51,0x61,0x71,(byte) 0x81,(byte)0x91,(byte)0xA1,(byte)0xB1,(byte)0xC1,(byte)0xD1,(byte)0xE1,(byte)0xF2,0x0};
//                    mCharCommandTx.setValue(commandSignatureSend);
//                    mCharCommandTx.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
//                    boolean statusSignatureSend = mBluetoothGatt.writeCharacteristic(mCharCommandTx);
//                    Log.v(TAG, "Write commandSignature into  4D050017 : " + statusSignatureSend);
//
//
//                    byte[] commandChallengeSend = {0x62,0x20 ,0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x08,0x09,0x0A,0x0B,0x0C,0x0D,0x0E,0x0F,0x10,0x11,0x21,0x31,0x41,0x51,0x61,0x71,(byte) 0x81,(byte)0x91,(byte)0xA1,(byte)0xB1,(byte)0xC1,(byte)0xD1,(byte)0xE1,(byte)0xF2,0x0};
//                    mCharCommandTx.setValue(commandChallengeSend);
//                    mCharCommandTx.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
//                    boolean statusChallengeSend = mBluetoothGatt.writeCharacteristic(mCharCommandTx);
//                    Log.v(TAG, "Write 6000 into  4D050017 : " + statusChallengeSend);


                } else if (state == BluetoothDevice.BOND_NONE && prevState == BluetoothDevice.BOND_BONDED){
                    Log.v(TAG, "Device is Un Paired");
                }

            }
        }
    };

    public static byte[] createByteArray(String str) {
        int length = str.length();
        byte[] bArr = new byte[(length / 2)];
        for (int i = 0; i < length; i += 2) {
            bArr[i / 2] = (byte) ((Character.digit(str.charAt(i), 16) << 4) + Character.digit(str.charAt(i + 1), 16));
        }
        return bArr;
    }

    public static String encode(byte[] bytes) {
        final int length = bytes.length;

        // | BigInteger constructor throws if it is given an empty array.
        if (length == 0) {
            return "00";
        }

        final int evenLength = (int)(2 * Math.ceil(length / 2.0));
        final String format = "%0" + evenLength + "x";
        final String result = String.format (format, new BigInteger(bytes));

        return result;
    }
}
