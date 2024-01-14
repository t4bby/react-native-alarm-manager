package com.baekgol.reactnativealarmmanager;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.icu.util.Calendar;
import android.os.Build;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;

import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.bridge.ReadableMap;

import java.text.SimpleDateFormat;
import java.text.DateFormat;

import java.sql.Time;
import java.time.LocalDate;

import com.baekgol.reactnativealarmmanager.db.Database;
import com.baekgol.reactnativealarmmanager.model.AlarmDto;
import com.baekgol.reactnativealarmmanager.util.AlarmReceiver;
import com.baekgol.reactnativealarmmanager.util.AlarmService;

public class AlarmModule extends ReactContextBaseJavaModule {
  private ReactApplicationContext reactContext;
  private NotificationManager notificationManager;
  private AlarmManager alarmManager;
  private AlarmDto alarm;
  private AlarmDto[] alarms;
  private int affectedCnt;
  private final String channelId = "alarm";

  AlarmModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
    alarmManager = (AlarmManager) reactContext.getSystemService(Context.ALARM_SERVICE);
    createNotificationChannel();
  }

  @Override
  public String getName() {
    return "Alarm";
  }

  private void createNotificationChannel(){
    if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){
      notificationManager = reactContext.getSystemService(NotificationManager.class);

      NotificationChannel channel = new NotificationChannel(channelId, "Alarm", NotificationManager.IMPORTANCE_HIGH);
      channel.setDescription("Alarm");
      channel.setSound(null, null);
      notificationManager.createNotificationChannel(channel);
    }
  }

  @ReactMethod
  public void schedule(final ReadableMap rm, Callback success, Callback fail){
    Runnable r = new Runnable() {
      @Override
      public void run() {
        Database db = Database.getInstance(getReactApplicationContext());
        AlarmDto newAlarm = createAlarm(rm, false);

          long alarmId = db.alarmDao().add(newAlarm);
          alarm = db.alarmDao().search((int)alarmId);

          String time = alarm.getAlarmTime().toString();
          String[] timeInfo = time.split(":");
          int hour = Integer.parseInt(timeInfo[0]);
          int minute = Integer.parseInt(timeInfo[1]);
          int second = Integer.parseInt(timeInfo[2]);
          // new
          Calendar currentCalendar = Calendar.getInstance();
          currentCalendar.setTimeInMillis(System.currentTimeMillis());

          String alarmText = alarm.getAlarmText();
          String[] alarmTextInfo = alarmText.split(" ");
          LocalDate alarmDate = LocalDate.parse(alarmTextInfo[3]);

          Calendar alarmCalender = Calendar.getInstance();
          alarmCalender.set(Calendar.YEAR, alarmDate.getYear());
          alarmCalender.set(Calendar.MONTH, alarmDate.getMonthValue() - 1);
          alarmCalender.set(Calendar.DAY_OF_MONTH, alarmDate.getDayOfMonth());
          alarmCalender.set(Calendar.HOUR_OF_DAY, hour);
          alarmCalender.set(Calendar.MINUTE, minute);
          alarmCalender.set(Calendar.SECOND, 0);

          long timeDifferenceMillis = alarmCalender.getTimeInMillis() - currentCalendar.getTimeInMillis();
          alarmCalender.setTimeInMillis(currentCalendar.getTimeInMillis() + timeDifferenceMillis);

          Intent alarmIntent = new Intent(reactContext, AlarmReceiver.class);
          alarmIntent.putExtra("id", alarm.getAlarmId());
          alarmIntent.putExtra("hour", hour);
          alarmIntent.putExtra("minute", minute);
          alarmIntent.putExtra("title", alarm.getAlarmTitle());
          alarmIntent.putExtra("text", alarm.getAlarmText());
          alarmIntent.putExtra("sound", alarm.getAlarmSound());
          alarmIntent.putExtra("icon", alarm.getAlarmIcon());
          alarmIntent.putExtra("soundLoop", alarm.isAlarmSoundLoop());
          alarmIntent.putExtra("vibration", alarm.isAlarmVibration());
          alarmIntent.putExtra("notiRemovable", alarm.isAlarmNotiRemovable());

          PendingIntent alarmPendingIntent = PendingIntent.getBroadcast(reactContext, alarm.getAlarmId(), alarmIntent, PendingIntent.FLAG_CANCEL_CURRENT);
          alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmCalender.getTimeInMillis(), alarmPendingIntent);
      }
    };

    Thread thread = new Thread(r);
    thread.start();

    try{
      thread.join();
      if(alarm!=null) success.invoke("Alarm scheduling was successful.");
      else fail.invoke("An alarm set for this time already exists.");
    } catch (InterruptedException e) {
      e.printStackTrace();
      fail.invoke("An error occurred while scheduling the alarm.");
    }finally{
      alarm = null;
    }
  }

  @ReactMethod
  public void search(final int id, Callback success, Callback fail){
    Runnable r = new Runnable() {
      @Override
      public void run() {
        Database db = Database.getInstance(getReactApplicationContext());
        alarm = db.alarmDao().search(id);
      }
    };

    Thread thread = new Thread(r);
    thread.start();

    try{
      thread.join();
      if(alarm!=null) success.invoke(createMap(alarm));
      else fail.invoke("An error occurred while searching the alarm.");
    } catch (InterruptedException e) {
      e.printStackTrace();
      fail.invoke("An error occurred while searching the alarm.");
    }finally{
      alarm = null;
    }
  }

  @ReactMethod
  public void searchAll(Callback success, Callback fail){
    Runnable r = new Runnable() {
      @Override
      public void run() {
        Database db = Database.getInstance(getReactApplicationContext());
        alarms = db.alarmDao().searchAll();
      }
    };

    Thread thread = new Thread(r);
    thread.start();

    try{
      thread.join();
      success.invoke(createArray(alarms));
    } catch (InterruptedException e) {
      e.printStackTrace();
      fail.invoke("An error occurred while searching alarms.");
    }finally{
      alarms = null;
    }
  }

  @ReactMethod
  public void modify(final ReadableMap rm, Callback success, Callback fail){
    Runnable r = new Runnable(){
      @Override
      public void run(){
        Database db = Database.getInstance(getReactApplicationContext());
        AlarmDto newAlarm = createAlarm(rm, true);
        AlarmDto existedAlarm = db.alarmDao().search(newAlarm.getAlarmId());
        PendingIntent alarmPendingIntent = PendingIntent.getBroadcast(
                reactContext,
                newAlarm.getAlarmId(),
                new Intent(reactContext, AlarmReceiver.class),
                PendingIntent.FLAG_NO_CREATE);

        if(existedAlarm!=null && newAlarm.getAlarmId()==existedAlarm.getAlarmId()){
          if(alarmPendingIntent!=null){
            alarmPendingIntent.cancel();
            alarmManager.cancel(alarmPendingIntent);
          }

          affectedCnt = db.alarmDao().modifyAlarm(newAlarm);

          if(newAlarm.isAlarmActivate()){
            String time = newAlarm.getAlarmTime().toString();
            String[] timeInfo = time.split(":");
            int hour = Integer.parseInt(timeInfo[0]);
            int minute = Integer.parseInt(timeInfo[1]);
            int second = Integer.parseInt(timeInfo[2]);
            // new
            Calendar currentCalendar = Calendar.getInstance();
            currentCalendar.setTimeInMillis(System.currentTimeMillis());

            String alarmText = newAlarm.getAlarmText();
            String[] alarmTextInfo = alarmText.split(" ");
            LocalDate alarmDate = LocalDate.parse(alarmTextInfo[3]);

            Calendar alarmCalender = Calendar.getInstance();
            alarmCalender.set(Calendar.YEAR, alarmDate.getYear());
            alarmCalender.set(Calendar.MONTH, alarmDate.getMonthValue() - 1);
            alarmCalender.set(Calendar.DAY_OF_MONTH, alarmDate.getDayOfMonth());
            alarmCalender.set(Calendar.HOUR_OF_DAY, hour);
            alarmCalender.set(Calendar.MINUTE, minute);
            alarmCalender.set(Calendar.SECOND, 0);

            long timeDifferenceMillis = alarmCalender.getTimeInMillis() - currentCalendar.getTimeInMillis();
            alarmCalender.setTimeInMillis(currentCalendar.getTimeInMillis() + timeDifferenceMillis);

            Intent alarmIntent = new Intent(reactContext, AlarmReceiver.class);
            alarmIntent.putExtra("id", newAlarm.getAlarmId());
            alarmIntent.putExtra("hour", hour);
            alarmIntent.putExtra("minute", minute);
            alarmIntent.putExtra("title", newAlarm.getAlarmTitle());
            alarmIntent.putExtra("text", newAlarm.getAlarmText());
            alarmIntent.putExtra("sound", newAlarm.getAlarmSound());
            alarmIntent.putExtra("icon", newAlarm.getAlarmIcon());
            alarmIntent.putExtra("soundLoop", newAlarm.isAlarmSoundLoop());
            alarmIntent.putExtra("vibration", newAlarm.isAlarmVibration());
            alarmIntent.putExtra("notiRemovable", newAlarm.isAlarmNotiRemovable());

            alarmPendingIntent = PendingIntent.getBroadcast(reactContext, newAlarm.getAlarmId(), alarmIntent, PendingIntent.FLAG_CANCEL_CURRENT);
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmCalender.getTimeInMillis(), alarmPendingIntent);
          }
        }
      }
    };

    Thread thread = new Thread(r);
    thread.start();

    try{
      thread.join();
      if(affectedCnt!=0) success.invoke("Alarm modification was successful.");
      else fail.invoke("An error occurred while modifying the alarm.");
    } catch (InterruptedException e) {
      e.printStackTrace();
      fail.invoke("An error occurred while modifying the alarm.");
    }finally{
      affectedCnt = 0;
    }
  }

  @ReactMethod
  public void delete(final int id, Callback success, Callback fail){
    Runnable r = new Runnable(){
      @Override
      public void run(){
        Database db = Database.getInstance(getReactApplicationContext());
        PendingIntent alarmPendingIntent = PendingIntent.getBroadcast(
                reactContext,
                id,
                new Intent(reactContext, AlarmReceiver.class),
                PendingIntent.FLAG_NO_CREATE);

        if(alarmPendingIntent!=null) {
          alarmPendingIntent.cancel();
          alarmManager.cancel(alarmPendingIntent);
        }

        affectedCnt = db.alarmDao().deleteAlarm(id);
      }
    };

    Thread thread = new Thread(r);
    thread.start();

    try{
      thread.join();
      if(affectedCnt!=0) success.invoke("Alarm deleting was successful.");
      else fail.invoke("An error occurred while deleting the alarm.");
    } catch (InterruptedException e) {
      e.printStackTrace();
      fail.invoke("An error occurred while deleting the alarm.");
    }finally{
      affectedCnt = 0;
    }
  }

  @ReactMethod
  public void stop(Callback success, Callback fail){
    Runnable r = new Runnable(){
      @Override
      public void run(){
        Intent alarmServiceIntent = new Intent(reactContext, AlarmService.class);
        reactContext.stopService(alarmServiceIntent);
      }
    };

    Thread thread = new Thread(r);
    thread.start();

    try{
      thread.join();
      success.invoke("Alarm stopping was successful.");
    } catch (InterruptedException e) {
      e.printStackTrace();
      fail.invoke("An error occurred while stopping the alarm.");
    }
  }

  public static void stop(final Context context){
    Runnable r = new Runnable(){
      @Override
      public void run(){
        Intent alarmServiceIntent = new Intent(context, AlarmService.class);
        context.stopService(alarmServiceIntent);
      }
    };

    Thread thread = new Thread(r);
    thread.start();

    try{
      thread.join();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  WritableMap createMap(AlarmDto dto){
    DateFormat format = new SimpleDateFormat("HH:mm:ss");
    WritableMap wm = new WritableNativeMap();

    wm.putInt("alarm_id", dto.getAlarmId());
    wm.putString("alarm_time", format.format(dto.getAlarmTime().getTime()));
    wm.putString("alarm_title", dto.getAlarmTitle());
    wm.putString("alarm_text", dto.getAlarmText());
    wm.putString("alarm_sound", dto.getAlarmSound());
    wm.putString("alarm_icon", dto.getAlarmIcon());
    wm.putBoolean("alarm_sound_loop", dto.isAlarmSoundLoop());
    wm.putBoolean("alarm_vibration", dto.isAlarmVibration());
    wm.putBoolean("alarm_noti_removable", dto.isAlarmNotiRemovable());
    wm.putBoolean("alarm_activate", dto.isAlarmActivate());

    return wm;
  }

  WritableArray createArray(AlarmDto[] dtos){
    WritableArray wa = new WritableNativeArray();

    for(AlarmDto dto:dtos)
      wa.pushMap(createMap(dto));

    return wa;
  }

  AlarmDto createAlarm(ReadableMap rm, boolean isModify){
    AlarmDto newAlarm = new AlarmDto();

    if(isModify) newAlarm.setAlarmId(rm.getInt("alarm_id"));
    //new
    String alarmDateTime = rm.getString("alarm_time"); // New attribute for date and time
    String[] dateTimeInfo = alarmDateTime.split(" "); 
    LocalDate localDate = LocalDate.parse(dateTimeInfo[1]);
    String alarmText = rm.getString("alarm_text") + " " + (localDate != null ? localDate : ""); 
    //end new

    newAlarm.setAlarmTime(Time.valueOf(dateTimeInfo[0]));
    newAlarm.setAlarmTitle(rm.getString("alarm_title"));
    newAlarm.setAlarmText(alarmText);
    newAlarm.setAlarmSound(rm.getString("alarm_sound"));
    newAlarm.setAlarmIcon(rm.getString("alarm_icon"));
    newAlarm.setAlarmSoundLoop(rm.getBoolean("alarm_sound_loop"));
    newAlarm.setAlarmVibration(rm.getBoolean("alarm_vibration"));
    newAlarm.setAlarmNotiRemovable(rm.getBoolean("alarm_noti_removable"));
    newAlarm.setAlarmActivate(rm.getBoolean("alarm_activate"));

    return newAlarm;
  }
}
