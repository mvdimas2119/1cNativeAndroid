
/////////////////////////////////////////////////////////////////////////////////////////////////////
//
// Examples for the report "Making external components for 1C mobile platform for Android""
// at the conference INFOSTART 2018 EVENT EDUCATION https://event.infostart.ru/2018/
//
// Sample 1: Delay in code
// Sample 2: Getting device information
// Sample 3: Device blocking: receiving external event about changing of sceen
//
// Copyright: Igor Kisil 2018
//
/////////////////////////////////////////////////////////////////////////////////////////////////////

package ru.infostart.education3;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import ru.infostart.education3.device.Utility;
import ru.infostart.education3.device.VH73Device;


  public class LockState implements Runnable {

  static native void OnLockChanged(long pObject);
  static native void OnReadDataRFID(long pObject,String sValue,String eventName);
  static native void OnReadCellRFID(long pObject,String sValue);

  private long m_V8Object; // 1C application context
  private Activity m_Activity; // custom activity of 1C:Enterprise
  private BroadcastReceiver m_Receiver;
  BluetoothAdapter mBluetoothAdapter;
  VH73Device currentDevice;
  Map<String, Integer> epc2num = new ConcurrentHashMap<String, Integer>();

  // Create the Handler
  private Handler handlerInventory = new Handler();
  threadInventoryClass threadInventory;

  class threadInventoryClass implements Runnable
  {
    private boolean isActive;
    private int cellID = 1;
    String passwd = "00000000";

    public void desable()
    {
      isActive = false;
    }

    threadInventoryClass()
    {
      isActive = true;
    }

    @Override
    public void run() {

            try {

              while(isActive) {
                try {
                  currentDevice.listTagID(1, 0, 0);
                  byte[] ret = currentDevice.getCmdResultWithTimeout(300);

                  if (ret == null)
                  {
                    Thread.sleep(300);
                    OnReadDataRFID(m_V8Object, "null","ReadDataThread");
                    continue;
                  }

                  VH73Device.ListTagIDResult listTagIDResult = VH73Device
                          .parseListTagIDResult(ret);

                  ArrayList<byte[]> epcs = listTagIDResult.epcs;
                  for (byte[] bs : epcs) {
                    String string = Utility.bytes2HexString(bs);

                    if (string.length() > 0) {
                        OnReadDataRFID(m_V8Object, string,"ReadDataThread");
                    }
                  }
                  Thread.sleep(300);
                }
                catch (Exception e1)
                {

                }
              }
            }
            catch (Exception e)
            {
              OnReadDataRFID(m_V8Object, "1" + e.getMessage(),"ReadDataThread");
            }

          }
  }

  public LockState(Activity activity, long v8Object)
  {
    m_Activity = activity;
    m_V8Object = v8Object;
  }


  private void addEpc(VH73Device.ListTagIDResult list) {
    ArrayList<byte[]> epcs = list.epcs;
    for (byte[] bs : epcs) {
      String string = Utility.bytes2HexString(bs);
      if (epc2num.containsKey(string)) {
        epc2num.put(string, epc2num.get(string) + 1);
      } else {
        epc2num.put(string, 1);
      }
    }
  }

  @Override
  protected void finalize() throws Throwable {
    super.finalize();
    stopConnect();
  }

  public void run()
  {
    System.loadLibrary("ru_infostart_education3_1_196");
  }

  public void show()
  {
    m_Activity.runOnUiThread(this);
  }

  public void start()
  {
    if (m_Receiver==null)
    {
      m_Receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          switch (intent.getAction())
          {
            case Intent.ACTION_SCREEN_ON:
              OnLockChanged(m_V8Object);
              break;
          }
        }
      };

      IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
      m_Activity.registerReceiver(m_Receiver, filter);
    }
  }
  public String GetBluetoothDevices()
  {
      mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
      Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
      Map<String,String> retVal = new HashMap<>();
      for (BluetoothDevice blDev : pairedDevices)
    {
      retVal.put(blDev.getAddress().toString(),blDev.getName().toString());
    }

    XStream xStream = new XStream(new DomDriver());
    xStream.alias("map", java.util.Map.class);
    String xml = xStream.toXML(retVal);

      return xml;
  }
  private Boolean initBluetoothDevice()
  {
    mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
    if (pairedDevices.size() > 0) {
      for (BluetoothDevice device : pairedDevices) {
        int i = 0;
        if (device.getAddress().equals("88:1B:99:28:2C:C4"))
        {
          currentDevice =   new VH73Device(this, device);

        }
      }

    }
    if (currentDevice == null)
    {
      return false;
    }
    return true;
  }
  public void startConnect()
  {
    if (currentDevice != null)
    {
      if (currentDevice.connect())
      {
        //threadInventory.start();
      }
    }
    else
    {
      initBluetoothDevice();
      if (currentDevice != null)
      {
        if (currentDevice.connect())
        {
            //threadInventory.start();
        }
      }
    }
  }
  public void ReadStreamStart(int cellID) {
    if (currentDevice.isConnected())
    {
        threadInventory = new threadInventoryClass();
        threadInventory.cellID = cellID;
        new Thread(threadInventory,"threadInventory").start();
    }
  }
  public void ReadStreamStop() {
    if (currentDevice.isConnected())
    {
      //threadInventory.stop();
      if (threadInventory.isActive)
      {
        threadInventory.desable();
      }
    }
  }

public void stopConnect()
{
  if (currentDevice != null)
  {
    try {
      if (threadInventory.isActive) {
        threadInventory.desable();
      }
      currentDevice.disconnect();
    } catch (IOException e) {
      e.printStackTrace();
    }

  }
}
  public void readDataRFID(String epc, int cell)
  {
    epc = "012001200120";
    String passwd = "00000000";

    try {

      if (currentDevice != null)
      {
        if (currentDevice.isConnected())
        {

         currentDevice.ReadWordBlock(epc,cell,0,4,passwd);
         byte[] ret  = currentDevice.getCmdResultWithTimeout(3000);

         StringBuilder builder = new StringBuilder(Utility.bytes2HexString(ret));
          builder.delete(0, 6);
          builder.delete(builder.length()-2, builder.length());
          OnReadDataRFID(m_V8Object,builder.toString(),"ReadDataCell");

          }

        }
      }
    catch (Exception e)
    {
      OnReadDataRFID(m_V8Object,"Ошибка четения ячейки","ReadDataCell");
    }
  }
  public void writeDataRFID(String sData, int cellID)
  {
    String epc = "012001200120";
    String passwd = "00000000";

    if (currentDevice != null)
    {
      if (currentDevice.isConnected())
      {
        try {

                OnReadDataRFID(m_V8Object,sData,"MessageReturn");

              currentDevice.WriteWordBlock(epc,cellID,0,sData,passwd);

              byte[] ret = currentDevice.getCmdResultWithTimeout(3000);
              if (!VH73Device.checkSucc(ret)) {

                  OnReadDataRFID(m_V8Object,"Запись произведена успешно","MessageReturn");
              } else {
              }
        }
        catch (Exception e)
        {
            OnReadDataRFID(m_V8Object,"Ошибка записи","MessageReturn");
        }
      }
    }

  }

  public void stop()
  {
    if (m_Receiver != null)
    {
      m_Activity.unregisterReceiver(m_Receiver);
      m_Receiver = null;
    }
  }
}
