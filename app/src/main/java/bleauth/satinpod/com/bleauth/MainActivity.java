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
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
private final static String TAG = "BLE";
    private BluetoothAdapter mBluetoothAdapter;
    BluetoothDevice mBluetoothDevice;
    private Handler mHandler;

    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 50000;
    private BluetoothAdapter.LeScanCallback mLeScanCallback;

    private BluetoothGatt mBluetoothGatt;

    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    private BluetoothGattCallback mGattCallback ;

    private BluetoothGattCharacteristic mCharIdentify;
    private BluetoothGattCharacteristic mCharAuthentication;
    private BluetoothGattCharacteristic mCharCommandTx;

    Button mSendCommand;
    Button mSendCommandUnLock;

    EditText mCommand;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mCommand = findViewById(R.id.command);
        mSendCommand = findViewById(R.id.send_command);
        mSendCommandUnLock = findViewById(R.id.send_command_unlock);

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

        mSendCommand.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCharCommandTx.setValue(new byte[]{(byte) 0x03, (byte)0x01,  (byte)0x03});
                mCharCommandTx.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                boolean statusLock = mBluetoothGatt.writeCharacteristic(mCharCommandTx);
                Log.v(TAG, "Write Lock command  into  4D050017 : " + statusLock);
            }
        });

        mSendCommandUnLock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String command = mCommand.getText().toString();
                byte[] commandInByte = createByteArray(command);
                mCharCommandTx.setValue(new byte[]{(byte) 0x03, (byte)0x01,  (byte)0x03});
                mCharCommandTx.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                boolean statusUnLock = mBluetoothGatt.writeCharacteristic(mCharCommandTx);
                Log.v(TAG, "Write unlock Command  into  4D050017 : " + statusUnLock);
            }
        });

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
                    Log.i(TAG, "Connected to GATT server.");
                    Log.i(TAG, "Attempting to start service discovery:" +
                            mBluetoothGatt.discoverServices());
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {

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
                broadcastUpdate(characteristic);
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

                if (status == BluetoothGatt.GATT_SUCCESS)
                {
                    Log.e("WRITE SUCCESS", "onCharacteristicWrite() - status: " + status + "  - UUID: " + characteristic.getUuid());

                if(characteristic.getUuid().toString().equalsIgnoreCase("4D050081-766C-42C4-8944-42BC98FC2D09")) {
                    BluetoothGattDescriptor descriptor = mCharAuthentication.getDescriptor(
                            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                    mBluetoothGatt.writeDescriptor(descriptor);
                    Log.e(TAG, "Indication Enable for : " + characteristic.getUuid());
                }

                } else {

                }
                    if (mBluetoothDevice.getBondState() != BluetoothDevice.BOND_BONDED) {
//                        mBluetoothDevice.setPin(new byte[]{ 0x60, 0x00, 0x60, 0x00});
                        boolean statusCreateBound = mBluetoothDevice.createBond();
                        Log.v(TAG, "Create Bound Status: " + statusCreateBound);
                    }

            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                super.onCharacteristicChanged(gatt, characteristic);
                Log.v(TAG, "onCharacteristicChanged value : " + characteristic.getValue() );
                broadcastUpdate(characteristic);
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
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                }
            }, SCAN_PERIOD);

            Log.v(TAG, "Bluetooth Scanning Start");
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            Log.v(TAG, "Bluetooth Scanning Stop");
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
    }

    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;
        for (BluetoothGattService gattService : gattServices) {
            uuid = gattService.getUuid().toString();
            Log.v(TAG, "\nService : " + uuid);

            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic :
                    gattCharacteristics) {
                uuid = gattCharacteristic.getUuid().toString();
                Log.v(TAG, "Characteristic : " + uuid);
                mBluetoothGatt.setCharacteristicNotification(gattCharacteristic, true);

                if(uuid.equalsIgnoreCase("4D050081-766C-42C4-8944-42BC98FC2D09")) {
                    Log.v(TAG, "CharIdentify Initilize");
                    int writeType = mCharCommandTx.getPermissions();
                    Log.v(TAG, "Write Type : " + writeType);

                    mCharIdentify = gattCharacteristic;

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
        mCharIdentify.setValue(new byte[]{(byte) 0x01});
        boolean status = mBluetoothGatt.writeCharacteristic(mCharIdentify);
        Log.v(TAG, "Lock Identify : " + status);

        mBluetoothGatt.setCharacteristicNotification(mCharAuthentication, true);
    }

    private final BroadcastReceiver mPairReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                final int state        = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
                final int prevState    = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR);

                if (state == BluetoothDevice.BOND_BONDED && prevState == BluetoothDevice.BOND_BONDING) {
                    Log.v(TAG, "Device is Paired");
                    mCharCommandTx.setValue(new byte[]{(byte) 0x60, (byte)0x00});
                    mCharCommandTx.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                    boolean status60 = mBluetoothGatt.writeCharacteristic(mCharCommandTx);
                    Log.v(TAG, "Write 6000 commandCertificate into  4D050017 : " + status60);

                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mCharCommandTx.setValue(new byte[]{(byte) 0x61, (byte)0x00});
                            mCharCommandTx.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                            boolean status61 = mBluetoothGatt.writeCharacteristic(mCharCommandTx);
                            Log.v(TAG, "Write 0x61 commandCertificate into  4D050017 : " + status61);
                        }
                    }, 5000);


                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            byte[] commandSignatureSend = {0x63, 0x20, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x11, 0x21, 0x31, 0x41, 0x51, 0x61, 0x71, (byte) 0x81, (byte) 0x91, (byte) 0xA1, (byte) 0xB1, (byte) 0xC1, (byte) 0xD1, (byte) 0xE1, (byte) 0xF2, 0x0};
                            mCharCommandTx.setValue(commandSignatureSend);
                            mCharCommandTx.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                            boolean status63 = mBluetoothGatt.writeCharacteristic(mCharCommandTx);
                            Log.v(TAG, "Write 0x63 commandSignature into  4D050017 : " + status63);
                        }
                    }, 10000);


                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            byte[] commandSignatureSend = {0x62, 0x20, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x11, 0x21, 0x31, 0x41, 0x51, 0x61, 0x71, (byte) 0x81, (byte) 0x91, (byte) 0xA1, (byte) 0xB1, (byte) 0xC1, (byte) 0xD1, (byte) 0xE1, (byte) 0xF2, 0x0};
                            mCharCommandTx.setValue(commandSignatureSend);
                            mCharCommandTx.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                            boolean status62 = mBluetoothGatt.writeCharacteristic(mCharCommandTx);
                            Log.v(TAG, "Write 0x62 commandSignature into  4D050017 : " + status62);
                        }
                    }, 17000);

                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mCharCommandTx.setValue(new byte[]{(byte) 0x66, (byte)0x00});
                            mCharCommandTx.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                            boolean status61 = mBluetoothGatt.writeCharacteristic(mCharCommandTx);
                            Log.v(TAG, "Write 0x66 commandCertificate into  4D050017 : " + status61);
                        }
                    }, 23000);


                } else if (state == BluetoothDevice.BOND_NONE && prevState == BluetoothDevice.BOND_BONDED){
                    Log.v(TAG, "Device is Un Paired");
                }

            }
        }
    };

    private void broadcastUpdate(final BluetoothGattCharacteristic characteristic) {
        // Writes the data formatted in HEX.
        final byte[] data = characteristic.getValue();

        if (data != null && data.length > 0) {
            Log.v(TAG, "Raw Data : " + data.toString());
            final StringBuilder stringBuilder = new StringBuilder(data.length);
            for(byte byteChar : data)
                stringBuilder.append(String.format("%02X ", byteChar));
            Log.v(TAG, "Data in String : " + stringBuilder.toString());
        }
    }

    public static byte[] createByteArray(String str) {
        int length = str.length();
        byte[] bArr = new byte[(length / 2)];
        for (int i = 0; i < length; i += 2) {
            bArr[i / 2] = (byte) ((Character.digit(str.charAt(i), 16) << 4) + Character.digit(str.charAt(i + 1), 16));
        }
        return bArr;
    }
}
