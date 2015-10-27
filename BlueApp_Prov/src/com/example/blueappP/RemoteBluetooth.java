package com.example.blueappP;

import com.example.blueappP.R;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

@SuppressLint("CommitPrefEdits")
public class RemoteBluetooth extends Activity {
	
	private static final String TAG = "Bluetooth";
    private static final boolean D = true;
    
    //Message types sent from the BluetoothChatService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;
    
    // Key names received from the BluetoothCommandService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";
    
    //Intent request codes
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    	
    
	// Layout view  
    private EditText msaldoEditText;
    private EditText mOutEditText;
    private EditText mTitle;
    public float acum = 0;
    Dialog dialogo = null;
    private Button Enviar;
    private Button buscar;
    private Button Des;
    
	// Name of the connected device
    private String mConnectedDeviceName = null;
    // Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;
    // Member object for Bluetooth Command Service
    private BluetoothCommandService mCommandService = null;
    private StringBuffer mOutStringBuffer;
    
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.app_tienda);
        buscar = (Button)findViewById(R.id.button1);
        Des = (Button)findViewById(R.id.button4);
        
        mTitle = (EditText) findViewById(R.id.editText2);
        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        
        buscar.setOnClickListener(new View.OnClickListener() {
        	public void onClick(View view) {
        		Intent i = new Intent(RemoteBluetooth.this,DeviceListActivity.class);
        		startActivityForResult(i, REQUEST_CONNECT_DEVICE);
        	}
        });
        
        Des.setOnClickListener(new View.OnClickListener() {
        	public void onClick(View view) {
        		onDestroy();
        	}
        });
        
                
    	
    }

	@Override
	protected void onStart() {
		super.onStart();
		
		// If BT is not on, request that it be enabled.
        // setupCommand() will then be called during onActivityResult
		if (!mBluetoothAdapter.isEnabled()) {
			Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
		}
		// otherwise set up the command service
		else {
			if (mCommandService==null)
				setupCommand();
		}
	}
	
	private TextView.OnEditorActionListener mWriteListener =
			   new TextView.OnEditorActionListener() {
			       public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
				            // If the action is a key-up event on the return key, send the message
				            if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_UP) {
				                String message = view.getText().toString();
				                sendMessage(message);
				            }
				            if(D) Log.i(TAG, "END onEditorAction");
				            return true;
				        }
				    };
	
	
	@Override
	protected void onResume() {
		super.onResume();
		
		// Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
		if (mCommandService != null) {
			if (mCommandService.getState() == BluetoothCommandService.STATE_NONE) {
				mCommandService.start();
			}
		}
	}
	
	
	
	private void setupCommand() {
		SharedPreferences preferencias=getSharedPreferences("canje",Context.MODE_PRIVATE);
		final Editor editor=preferencias.edit();
		
		msaldoEditText = (EditText) findViewById(R.id.editText1);
        msaldoEditText.setText(preferencias.getString("total","0.00"));  
        
        mOutEditText = (EditText) findViewById(R.id.editText1);
        mOutEditText.setOnEditorActionListener(mWriteListener);

        Enviar = (Button)findViewById(R.id.button3);
        Enviar.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                // Send a message using content of the edit text widget
            	TextView view = (TextView) findViewById(R.id.editText1);
                String message = view.getText().toString();
                Float m = Float.parseFloat(mOutEditText.getText().toString());
                if (m>0.00){
                		sendMessage(message);
                		editor.putString("total", "0.00");
                		mOutEditText.setText("0.00");
                		editor.commit();
                		enviado();
                }else {
                	Noenviado();
                }
         }
            
            });

        
	    //Initialize the BluetoothChatService to perform bluetooth connections
	    mCommandService = new BluetoothCommandService(this, mHandler);
	    
	    // Initialize the buffer for outgoing messages
        mOutStringBuffer = new StringBuffer("");
      
    }
	
	@Override
    public synchronized void onPause() {
        super.onPause();
        if(D) Log.e(TAG, "- ON PAUSE -");
    }

    @Override
    public void onStop() {
        super.onStop();
        if(D) Log.e(TAG, "-- ON STOP --");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Stop the Bluetooth chat services
        if (mCommandService != null) mCommandService.stop();
        if(D) Log.e(TAG, "--- ON DESTROY ---");
    }
    
    private void sendMessage(String message) {
        // Check that we're actually connected before trying anything
        if (mCommandService.getState() != BluetoothCommandService.STATE_CONNECTED) {
            Toast.makeText(this, "No conectado", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check that there's actually something to send
        if (message.length() > 0) {
           
            mCommandService.write(message);
           // Toast.makeText(this, "Enviado", Toast.LENGTH_SHORT).show();

            // Reset out string buffer to zero and clear the edit text field
            mOutStringBuffer.setLength(0);
            mOutEditText.setText(mOutStringBuffer);
        }
    }  
       
    private Dialog crearDialogoConfirmacion(final String monto){
    	 AlertDialog.Builder dialogo1 = new AlertDialog.Builder(this);  
         dialogo1.setTitle("Confirmacion");  
         dialogo1.setMessage("¿ Acepta recibir "+monto+"Bs de pago ?");            
         dialogo1.setCancelable(false);  
         dialogo1.setPositiveButton("Si", new DialogInterface.OnClickListener() {  
             public void onClick(DialogInterface dialogo1, int id) {  
            	 procesa_monto(monto);  
            	 aceptado();
             }  
             
         });  
         dialogo1.setNegativeButton("No", new DialogInterface.OnClickListener() {  
             public void onClick(DialogInterface dialogo1, int id) {  
            	 rechazar();
             }  
         });            
         //dialogo1.show();
         return dialogo1.show();
     }
    
    public void rechazar() {
        Toast t=Toast.makeText(this,"Pago no recibido", Toast.LENGTH_SHORT);
        t.show();
    }
    
    public void aceptado() {
        Toast t=Toast.makeText(this,"Pago recibido", Toast.LENGTH_SHORT);
        t.show();
    }
    
    public void enviado() {
        Toast t=Toast.makeText(this,"Monto enviado", Toast.LENGTH_SHORT);
        t.show();
    }
    
    public void Noenviado() {
        Toast t=Toast.makeText(this,"Monto no enviado", Toast.LENGTH_SHORT);
        t.show();
    }
    
    
    public void procesa_monto(String monto){
    	SharedPreferences preferencias=getSharedPreferences("canje",Context.MODE_PRIVATE);
    	Editor editor=preferencias.edit();
    	acum= Float.parseFloat(msaldoEditText.getText().toString())+Float.parseFloat(monto);
    	msaldoEditText.setText(String.valueOf(acum));
    	editor.putString("total", String.valueOf(acum));
        editor.commit();
    }
	
	// The Handler that gets information back from the BluetoothChatService
    private final Handler mHandler = new Handler() {

    	@Override
        
        public void handleMessage(Message msg) {
    		switch (msg.what) {
            case MESSAGE_STATE_CHANGE:
                switch (msg.arg1) {
                case BluetoothCommandService.STATE_CONNECTED:
                    mTitle.setText(mConnectedDeviceName);                    
                    break;
                case BluetoothCommandService.STATE_CONNECTING:
                    mTitle.setText(R.string.title_connecting);
                    break;
                case BluetoothCommandService.STATE_LISTEN:
                case BluetoothCommandService.STATE_NONE:
                    mTitle.setText(R.string.title_not_connected);
                    break;
                }
                break;
            case MESSAGE_WRITE:
            	break;
                case MESSAGE_READ:
	                byte[] readBuf = (byte[]) msg.obj;
	                // construct a string from the valid bytes in the buffer
	                
	                String monto = new String(readBuf, 0, msg.arg1);
	                dialogo=crearDialogoConfirmacion(monto);
                break;
            case MESSAGE_DEVICE_NAME:
                // save the connected device's name
                mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                Toast.makeText(getApplicationContext(), " "
                               + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                break;
            case MESSAGE_TOAST:
                Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
                               Toast.LENGTH_SHORT).show();
                break;
            }
        }
    };
    
    @Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
        case REQUEST_CONNECT_DEVICE:
            // When DeviceListActivity returns with a device to connect
            if (resultCode == Activity.RESULT_OK) {
                // Get the device MAC address
                String address = data.getExtras()
                                     .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                // Get the BLuetoothDevice object
                BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
                // Attempt to connect to the device
                mCommandService.connect(device);
            }
            break;
        case REQUEST_ENABLE_BT:
            // When the request to enable Bluetooth returns
            if (resultCode == Activity.RESULT_OK) {
                // Bluetooth is now enabled, so set up a chat session
                setupCommand();
            } else {
                // User did not enable Bluetooth or an error occured
                Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
                finish();
            }
        }
        
    }
}