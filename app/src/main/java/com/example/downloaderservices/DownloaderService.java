package com.example.downloaderservices;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;


public class DownloaderService extends Service {
    private Looper serviceLooper;
    private ServiceHandler serviceHandler;
    private static final String CHANNEL_ID = "NOTIF_C_1";
    private int notif_A_Id;
    private NotificationManagerCompat notificationManager;
    private NotificationCompat.Builder builder;
    private String filePath = "";

    // Handler that receives messages from the thread
    private final class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            // Normally we would do some work here, like download a file.
            URL url_dwl = (URL) msg.obj;
            String reply = "";

            int fileLength = 0;
            long total = 0;

            InputStream input = null;
            OutputStream output = null;
            HttpURLConnection connection = null;

            try {
                connection = (HttpURLConnection) url_dwl.openConnection();
                connection.connect();

                // expect HTTP 200 OK, so we don't mistakenly save error report
                // instead of the file
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    builder.setContentTitle("Connection Error");
                    builder.setContentText("Unfortunately Connection is not established");
                    //update the notification
                    notificationManager.notify(notif_A_Id, builder.build());
                    Thread.currentThread().interrupt();
                }

                // this will be useful to display download percentage
                // might be -1: server did not report the length
                fileLength = connection.getContentLength();
                String link = url_dwl.toString();
                String f_name = link.substring(link.lastIndexOf("/") + 1, link.length());
                reply = "";
                reply += "File Name: " + f_name + "\nFile Length: " + fileLength / 1048576 + "Mb";
                builder.setContentText("File Description: "+reply);
                //show the notification
                notificationManager.notify(notif_A_Id, builder.build());

                // download the file
                input = connection.getInputStream();
                String path = "";
                //checking is an external storage is available
                if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
                    //getting the path of external downloads directory
                    path = getApplicationContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).getPath();

                    filePath = path + "/" + f_name;
                    output = new FileOutputStream(filePath);

                    byte data[] = new byte[4096];
                    total = 0;
                    int count;
                    //reading the file from internet and writing it to the path
                    while ((count = input.read(data)) != -1) {
                        // allow canceling with back button
                        total += count;
                        // publishing the progress....
                        if (fileLength > 0) {// only if total length is known
                            //setting the progress of progress bar
                            builder.setProgress(100, (int) (total * 100 / fileLength), false);
                            //update the notification
                            notificationManager.notify(notif_A_Id, builder.build());
                        }
                        output.write(data, 0, count);
                    }

                } else {
                    builder.setContentTitle("Media Error");
                    builder.setContentText("Media Not Present");
                    //update the notification
                    notificationManager.notify(notif_A_Id, builder.build());
                }

            } catch (final Exception e) {
                builder.setContentTitle("Some Exception");
                builder.setContentText(e.getMessage());
                //show the notification
                notificationManager.notify(notif_A_Id, builder.build());
            } finally {
                try {
                    if (output != null)
                        output.close();
                    if (input != null)
                        input.close();
                } catch (IOException ignored) {
                }

                if (connection != null) {
                    connection.disconnect();
                }
            }
            // After the download completes create a PendingIntent and and notify the user, and enable tap action
            //getting Uri path from FileProvider of this app
            Uri path = FileProvider.getUriForFile(getApplicationContext(), BuildConfig.APPLICATION_ID + ".provider", new File(filePath));
            //Creating intent for action ACTION_VIEW
            Intent intent = new Intent(Intent.ACTION_VIEW);
            //Setting the data for intent i.e. the file path
            intent.setData(path);
            //To grant permission to other apps for reading this apps provided files
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            //If no default program is set to open the file open the chooser dialog
            Intent chooser = Intent.createChooser(intent, "Choose an app to open with: ");
            //creating a pending intent
            final PendingIntent tapPendingIntent = PendingIntent.getActivity(DownloaderService.this, 0, chooser, PendingIntent.FLAG_UPDATE_CURRENT);

            builder.setContentTitle("File downloaded");
            builder.setContentIntent(tapPendingIntent);
            builder.setOnlyAlertOnce(false);
            builder.setAutoCancel(true);
            // notificationId is a unique int for each notification that you must define
            //show the notification
            notificationManager.notify(notif_A_Id, builder.build());

            // Stop the service using the startId, so that we don't stop
            // the service in the middle of handling another job
            stopSelf(msg.arg1);
        }
    }

    public DownloaderService() {
    }

    @Override
    public void onCreate() {
        //Creating an initial notification for the file download service
        createNotificationChannel();
        notif_A_Id = 101;
        notificationManager = NotificationManagerCompat.from(this);
        builder = new NotificationCompat.Builder(this, CHANNEL_ID);
        builder.setSmallIcon(R.drawable.ic_notification);
        builder.setContentTitle("File Download");
        builder.setContentText("File Description: \n");
        builder.setPriority(NotificationCompat.PRIORITY_DEFAULT);
        builder.setOnlyAlertOnce(true);
        builder.setStyle(new NotificationCompat.BigTextStyle());
        builder.setProgress(100,0,false);
        // notificationId is a unique int for each notification that you must define
        //show the notification
        notificationManager.notify(notif_A_Id, builder.build());

        // Start up the thread running the service. Note that we create a
        // separate thread because the service normally runs in the process's
        // main thread, which we don't want to block. We also make it
        // background priority so CPU-intensive work doesn't disrupt our UI.
        // HandlerThread class is a subclass of Thread class
        HandlerThread thread = new HandlerThread("ServiceStartArguments", Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();

        // Get the HandlerThread's Looper and use it for our Handler
        serviceLooper = thread.getLooper();
        serviceHandler = new ServiceHandler(serviceLooper);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String url_st = intent.getStringExtra("URL");
        URL url_dwl;
        try {
            url_dwl = new URL(url_st);
            // For each start request, send a message to start a job and deliver the
            // url to start downloading from internet and
            // start ID so we know which request we're stopping when we finish the job
            Message msg = serviceHandler.obtainMessage();
            msg.obj = url_dwl;
            msg.arg1 = startId;
            serviceHandler.sendMessage(msg);
        } catch (MalformedURLException e) {
            Toast.makeText(this, "URL not valid", Toast.LENGTH_SHORT).show();
        }

        // If we get killed, after returning from here, restart
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // We don't provide binding, so return null
        return null;
    }

    @Override
    public void onDestroy() {
        Toast.makeText(this, "Service done", Toast.LENGTH_SHORT).show();
    }

    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.channel_name);
            String description = getString(R.string.channel_description);
            //will be used instead of priority
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
}
