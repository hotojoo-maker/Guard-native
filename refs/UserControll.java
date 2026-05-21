package com.catfish.newvip.core;

import android.app.Activity;
import android.app.Application;
import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Ringtone;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.os.Vibrator;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import com.catfish.newvip.MainEntry;
import com.catfish.newvip.core.ShakeHandler;
import com.catfish.newvip.preference.LocationInfo;
import com.catfish.newvip.preference.VipPreference;
import com.catfish.newvip.util.MLOG;
import com.catfish.newvip.util.NativeHelper;
import com.catfish.newvip.util.ReflectHelper;
import com.catfish.newvip.util.SoundUtil;
import com.tencent.mapsdk.raster.model.LatLng;
import com.tencent.tinker.loader.app.MyBroadcastReceiver;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONObject;

/* loaded from: C:\Users\Me\Desktop\apk\dex_extract\classes17.dex */
public class UserControll {
    public static String SNSDATA_CLASS = "m05.g46";
    private static UserControll sInstance = new UserControll();
    private boolean isInit = false;
    private EditText mEditText = null;
    private boolean mVipMode = false;
    private ShakeHandler mShaker = null;
    private long lastSecretMsgTipsTime = 0;
    private AudioManager mAudioManager = null;
    private Vibrator mVibrator = null;
    private Ringtone ringtone = null;
    private BroadcastReceiver mReceiver = new MyBroadcastReceiver();

    public static UserControll getInstance() {
        return sInstance;
    }

    private UserControll() {
    }

    private static void invokeSearchOnBackPressed(Object ftsMainUIInstance) {
        if (ftsMainUIInstance == null) {
            return;
        }
        try {
            Class[] localPara = new Class[0];
            Method onBackPressedMethod = ReflectHelper.getMethod(ftsMainUIInstance.getClass(), "onBackPressed", localPara);
            if (onBackPressedMethod != null) {
                onBackPressedMethod.setAccessible(true);
                onBackPressedMethod.invoke(ftsMainUIInstance, new Object[0]);
            } else {
                MLOG.e("not found onBackPressedMethod");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void init(Application app) {
        if (this.isInit) {
            MLOG.d("[UserControll] is inited ");
            return;
        }
        MLOG.d("[UserControll] init ");
        this.isInit = true;
        if (app != null) {
            this.mAudioManager = (AudioManager) app.getSystemService("audio");
            this.mVibrator = (Vibrator) app.getSystemService("vibrator");
        }
        BroadcastHandler.getInstance().register(this.mReceiver, new String[]{"android.intent.action.SCREEN_OFF", "android.intent.action.CLOSE_SYSTEM_DIALOGS"});
        ShakeHandler shakeHandler = new ShakeHandler(app);
        this.mShaker = shakeHandler;
        shakeHandler.registerOnShakeListener(new ShakeHandler.OnShakeListener() { // from class: com.catfish.newvip.core.UserControll.1
            @Override // com.catfish.newvip.core.ShakeHandler.OnShakeListener
            public void onShake() {
                MLOG.i("onShake start !!!");
                if (UserControll.this.isVipMode()) {
                    MLOG.i("onShake exitVipMode !!!");
                    UserControll.this.exitVipMode(false);
                }
            }
        });
        MLOG.d("[UserControll] registerOnShakeListener ");
    }

    public synchronized void enterVipMode() {
        this.mVipMode = true;
        MLOG.i("enterVipMode- mVipMode=" + this.mVipMode);
        ActivityControll.getInstance().notifyStateChanged();
        ShakeHandler shakeHandler = this.mShaker;
        if (shakeHandler != null) {
            shakeHandler.start();
        }
        BroadcastHandler.getInstance().register(this.mReceiver, new String[]{"android.intent.action.SCREEN_OFF", "android.intent.action.CLOSE_SYSTEM_DIALOGS"});
    }

    public synchronized void exitVipMode(boolean isBack) {
        this.mVipMode = false;
        MLOG.i("exitVipMode- mVipMode=" + this.mVipMode);
        ActivityControll.getInstance().notifyStateChanged();
        ActivityControll.getInstance().getCurrActivity();
        boolean vipEnable = NativeHelper.getVipEnable();
        if (vipEnable) {
            ActivityControll.getInstance().backToLauncher(isBack);
        }
        ShakeHandler shakeHandler = this.mShaker;
        if (shakeHandler != null) {
            shakeHandler.stop();
        }
        VibratorHandler.getInstance().vibrate();
        BroadcastHandler.getInstance().unregister(this.mReceiver);
    }

    public boolean isVipMode() {
        return this.mVipMode || !VipPreference.getInstance().isVipEnable();
    }

    public boolean vipDisable() {
        return !VipPreference.getInstance().isVipEnable();
    }

    public void hookConverBack() {
        MLOG.i("[UserControll] hookConverBack ---");
        boolean vipEnable = NativeHelper.getVipEnable();
        if (isVipMode() && vipEnable) {
            MLOG.i("[UserControll] hookConverBack  exitVipMode!!");
            exitVipMode(false);
        }
    }

    public void registerEditText(ViewGroup viewGroup) {
        if (viewGroup == null) {
            return;
        }
        int childCount = viewGroup.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View childAt = viewGroup.getChildAt(i);
            if (childAt instanceof EditText) {
                addEditWatcher((EditText) childAt);
                return;
            } else {
                if (childAt instanceof ViewGroup) {
                    registerEditText((ViewGroup) childAt);
                }
            }
        }
    }

    public void monitorFtsEdit(CharSequence textview, Activity activity) {
        if (NativeHelper.getInstance().chkPwd(activity, textview.toString())) {
            invokeSearchOnBackPressed(ActivityControll.getInstance().getSearchActivity());
            enterVipMode();
            VerifyHandler.getInstance().verify2(MainEntry.getApp().getApplicationContext(), false);
        }
    }

    private void addEditWatcher(EditText textview) {
        if (this.mEditText == textview) {
            return;
        }
        this.mEditText = textview;
        textview.addTextChangedListener(new TextWatcher() { // from class: com.catfish.newvip.core.UserControll.2
            @Override // android.text.TextWatcher
            public void afterTextChanged(Editable arg0) {
                UserControll.this.monitorFtsEdit(arg0.toString(), ActivityControll.getInstance().getSearchActivity());
            }

            @Override // android.text.TextWatcher
            public void beforeTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {
            }

            @Override // android.text.TextWatcher
            public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {
            }
        });
    }

    public boolean hookTransFlag() {
        NativeHelper.getInstance();
        boolean ret = NativeHelper.getTransVoiceMsg();
        if (ret) {
            MLOG.e("[UserControl ]hookTransFlag getTransVoiceMsg true");
        } else {
            MLOG.e("[UserControl ]hookTransFlag getTransVoiceMsg false");
        }
        return ret;
    }

    public boolean hookAddressInfo(String user) {
        return !TextUtils.isEmpty(user) && VipPreference.getInstance().getVipSecret().contains(user);
    }

    public void hookRecent(List recnetList) {
        Object tmpObj;
        if (recnetList != null) {
            for (int idx = recnetList.size() - 1; idx >= 0; idx--) {
                Object recentObj = recnetList.get(idx);
                if (recentObj != null && (tmpObj = ReflectHelper.getFieldValueByFieldName(recentObj, "d")) != null) {
                    ReflectHelper.reflectFieldInfo("", tmpObj);
                }
            }
        }
    }

    public List<String> addBlackList(List<String> list) {
        String vipSecret = VipPreference.getInstance().getVipSecret();
        if (vipSecret.length() == 0) {
            return list;
        }
        String[] vips = vipSecret.split(",");
        HashSet<String> tmpSet = new HashSet<>();
        if (list != null && list.size() > 0) {
            tmpSet.addAll(list);
        }
        ArrayList arrayList = new ArrayList();
        if (list != null && list.size() > 0) {
            arrayList.addAll(list);
        }
        List<String> newList = new ArrayList<>();
        for (String vip : vips) {
            if (!tmpSet.contains(vip)) {
                tmpSet.add(vip);
                newList.add(vip);
            }
        }
        if (list != null) {
            list.addAll(newList);
        }
        newList.addAll(arrayList);
        return newList;
    }

    public ArrayList<String> addBlackList2(ArrayList<String> list) {
        String vipSecret = VipPreference.getInstance().getVipSecret();
        if (vipSecret.length() == 0) {
            return list;
        }
        String[] vips = vipSecret.split(",");
        HashSet<String> tmpSet = new HashSet<>();
        if (list != null && list.size() > 0) {
            tmpSet.addAll(list);
        }
        List<String> oldList = new ArrayList<>();
        if (list != null && list.size() > 0) {
            oldList.addAll(list);
        }
        ArrayList<String> newList = new ArrayList<>();
        for (String vip : vips) {
            if (!tmpSet.contains(vip)) {
                tmpSet.add(vip);
                newList.add(vip);
            }
        }
        if (list != null) {
            list.addAll(newList);
        }
        newList.addAll(oldList);
        return newList;
    }

    public void setSecretAudioIdx(int idx, String name) {
        NativeHelper.setSecretAudioIdx(idx);
        NativeHelper.setSecretAudioName(name);
    }

    public void vibrate() {
        if (System.currentTimeMillis() - this.lastSecretMsgTipsTime < 1000 || this.mAudioManager == null || this.mVibrator == null) {
            return;
        }
        try {
            this.lastSecretMsgTipsTime = System.currentTimeMillis();
            if (this.mAudioManager.getRingerMode() == 0) {
                return;
            }
            long[] pattern = {0, 180, 40, 120};
            AudioAttributes audioAttributes = new AudioAttributes.Builder().setContentType(4).setUsage(4).build();
            this.mVibrator.vibrate(pattern, -1, audioAttributes);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void playSound() {
        if (System.currentTimeMillis() - this.lastSecretMsgTipsTime < 1000) {
            return;
        }
        this.lastSecretMsgTipsTime = System.currentTimeMillis();
        if (this.mAudioManager.getRingerMode() == 0) {
            return;
        }
        SoundUtil.getInstance().playSound(NativeHelper.getSecretAudioIdx());
    }

    public void replaceNotification(Message message) {
        Bundle data = message.getData();
        CharSequence string = data.getString("notification.show.talker");
        data.getString("notification.show.message.content");
        data.getInt("notification.show.message.type");
        data.getInt("notification.show.tipsflag");
        String[] vips = VipPreference.getInstance().getVipSecret().split(",");
        for (String fVar : vips) {
            if (TextUtils.equals(fVar, string)) {
                data.putString("notification.show.talker", "weixin");
                data.putString("notification.show.message.content", "[应用消息]");
                data.putString("notification.show.myflag", "1");
            }
        }
    }

    public Notification setNotification(Context context, Notification notification) {
        boolean secretNotification = VipPreference.getInstance().secretNotification();
        boolean secretVirbrate = VipPreference.getInstance().secretVirbrate();
        int secretAudioIdx = VipPreference.getInstance().secretAudioIdx();
        if (!secretNotification && !secretVirbrate) {
            MLOG.e("[UserCtrol-setNotification] 00000-- ");
            notification.sound = null;
            notification.vibrate = null;
        }
        if (secretVirbrate && secretVirbrate && secretAudioIdx < 0) {
            MLOG.e("[UserCtrol-setNotification] 11111-- ");
            notification.sound = null;
            notification.defaults |= 2;
        } else if (secretAudioIdx >= 0) {
            MLOG.e("[UserCtrol-setNotification] 2222-- " + secretAudioIdx);
            if (notification.sound != null) {
                MLOG.e("[UserCtrol-setNotification] 2222  old-- " + notification.sound.toString());
            }
            String fileName = SoundUtil.getSoundFileName(secretAudioIdx);
            MLOG.e("[UserCtrol-setNotification] 2222  fileName-- " + fileName);
            Uri uri = SoundUtil.getUriByFile(context, new File(fileName));
            if (uri != null) {
                notification.sound = uri;
                MLOG.e("[UserCtrol-setNotification] 2222  new-- " + notification.sound.toString());
            } else {
                MLOG.e("[UserCtrol-setNotification] 2222  new uri is null");
                Uri uri2 = Uri.parse("content://com.tencent.mm.external.fileprovider/NewMsgRingtone/Elegance.mp3");
                notification.sound = uri2;
                MLOG.e("[UserCtrol-setNotification] 2222  new uri2 " + uri2.toString());
            }
        }
        return notification;
    }

    public int emptyChatting(int count, String user) {
        if (VipPreference.getInstance().getVipSecret().contains(user)) {
            return 0;
        }
        return count;
    }

    public boolean hasChattingUser(String user) {
        if (VipPreference.getInstance().getVipSecret().contains(user)) {
            return true;
        }
        return false;
    }

    public boolean checkCallingUserSecret(String paramString) {
        if (TextUtils.isEmpty(paramString) || !VipPreference.getInstance().getVipSecret().contains(paramString)) {
            return false;
        }
        VipPreference.getInstance().isSecretVibrateTips();
        boolean secretNotification = VipPreference.getInstance().secretNotification();
        if (secretNotification) {
            VipPreference.getInstance().secretVirbrate();
            int secretAudioIdx = VipPreference.getInstance().secretAudioIdx();
            MLOG.e("checkCallingUserSecret  play sound ########## secretAudioIdx - " + secretAudioIdx);
            if (secretAudioIdx >= 0) {
                playSound();
                return true;
            }
            vibrate();
            return true;
        }
        return true;
    }

    public boolean checkUserSecret(String paramString) {
        if (TextUtils.isEmpty(paramString) || !VipPreference.getInstance().getVipSecret().contains(paramString)) {
            return false;
        }
        MLOG.e("checkUserSecret  play sound !!!!!!!!!");
        boolean secretNotification = VipPreference.getInstance().secretNotification();
        if (secretNotification) {
            VipPreference.getInstance().secretVirbrate();
            int secretAudioIdx = VipPreference.getInstance().secretAudioIdx();
            MLOG.e("checkUserSecret  play sound ########## secretAudioIdx - " + secretAudioIdx);
            if (secretAudioIdx >= 0) {
                playSound();
            }
            vibrate();
            return true;
        }
        return true;
    }

    public boolean hookSearchContact(String user) {
        return !TextUtils.isEmpty(user) && VipPreference.getInstance().getVipSecret().contains(user);
    }

    public boolean ckSetLocation(Activity activity, View cancelBtn, Object addr) {
        int value = activity.getIntent().getIntExtra("my_set_location", 0);
        if (value != 1 || cancelBtn == null) {
            return false;
        }
        try {
            LocationInfo info = new LocationInfo();
            info.a = (String) ReflectHelper.getFieldValueByFieldName(addr, "r");
            info.b = (String) ReflectHelper.getFieldValueByFieldName(addr, "d");
            float lat = ((Float) ReflectHelper.getFieldValueByFieldName(addr, "t")).floatValue();
            float lng = ((Float) ReflectHelper.getFieldValueByFieldName(addr, "u")).floatValue();
            info.f156c = new LatLng(lat, lng);
            info.d = new LocationInfo.AddressInfo();
            info.d.f157c = (String) ReflectHelper.getFieldValueByFieldName(addr, "g");
            info.d.b = (String) ReflectHelper.getFieldValueByFieldName(addr, "f");
            info.d.a = 0L;
            String locationStr = info.a();
            JSONObject locationJson = new JSONObject(locationStr);
            VipPreference.getInstance().setLocationInfo(locationJson);
            MLOG.i("set new_location-" + locationJson.toString());
            JSONObject result = VipPreference.getInstance().getLocationInfo();
            MLOG.i("get new_location-" + result.toString());
            activity.finish();
            return true;
        } catch (Exception ex) {
            MLOG.e(ex.toString());
            return false;
        }
    }

    public boolean csn(String user) {
        return !TextUtils.isEmpty(user) && VipPreference.getInstance().getVipSecret().contains(user) && VipPreference.getInstance().isFakeNotifier();
    }

    public void hookFts(Object data, View view) {
        String strData;
        try {
            Field f = Utils.a(data.getClass(), "s");
            if (f != null && view != null) {
                Object fData = f.get(data);
                MLOG.i("hookFts: start -> data cls " + data.getClass() + "  view Layout class-" + view.getLayoutParams().getClass());
                ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
                if (fData != null && (fData instanceof String) && (strData = (String) fData) != null && VipPreference.getInstance().getVipSecret().contains(strData)) {
                    layoutParams.height = 1;
                    view.setVisibility(8);
                    view.setLayoutParams(layoutParams);
                }
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    public boolean hookSnsGroup() {
        if (VipPreference.getInstance().isVipEnable() && VipPreference.getInstance().isHideGroup()) {
            return true;
        }
        return false;
    }

    public boolean ttt() {
        long ct = System.currentTimeMillis() / 1000;
        if (1000 >= ct) {
            return false;
        }
        return 0 != 0 || ct - 1000 <= 3600;
    }

    public final int dkF(List<String> exceptList) {
        if (exceptList != null && exceptList.size() > 0) {
            String tmpStr = "and talker not in (";
            int idx = 0;
            while (idx < exceptList.size()) {
                tmpStr = tmpStr + (idx > 0 ? ",'" : "'") + exceptList.get(idx) + "'";
                idx++;
            }
            String str = "select count(*) from SnsComment where isRead = ? and isSilence != ? " + tmpStr + ")";
        }
        return 0;
    }

    public final String hkL(List<String> exceptList) {
        String sql = "select *, rowid from SnsComment where isRead = ?  and isSilence != ? ";
        if (exceptList != null && exceptList.size() > 0) {
            String tmpStr = "and talker not in (";
            int idx = 0;
            while (idx < exceptList.size()) {
                tmpStr = tmpStr + (idx > 0 ? ",'" : "'") + exceptList.get(idx) + "'";
                idx++;
            }
            sql = "select *, rowid from SnsComment where isRead = ?  and isSilence != ? " + tmpStr + ")";
        }
        return sql + " order by createTime desc ";
    }

    public final String alA(int i2, List<String> exceptList) {
        String sql = "select *, rowid from SnsComment where isSend = 0 ";
        if (exceptList != null && exceptList.size() > 0) {
            String tmpStr = "and talker not in (";
            int idx = 0;
            while (idx < exceptList.size()) {
                tmpStr = tmpStr + (idx > 0 ? ",'" : "'") + exceptList.get(idx) + "'";
                idx++;
            }
            sql = "select *, rowid from SnsComment where isSend = 0 " + tmpStr + ")";
        }
        return sql + " order by createTime desc LIMIT " + i2;
    }

    public void hookSns(int index, BaseAdapter adapter, View view) {
        Object data = adapter.getItem(index);
        MLOG.d("[UserControll] hookSns snsData !!!!!!!! ");
        Field f = Utils.a(data.getClass(), "field_userName");
        if (f != null && view != null) {
            try {
                String user = (String) f.get(data);
                String vips = VipPreference.getInstance().getVipSecret();
                MLOG.d("[UserControll] hookSns snsData !!!!!!!! user-" + user + "   vips- " + vips);
                boolean isVip = vips.contains(user);
                ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
                if (layoutParams == null) {
                    layoutParams = new AbsListView.LayoutParams(-1, 0);
                }
                layoutParams.height = isVip ? 1 : -2;
                view.setLayoutParams(layoutParams);
                view.setVisibility(isVip ? 8 : 0);
            } catch (IllegalAccessException ex) {
                MLOG.e(ex.toString());
            }
        }
    }

    public void hookSns2(String name, View view) {
        if (name != null && view != null) {
            String vips = VipPreference.getInstance().getVipSecret();
            MLOG.d("[UserControll] hookSns2 " + view.getClass() + "  !!!!!!!! user-" + name);
            boolean isVip = vips.contains(name);
            ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
            if (layoutParams == null) {
                layoutParams = new ViewGroup.LayoutParams(-1, -2);
            }
            layoutParams.height = isVip ? 1 : -2;
            view.setLayoutParams(layoutParams);
            view.setVisibility(isVip ? 8 : 0);
        }
    }

    public boolean hookSnsObject(Object snsObject) {
        boolean ret1 = false;
        boolean ret2 = false;
        MLOG.d("[UserControll]hookSnsObject start -- ");
        if (snsObject != null) {
            try {
                if (snsObject == null) {
                    MLOG.e("[UserControll] hookSnsObject snsObject !!!!!!!! snsObject is null");
                    return false;
                }
                ret1 = hookSnsCommentOne("CommentUserList", snsObject);
                ret2 = hookSnsCommentOne("LikeUserList", snsObject);
            } catch (Exception ex) {
                MLOG.e(ex.toString());
            }
        }
        MLOG.d("[UserControll]hookSnsObject end -- ");
        return ret1 || ret2;
    }

    public boolean hookSnsObject2(Object snsObject) {
        boolean ret = false;
        MLOG.d("[UserControll]hookSnsObject2 start -- ");
        if (snsObject != null) {
            try {
                if (snsObject == null) {
                    MLOG.e("[UserControll] hookSnsObject2 snsObject !!!!!!!! snsObject is null");
                    return false;
                }
                ret = hasSnsCommentOne("CommentUserList", snsObject);
                if (!ret) {
                    ret = hookSnsCommentOne("LikeUserList", snsObject);
                }
            } catch (Exception ex) {
                MLOG.e(ex.toString());
            }
        }
        MLOG.d("[UserControll]hookSnsObject2 end -- ");
        return ret;
    }

    /* JADX WARN: Code restructure failed: missing block: B:13:0x0051, code lost:
    
        r1 = (java.util.List) r9;
     */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public void hookSnsLikes(java.lang.Object r12, java.lang.Object r13) {
        /*
            r11 = this;
            java.lang.StringBuilder r0 = new java.lang.StringBuilder
            r0.<init>()
            java.lang.String r1 = "[UserControll] hookSnsLikes start!  listObj-"
            java.lang.StringBuilder r0 = r0.append(r1)
            java.lang.Class r1 = r12.getClass()
            java.lang.StringBuilder r0 = r0.append(r1)
            java.lang.String r1 = "   dObj-"
            java.lang.StringBuilder r0 = r0.append(r1)
            java.lang.Class r1 = r13.getClass()
            java.lang.StringBuilder r0 = r0.append(r1)
            java.lang.String r0 = r0.toString()
            com.catfish.newvip.util.MLOG.d(r0)
            r0 = 0
            r1 = 0
            r2 = 0
            boolean r3 = r12 instanceof java.util.List
            if (r3 == 0) goto L33
            r0 = r12
            java.util.List r0 = (java.util.List) r0
            r2 = 1
        L33:
            r3 = 0
            r4 = 1
            java.lang.Class r5 = r13.getClass()     // Catch: java.lang.IllegalAccessException -> L57 java.lang.NullPointerException -> L5c
            java.lang.reflect.Field[] r5 = r5.getDeclaredFields()     // Catch: java.lang.IllegalAccessException -> L57 java.lang.NullPointerException -> L5c
            int r6 = r5.length     // Catch: java.lang.IllegalAccessException -> L57 java.lang.NullPointerException -> L5c
            r7 = r3
        L3f:
            if (r7 >= r6) goto L56
            r8 = r5[r7]     // Catch: java.lang.IllegalAccessException -> L57 java.lang.NullPointerException -> L5c
            r8.setAccessible(r4)     // Catch: java.lang.IllegalAccessException -> L57 java.lang.NullPointerException -> L5c
            java.lang.Object r9 = r8.get(r13)     // Catch: java.lang.IllegalAccessException -> L57 java.lang.NullPointerException -> L5c
            boolean r10 = r9 instanceof java.util.List     // Catch: java.lang.IllegalAccessException -> L57 java.lang.NullPointerException -> L5c
            if (r10 == 0) goto L53
            r6 = r9
            java.util.List r6 = (java.util.List) r6     // Catch: java.lang.IllegalAccessException -> L57 java.lang.NullPointerException -> L5c
            r1 = r6
            goto L56
        L53:
            int r7 = r7 + 1
            goto L3f
        L56:
            goto L61
        L57:
            r5 = move-exception
            com.catfish.newvip.util.MLOG.e(r5)
            goto L61
        L5c:
            r5 = move-exception
            com.catfish.newvip.util.MLOG.e(r5)
            goto L56
        L61:
            if (r1 != 0) goto L64
            return
        L64:
            java.lang.String r5 = "[UserControll] hookSnsLikes start!!!!!!!!!!"
            com.catfish.newvip.util.MLOG.e(r5)
            int r5 = r1.size()
            if (r5 <= 0) goto L7b
            java.lang.String r5 = "[UserControll] hookSnsLikes reflectMethodInfo!!!!!!!!!!"
            com.catfish.newvip.util.MLOG.e(r5)
            java.lang.Object r5 = r1.get(r3)
            com.catfish.newvip.util.ReflectHelper.reflectMethodInfo(r5)
        L7b:
            com.catfish.newvip.preference.VipPreference r5 = com.catfish.newvip.preference.VipPreference.getInstance()
            java.lang.String r5 = r5.getVipSecret()
            java.lang.Class[] r6 = new java.lang.Class[r4]
            java.lang.Class r7 = java.lang.Integer.TYPE
            r6[r3] = r7
            java.lang.String r7 = "bu4.c"
            java.lang.String r8 = "a"
            java.lang.reflect.Method r6 = com.catfish.newvip.core.Utils.getMethod(r7, r8, r6)
            if (r6 == 0) goto Lcd
            int r7 = r1.size()
            int r7 = r7 - r4
        L98:
            if (r7 < 0) goto Lcd
            java.lang.Object r8 = r1.get(r7)     // Catch: java.lang.Exception -> Lbb java.lang.reflect.InvocationTargetException -> Lc0 java.lang.IllegalAccessException -> Lc5
            java.lang.Object[] r9 = new java.lang.Object[r4]     // Catch: java.lang.Exception -> Lbb java.lang.reflect.InvocationTargetException -> Lc0 java.lang.IllegalAccessException -> Lc5
            java.lang.Integer r10 = java.lang.Integer.valueOf(r2)     // Catch: java.lang.Exception -> Lbb java.lang.reflect.InvocationTargetException -> Lc0 java.lang.IllegalAccessException -> Lc5
            r9[r3] = r10     // Catch: java.lang.Exception -> Lbb java.lang.reflect.InvocationTargetException -> Lc0 java.lang.IllegalAccessException -> Lc5
            java.lang.Object r9 = r6.invoke(r8, r9)     // Catch: java.lang.Exception -> Lbb java.lang.reflect.InvocationTargetException -> Lc0 java.lang.IllegalAccessException -> Lc5
            java.lang.String r9 = (java.lang.String) r9     // Catch: java.lang.Exception -> Lbb java.lang.reflect.InvocationTargetException -> Lc0 java.lang.IllegalAccessException -> Lc5
            boolean r10 = r5.contains(r9)     // Catch: java.lang.Exception -> Lbb java.lang.reflect.InvocationTargetException -> Lc0 java.lang.IllegalAccessException -> Lc5
            if (r10 == 0) goto Lc9
            r1.remove(r7)     // Catch: java.lang.Exception -> Lbb java.lang.reflect.InvocationTargetException -> Lc0 java.lang.IllegalAccessException -> Lc5
            if (r0 == 0) goto Lc9
            r0.remove(r7)     // Catch: java.lang.Exception -> Lbb java.lang.reflect.InvocationTargetException -> Lc0 java.lang.IllegalAccessException -> Lc5
            goto Lc9
        Lbb:
            r8 = move-exception
            com.catfish.newvip.util.MLOG.e(r8)
            goto Lca
        Lc0:
            r8 = move-exception
            com.catfish.newvip.util.MLOG.e(r8)
            goto Lc9
        Lc5:
            r8 = move-exception
            com.catfish.newvip.util.MLOG.e(r8)
        Lc9:
        Lca:
            int r7 = r7 + (-1)
            goto L98
        Lcd:
            java.lang.String r3 = "[UserControll] hookSnsLikes end!!!!!!!!!!"
            com.catfish.newvip.util.MLOG.e(r3)
            return
        */
        throw new UnsupportedOperationException("Method not decompiled: com.catfish.newvip.core.UserControll.hookSnsLikes(java.lang.Object, java.lang.Object):void");
    }

    public void hookSnsComments(LinkedList<Object> list) {
        String vips = VipPreference.getInstance().getVipSecret();
        MLOG.e("[UserControll] hookSnsComments vips -- " + vips);
        for (int i = list.size() - 1; i >= 0; i--) {
            Object obj = list.get(i);
            String userName1 = (String) ReflectHelper.callMethod(obj, "getUsername");
            String replyName1 = (String) ReflectHelper.callMethod(obj, "getReply_username");
            MLOG.e("[UserControll] hookSnsComments userName1 -- " + userName1 + "   replyName1 -" + replyName1);
            String userName = (String) ReflectHelper.getFieldValueByFieldName(obj, "username");
            String replyName = (String) ReflectHelper.getFieldValueByFieldName(obj, "reply_username");
            MLOG.e("[UserControll] hookSnsComments userName -- " + userName + "   replyName -" + replyName);
            if (vips.contains(userName) || vips.contains(replyName)) {
                list.remove(i);
            }
        }
    }

    private boolean hookSnsCommentOne(String fieldName, Object snsObject) {
        String vips;
        Field field;
        boolean ret = false;
        try {
            vips = VipPreference.getInstance().getVipSecret();
            field = Utils.getField("com.tencent.mm.protocal.protobuf.SnsObject", fieldName);
        } catch (Exception ex) {
            MLOG.e(ex.toString());
        }
        if (field == null) {
            MLOG.e("[UserControll] hookSnsCommentOne -- " + fieldName + "not found field -" + fieldName);
            return false;
        }
        List<Object> dataList = (List) field.get(snsObject);
        int vipCount = 0;
        for (int i = dataList.size() - 1; i >= 0; i--) {
            Object a = dataList.get(i);
            Field usernameField = Utils.getField(SNSDATA_CLASS, "d");
            String user = (String) usernameField.get(a);
            if (vips.contains(user)) {
                ret = true;
                dataList.remove(i);
                vipCount++;
            }
        }
        String listCountName = "";
        String countName = "";
        if (fieldName.equals("LikeUserList")) {
            listCountName = "LikeUserListCount";
            countName = "LikeCount";
        } else if (fieldName == "CommentUserList") {
            listCountName = "CommentUserListCount";
            countName = "CommentCount";
        }
        if (listCountName != "") {
            int oldCount = ((Integer) ReflectHelper.getFieldValueByFieldName(snsObject, listCountName)).intValue();
            int newCount = oldCount - vipCount;
            ReflectHelper.setFieldValueByFieldName(snsObject, listCountName, Integer.valueOf(newCount > 0 ? newCount : 0));
        }
        if (countName != "") {
            int oldCount2 = ((Integer) ReflectHelper.getFieldValueByFieldName(snsObject, countName)).intValue();
            int newCount2 = oldCount2 - vipCount;
            ReflectHelper.setFieldValueByFieldName(snsObject, countName, Integer.valueOf(newCount2 > 0 ? newCount2 : 0));
        }
        return ret;
    }

    private boolean hasSnsCommentOne(String fieldName, Object snsObject) {
        try {
            String vips = VipPreference.getInstance().getVipSecret();
            Field field = Utils.getField("com.tencent.mm.protocal.protobuf.SnsObject", fieldName);
            if (field == null) {
                MLOG.e("[UserControll] hasSnsCommentOne -- " + fieldName + "not found field -" + fieldName);
                return false;
            }
            List<Object> dataList = (List) field.get(snsObject);
            for (int i = dataList.size() - 1; i >= 0; i--) {
                Object a = dataList.get(i);
                Field usernameField = Utils.getField(SNSDATA_CLASS, "d");
                String user = (String) usernameField.get(a);
                if (vips.contains(user)) {
                    return true;
                }
            }
            return false;
        } catch (Exception ex) {
            MLOG.e(ex.toString());
            return false;
        }
    }

    public Object hookSnsCommentDetail(Object snsobj) {
        try {
            MLOG.d("[UserControll]hookSnsCommentDetail -- ");
            hookSnsCommentOne("CommentUserList", snsobj);
            hookSnsCommentOne("LikeUserList", snsobj);
        } catch (Exception ex) {
            MLOG.e(ex.toString());
        }
        return snsobj;
    }

    public int hookContactCount(int count) {
        int vipCpunt = VipPreference.getInstance().getVipMemberCount();
        if (vipCpunt > count) {
            return 0;
        }
        return count - vipCpunt;
    }

    public int showUnReadMsgCount(int count) {
        if (VipPreference.getInstance().isShowUnReadMsg()) {
            return count;
        }
        return 0;
    }

    public void hookFriendStatus(ConcurrentHashMap map) {
        VipPreference.getInstance().getVipSecret();
        String[] vips = VipPreference.getInstance().getVipSecret().split(",");
        for (String vip : vips) {
            map.remove(vip);
        }
    }

    public List hookFriendStatusList(List<Object> statusList) {
        if (statusList == null || statusList.size() == 0) {
            return statusList;
        }
        List tempList = new ArrayList();
        for (int i = 0; i < statusList.size(); i++) {
            try {
                try {
                    Object status = statusList.get(i);
                    MLOG.e("hookStatusTopics  status:  " + status.getClass().getName() + "--" + status.toString());
                    Field f = Utils.a(status.getClass(), "field_UserName");
                    if (f != null) {
                        String user = (String) f.get(status);
                        MLOG.e("hookStatusTopics  user:  " + user);
                        boolean isVip = VipPreference.getInstance().getVipSecret().contains(user);
                        if (!isVip) {
                            MLOG.e("hookStatusTopics  user:  " + user + "--------- remove");
                            tempList.add(status);
                        }
                    } else {
                        MLOG.e("hookStatusTopics:--not found field_UserName field");
                    }
                } catch (Exception ex) {
                    MLOG.e(ex.toString());
                }
            } catch (Throwable th) {
                return tempList;
            }
        }
        return tempList;
    }

    public void hookFriendStatusList2(List<Object> statusList) {
        if (statusList == null || statusList.size() == 0) {
            return;
        }
        try {
            for (int i = statusList.size(); i >= 0; i--) {
                Object status = statusList.get(i);
                Field f = Utils.a(status.getClass(), "field_UserName");
                if (f != null) {
                    String user = (String) f.get(status);
                    boolean isVip = VipPreference.getInstance().getVipSecret().contains(user);
                    if (isVip) {
                        statusList.remove(i);
                    }
                }
            }
        } catch (Exception ex) {
            MLOG.e(ex.toString());
        }
    }

    public List hookStatusTopics(List<Object> statusList) {
        if (statusList != null) {
            try {
                if (statusList.size() != 0) {
                    try {
                        for (int i = statusList.size() - 1; i >= 0; i--) {
                            Object status = statusList.get(i);
                            MLOG.e("hookStatusTopics  objct:  " + status.getClass().getName());
                            Field f = Utils.a(status.getClass(), "field_UserName");
                            if (f != null) {
                                String user = (String) f.get(status);
                                MLOG.e("hookStatusTopics  user:  " + user);
                                boolean isVip = VipPreference.getInstance().getVipSecret().contains(user);
                                if (isVip) {
                                    MLOG.e("hookStatusTopics  user:  " + user + "--------- remove");
                                    statusList.remove(i);
                                }
                            } else {
                                MLOG.e("hookStatusTopics:--not found field_UserName field");
                            }
                        }
                    } catch (Exception ex) {
                        MLOG.e(ex.toString());
                    }
                    return statusList;
                }
            } catch (Throwable th) {
                return statusList;
            }
        }
        return statusList;
    }

    public void hookFriendStatusList(String statusId, ArrayList<Object> statusList) {
        if (statusList == null || statusList.size() == 0) {
            return;
        }
        try {
            for (int i = statusList.size() - 1; i >= 0; i--) {
                Object status = statusList.get(i);
                Field f = Utils.a(status.getClass(), "field_UserName");
                if (f != null) {
                    String user = (String) f.get(status);
                    boolean isVip = VipPreference.getInstance().getVipSecret().contains(user);
                    if (isVip) {
                        statusList.remove(i);
                    }
                }
            }
        } catch (Exception ex) {
            MLOG.e(ex.toString());
        }
    }

    public boolean hookFriendStatusItem(String friendName) {
        return VipPreference.getInstance().getVipSecret().contains(friendName);
    }

    public void hookSelectUser(HashSet data, Intent intent) {
        if (intent != null) {
            intent.getBooleanExtra("from_select_contact", false);
        }
        if (data != null) {
            String[] vips = VipPreference.getInstance().getVipSecret().split(",");
            data.addAll(Arrays.asList(vips));
        }
    }

    public List hookLabUser(List list) {
        String[] vips = VipPreference.getInstance().getVipSecret().split(",");
        if (list == null) {
            list = new ArrayList();
        }
        list.addAll(Arrays.asList(vips));
        return list;
    }

    public boolean hookUserLab(String user) {
        return VipPreference.getInstance().getVipSecret().contains(user);
    }

    public void hookLabUserEx(ArrayList<?> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        String[] vips = VipPreference.getInstance().getVipSecret().split(",");
        for (int idx = list.size() - 1; idx >= 0; idx--) {
            Object item = list.get(idx);
            if (String.class.isInstance(item)) {
                String tempStr = (String) item;
                int length = vips.length;
                int i = 0;
                while (true) {
                    if (i < length) {
                        String username = vips[i];
                        if (!tempStr.equals(username)) {
                            i++;
                        } else {
                            list.remove(idx);
                            break;
                        }
                    }
                }
            }
        }
    }

    public boolean hookFinder(String user) {
        return VipPreference.getInstance().getVipSecret().contains(user);
    }
}
