package com.galarzaa.androidthings.samples;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.galarzaa.androidthings.Rc522;
import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.PeripheralManager;
import com.google.android.things.pio.SpiDevice;

import java.io.IOException;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private Rc522 mRc522;
    RfidTask mRfidTask;
    private TextView mTagDetectedView;
    private TextView mTagUidView;
    private TextView mTagResultsView;
    private Button button;

    private SpiDevice spiDevice;
    private SpiDevice spiDevice2;

    private Gpio gpioReset;



    static final String TAG = MainActivity.class.getSimpleName();
    private static final String SPI_PORT = "SPI0.0";
    private static final String SPI_PORT2 = "SPI0.1";
    private static final String PIN_RESET = "BCM25";
    String resultsText = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTagDetectedView = (TextView)findViewById(R.id.tag_read);
        mTagUidView = (TextView)findViewById(R.id.tag_uid);
        mTagResultsView = (TextView) findViewById(R.id.tag_results);
        button = (Button)findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mRfidTask = new RfidTask(mRc522);
                mRfidTask.execute();
                ((Button)v).setText(R.string.reading);
            }
        });
        PeripheralManager pioService = PeripheralManager.getInstance();
        List<String> deviceList = pioService.getSpiBusList();
        List<String> deviceI2c = pioService.getI2cBusList();
        if (deviceList.isEmpty()) {
            Log.d(TAG, "No SPI bus available on thie device...");
        } else {
            Log.d(TAG, "List of available devices --->" + deviceList);
            Log.d(TAG ,"Available GPIO " + pioService.getGpioList());
            Log.d(TAG, "I2c Device -->" + deviceI2c);
        }
        try {
            spiDevice = pioService.openSpiDevice(SPI_PORT);
            spiDevice2 = pioService.openSpiDevice(SPI_PORT2);
            gpioReset = pioService.openGpio(PIN_RESET);
            //
            //Log.d(TAG, "spiDevice -->" + "--"+ spiDevice2.getName());
            //mRc522 = new Rc522(spiDevice, spiDevice2, gpioReset);
            //mRc522 = new Rc522(spiDevice, spiDevice2, gpioReset);

            mRc522 = new Rc522(spiDevice, gpioReset);


            //mRc522 = new Rc522(spiDevice, spiDevice2, gpioReset);
            mRc522.setDebugging(true);
        } catch (IOException e) {
            Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try{
            if(spiDevice != null){
                spiDevice.close();
            }

            if (spiDevice2 != null) {
                spiDevice2.close();
            }
            if(gpioReset != null){
                gpioReset.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class RfidTask extends AsyncTask<Object, Object, Boolean> {
        private static final String TAG = "RfidTask";
        private Rc522 rc522;

        RfidTask(Rc522 rc522){
            this.rc522 = rc522;
        }

        @Override
        protected void onPreExecute() {
            button.setEnabled(false);
            mTagResultsView.setVisibility(View.GONE);
            mTagDetectedView.setVisibility(View.GONE);
            mTagUidView.setVisibility(View.GONE);
            resultsText = "";
        }

        @Override
        protected Boolean doInBackground(Object... params) {
            rc522.stopCrypto();
            while(true){
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    return null;
                }
                //Check if a RFID tag has been found
                if(!rc522.request()){
                    Log.d(TAG, "request rcc....");
                    continue;
                }
                //Check for collision errors
                if(!rc522.antiCollisionDetect()){
                    Log.d(TAG, "antiCollision");
                    continue;
                }

                byte[] uuid = rc522.getUid();

                Log.d(TAG, "uuid -->" + rc522.selectTag(uuid));
                return rc522.selectTag(uuid);
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            //if(!success){
                Log.d(TAG, "fail execute");
                mTagResultsView.setText(R.string.unknown_error);
                //return;
            //}
            // Try to avoid doing any non RC522 operations until you're done communicating with it.
            byte address = Rc522.getBlockAddress(2,1);

            Log.d(TAG, "addRess -->" + address);
            // Mifare's card default key A and key B, the key may have been changed previously
            byte[] key = {(byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF};

            Log.d(TAG, "key -->" + key);
            // Each sector holds 16 bytes
            // Data that will be written to sector 2, block 1
            byte[] newData = {0x0F,0x0E,0x0D,0x0C,0x0B,0x0A,0x09,0x08,0x07,0x06,0x05,0x04,0x03,0x02,0x01,0x00};

            Log.d(TAG, "newData -->" + newData);
            // In this case, Rc522.AUTH_A or Rc522.AUTH_B can be used
            try {
                //We need to authenticate the card, each sector can have a different key
                boolean result = rc522.authenticateCard(Rc522.AUTH_A, address, key);
                if (!result) {
                    mTagResultsView.setText(R.string.authetication_error);
                    return;
                }
                result = rc522.writeBlock(address, newData);
                if(!result){
                    mTagResultsView.setText(R.string.write_error);
                    return;
                }
                resultsText += "Sector written successfully";
                byte[] buffer = new byte[16];

                Log.d(TAG, "buffer ->" + buffer);
                //Since we're still using the same block, we don't need to authenticate again
                result = rc522.readBlock(address, buffer);
                if(!result){
                    mTagResultsView.setText(R.string.read_error);
                    return;
                }
                resultsText += "\nSector read successfully: "+ Rc522.dataToHexString(buffer);

                Log.d(TAG, "resultsText-->" + resultsText);
                rc522.stopCrypto();
                mTagResultsView.setText(resultsText);
            }finally{
                button.setEnabled(true);
                button.setText(R.string.start);
                mTagUidView.setText(getString(R.string.tag_uid,rc522.getUidString()));
                mTagResultsView.setVisibility(View.VISIBLE);
                mTagDetectedView.setVisibility(View.VISIBLE);
                mTagUidView.setVisibility(View.VISIBLE);
            }
        }
    }
}
