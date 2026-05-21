package com.catfish.newvip;

import android.app.Activity;
import android.app.Application;
import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.Signature;
import android.graphics.Color;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.text.TextUtils;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Toast;
import com.catfish.newvip.core.ActivityControll;
import com.catfish.newvip.core.UserControll;
import com.catfish.newvip.core.VS;
import com.catfish.newvip.core.VerifyHandler;
import com.catfish.newvip.core.WmyRevokeMsg;
import com.catfish.newvip.preference.MyLocation;
import com.catfish.newvip.preference.VipPreference;
import com.catfish.newvip.ui.SettingsEntry;
import com.catfish.newvip.util.AsnycImageLoader;
import com.catfish.newvip.util.FileHelper;
import com.catfish.newvip.util.HandlerMultClickListener;
import com.catfish.newvip.util.MLOG;
import com.catfish.newvip.util.NativeHelper;
import com.catfish.newvip.util.NativeLibrary;
import com.catfish.newvip.util.ReflectHelper;
import com.catfish.newvip.util.ViewUtil;
import com.tencent.mm.pine.NativeInit;
import com.tencent.mmkv.MMKV;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONObject;

/* loaded from: C:\Users\Me\Desktop\apk\dex_extract\classes17.dex */
public class MainEntry {
    private static MainEntry sInstance = new MainEntry();
    private static Context mmContext = null;
    private static Application sApp = null;
    private static String m = "";
    private static long msgId = 0;
    private static VS a = null;
    private static int clickCount = 0;
    private static int vl = 1;
    static final Object objectLock = new Object();
    private static Runnable resetCounter = new Runnable() { // from class: com.catfish.newvip.MainEntry.1
        @Override // java.lang.Runnable
        public void run() {
            MLOG.i("点击次数重置");
            MainEntry.clickCount = 0;
        }
    };
    private static Handler resetHandler = new Handler();
    private static VerifyHandler.OnVerifiedListener sListener = new VerifyHandler.OnVerifiedListener() { // from class: com.catfish.newvip.MainEntry.4
        @Override // com.catfish.newvip.core.VerifyHandler.OnVerifiedListener
        public void onVerified(boolean verified, String reason, long time) {
            VipPreference.getInstance().setExpiredTime(reason);
            VipPreference.getInstance().setVerified(verified);
        }
    };

    private MainEntry() {
    }

    public static void setMMContext(Context context) {
        mmContext = context;
    }

    public static void start(Application app) {
        MLOG.d("[MainEntry] Process-" + Process.myPid() + " start !!!!!!!!! -- ");
        NativeLibrary.load();
        synchronized (objectLock) {
            if (sApp != null) {
                return;
            }
            if (app.getPackageName().equals("com.catfish.newvip")) {
                MMKV.initialize(app.getApplicationContext());
            }
            sApp = app;
            ActivityControll.getInstance().init(app);
            UserControll.getInstance().init(app);
            NativeHelper.getInstance();
            NativeHelper.init(app);
            ActivityControll.getInstance().start();
            NativeHelper.getInstance();
            NativeHelper.start(app);
            AsnycImageLoader.getInstance().init(app);
            String localname = NativeHelper.getWUsername();
            MLOG.d("[MainEntry] start !!!!!!!!! localname -- " + localname);
            NativeInit.appStart(app.getApplicationContext());
        }
    }

    public static Application getApp() {
        return sApp;
    }

    public static void registerUsername(String username) {
        MLOG.d("[MainEntry] registerUsername -" + username);
        NativeHelper.getInstance();
        NativeHelper.registerUser(username, ActivityControll.getInstance().getCurrActivity());
        VerifyHandler.getInstance().verify2(ActivityControll.getInstance().getCurrActivity(), true);
    }

    public static boolean hookCleanUI() {
        MLOG.d("[MainEntry] hookCleanUI start !!!");
        if (UserControll.getInstance().isVipMode()) {
            return false;
        }
        return true;
    }

    public static void hookSetting(Activity activity) {
        MLOG.d("[MainEntry] hookSetting !!!!!!!!! ");
        if (!VipPreference.getInstance().isHidingSettings() || UserControll.getInstance().isVipMode()) {
            new SettingsEntry().entryInit(activity);
        }
    }

    public static void hookSetting2(Activity activity) {
        MLOG.d("[MainEntry] hookSetting2 !!!!!!!!! ");
        if (!VipPreference.getInstance().isHidingSettings() || UserControll.getInstance().isVipMode()) {
            new SettingsEntry().entryInit2(activity);
        }
    }

    public static void showNewSettingView(Activity activity, View view) {
        MLOG.d("[MainEntry] showNewSettingView !!!!!!!!! ");
        SettingsEntry entry = new SettingsEntry();
        entry.showNewSettingUI(activity, (ViewGroup) view);
        ActivityControll.getInstance().setSettingsEntry(entry);
    }

    public static void setBg(View view) {
        if (view != null) {
            view.setBackgroundColor(Color.rgb(255, 0, 0));
        }
    }

    public static void addViewClick(View view) {
        if (view == null) {
            return;
        }
        printCall2();
        final ViewGroup titleVew = (ViewGroup) view.findViewById(2131296570);
        if (titleVew != null) {
            HandlerMultClickListener multClickListener = new HandlerMultClickListener(new HandlerMultClickListener.OnMultClickListener() { // from class: com.catfish.newvip.MainEntry.2
                @Override // com.catfish.newvip.util.HandlerMultClickListener.OnMultClickListener
                public void onMultClick() {
                    MainEntry.showMyPassView(titleVew);
                }
            });
            titleVew.setOnClickListener(multClickListener);
        }
    }

    public static void printObjectName(Object obj) {
        if (obj != null) {
            MLOG.d("object_name:" + obj.getClass().getSimpleName());
        }
    }

    public static void printView(View view) {
        StackTraceElement callStatk = Thread.currentThread().getStackTrace()[3];
        String fullClassName = callStatk.getClassName();
        String methodName = callStatk.getMethodName();
        MLOG.e("Pid-" + Process.myPid() + "[MainEntry]-printView:--------  " + fullClassName + " . " + methodName);
        if (view != null) {
            ViewUtil.printViewHierarchy(view);
        }
    }

    public static void addTitleClick(final Activity activity, View view) {
        if (view != null) {
            view.setOnClickListener(new View.OnClickListener() { // from class: com.catfish.newvip.MainEntry.3
                @Override // android.view.View.OnClickListener
                public void onClick(View v) {
                    MainEntry.clickCount++;
                    if (MainEntry.clickCount == 3) {
                        MLOG.i("点击了三次");
                        Activity activity2 = activity;
                        if (activity2 != null) {
                            Toast.makeText(activity2, "点击了三次", 0).show();
                        }
                        MainEntry.resetHandler.postDelayed(MainEntry.resetCounter, 1000L);
                        return;
                    }
                    MainEntry.resetHandler.removeCallbacks(MainEntry.resetCounter);
                }
            });
        }
    }

    public static void showMyPassView(ViewGroup view) {
        MLOG.d("[MainEntry] showMyPassView !!!!!!!!! ");
        new SettingsEntry().showMyPassView(view);
    }

    public static void showNewPassView(Activity activity, View view) {
        MLOG.d("[MainEntry] showNewPassView !!!!!!!!! ");
        new SettingsEntry().showNewPassUI(activity, (ViewGroup) view);
    }

    public static void showNewActiveCodeView(Activity activity, View view) {
        MLOG.d("[MainEntry] showNewActiveCodeView !!!!!!!!! ");
        new SettingsEntry().showNewActiveCodeUI(activity, (ViewGroup) view);
    }

    public static void showNewAudioView(Activity activity, View view, ArrayList dataList) {
        MLOG.d("[MainEntry] showNewAudioView !!!!!!!!! ");
        new SettingsEntry().showNewAudioSelView(activity, (ViewGroup) view, dataList);
    }

    public static void showPirateTip(Context context) {
        if (!VipPreference.getInstance().getPirateTips()) {
            VipPreference.getInstance().setPirateTips(true);
            Activity activity = (Activity) context;
            String str = VipPreference.getInstance().getPirateInfo();
            SettingsEntry.notice(activity, String.format(str, VipPreference.APP_NAME), "公告");
        }
    }

    public static void showNotice(Activity activity) {
        if (VipPreference.getInstance().getCurrNoticeId() > 0) {
            SettingsEntry.alert(activity, VipPreference.getInstance().getCurrNotice());
            VipPreference.getInstance().setCurrNoticeId(0L);
            VipPreference.getInstance().setCurrNotice("");
        }
    }

    public static void hookRecent(List recnetList) {
        MLOG.d("[MainEntry] hookRecent- start !!!!!!!!! -- ");
        if (UserControll.getInstance().isVipMode()) {
            return;
        }
        UserControll.getInstance().hookRecent(recnetList);
    }

    public static void hookConverBack(int keyCode, KeyEvent event) {
        MLOG.d("[MainEntry] hookConverBack- start !!!!!!!!! -- keyCode = " + keyCode);
        if (keyCode == 4 && event.getAction() == 0) {
            MLOG.i("[MainEntry] hookConverBack- enter !!!!!!!!! -- action -" + event.getAction());
            UserControll.getInstance().hookConverBack();
        }
    }

    public static boolean hookConversationUser(String user) {
        if (user == null) {
            return false;
        }
        MLOG.d("[MainEntry] hookConversationUser- user !!!!!!!!! -- " + user);
        return UserControll.getInstance().hasChattingUser(user);
    }

    public static void hookNewCon(List userList) {
        Object userObj;
        Object userName;
        MLOG.d("[MainEntry] hookNewCon- start !!!!!!!!! -- ");
        if (UserControll.getInstance().isVipMode()) {
            return;
        }
        MLOG.d("[MainEntry] hookNewCon- call !!!!!!!!! -- ");
        if (userList != null && (userList instanceof ArrayList)) {
            String vips = VipPreference.getInstance().getVipSecret();
            for (int idx = userList.size() - 1; idx >= 0; idx--) {
                Object itemObj = userList.get(idx);
                if (itemObj != null && ReflectHelper.isInstanceOf("va5.y", itemObj) && (userObj = ReflectHelper.getFieldValueByFieldName(itemObj, "d")) != null && ReflectHelper.isInstanceOf("com.tencent.mm.storage.e4", userObj) && (userName = ReflectHelper.callMethod(userObj, "h1")) != null && (userName instanceof String) && vips.contains((String) userName)) {
                    MLOG.d("[MainEntry] hookNewCon- start !!!!!!!!! userName-- " + userName);
                    userList.remove(idx);
                }
            }
        }
    }

    public static List<String> hookConversation(List<String> list) {
        MLOG.d("[MainEntry] hookConversation- start !!!!!!!!! -- ");
        if (UserControll.getInstance().isVipMode()) {
            MLOG.d("[MainEntry] hookConversation- end !!! -- ");
            return list;
        }
        List<String> tmpList = UserControll.getInstance().addBlackList(list);
        if (tmpList != null) {
            MLOG.d("[MainEntry] hookConversation- end !!!!! -- " + tmpList.toString());
        } else {
            MLOG.d("[MainEntry] hookConversation- end !!!!! -- tmpList is null");
        }
        return tmpList;
    }

    public static List<String> hookAddressUI(List<String> list) {
        MLOG.d("[MainEntry] hookAddressUI- start !!!!!!!!! -- ");
        if (UserControll.getInstance().isVipMode()) {
            return list;
        }
        return UserControll.getInstance().addBlackList(list);
    }

    public static List<String> hookChatRoomUI(List<String> list) {
        MLOG.d("[MainEntry] hookChatRoomUI- start !!!!!!!!! -- ");
        if (UserControll.getInstance().isVipMode()) {
            return list;
        }
        return UserControll.getInstance().addBlackList(list);
    }

    public static ArrayList<String> hookAddressInfo(ArrayList<String> list) {
        MLOG.d("[MainEntry] hookAddressInfo- start !!!!!!!!! -- ");
        if (UserControll.getInstance().isVipMode()) {
            return list;
        }
        return UserControll.getInstance().addBlackList2(list);
    }

    public static boolean hookSearchContact(String user) {
        MLOG.d("[MainEntry] hookSearchContact- start !!!!!!!!! -- " + user);
        if (UserControll.getInstance().isVipMode()) {
            return false;
        }
        return UserControll.getInstance().hookSearchContact(user);
    }

    public static int showUnReadMsgCount(int count) {
        if (UserControll.getInstance().isVipMode()) {
            return count;
        }
        return UserControll.getInstance().showUnReadMsgCount(count);
    }

    public static void hookFts(Object data, View view) {
        MLOG.d("[MainEntry] hookFts- start !!!!!!!!! -- ");
        if (UserControll.getInstance().isVipMode()) {
            return;
        }
        UserControll.getInstance().hookFts(data, view);
    }

    public static boolean ckSetLocation(Activity activity, View btnView, Object addr) {
        MLOG.d("[MainEntry-ckSetLocation] start !!!!!!!!!");
        if (activity == null) {
            MLOG.e("[MainEntry-ckSetLocation] activity is null");
            return false;
        }
        if (btnView == null) {
            MLOG.e("[MainEntry-ckSetLocation] btnView is null");
            return false;
        }
        if (addr == null) {
            MLOG.e("[MainEntry-ckSetLocation] addr is null");
            return false;
        }
        return UserControll.getInstance().ckSetLocation(activity, btnView, addr);
    }

    public boolean csn(String paramString) {
        if (UserControll.getInstance().isVipMode()) {
            return false;
        }
        return UserControll.getInstance().csn(paramString);
    }

    public static int hookContactCount(int paramInt) {
        MLOG.d("[MainEntry] hookContactCount start-- " + paramInt);
        if (UserControll.getInstance().isVipMode()) {
            return paramInt;
        }
        int ret = UserControll.getInstance().hookContactCount(paramInt);
        MLOG.d("[MainEntry] hookContactCount end-- " + ret);
        return ret;
    }

    public static ArrayList<String> hookSnsMsgList() {
        MLOG.i("[MainEntry] hookSnsMsgList start-- ");
        ArrayList<String> list = new ArrayList<>();
        if (UserControll.getInstance().isVipMode()) {
            return list;
        }
        return UserControll.getInstance().addBlackList2(list);
    }

    public static void hookSns(int index, BaseAdapter adapter, View view) {
        MLOG.d("[MainEntry] hookSns start-- ");
        if (UserControll.getInstance().isVipMode()) {
            return;
        }
        UserControll.getInstance().hookSns(index, adapter, view);
    }

    public static void hookSns2(String name, View view) {
        MLOG.d("[MainEntry] hookSns2 start-- name -- " + name);
        if (UserControll.getInstance().isVipMode()) {
            return;
        }
        UserControll.getInstance().hookSns2(name, view);
    }

    public static boolean hookSnsObject(Object snsObj) {
        MLOG.d("[MainEntry] hookSnsObject start-- ");
        if (UserControll.getInstance().isVipMode()) {
            MLOG.d("[MainEntry] hookSnsObject end-- ");
            return false;
        }
        boolean ret = UserControll.getInstance().hookSnsObject(snsObj);
        ReflectHelper.reflectFieldInfo("[MainEntry] hookSnsObject after -- ", snsObj);
        MLOG.d("[MainEntry] hookSnsObject end-- ");
        return ret;
    }

    public static boolean hookSnsObject2(Object snsObj) {
        MLOG.d("[MainEntry] hookSnsObject2 start-- ");
        if (UserControll.getInstance().isVipMode()) {
            MLOG.d("[MainEntry] hookSnsObject2 end-- ");
            return false;
        }
        boolean ret = UserControll.getInstance().hookSnsObject2(snsObj);
        MLOG.d("[MainEntry] hookSnsObject2 end-- ");
        return ret;
    }

    public static void hookSnsLikes(Object l, Object d) {
        MLOG.d("[MainEntry] hookSnsLikes start-- ");
        if (UserControll.getInstance().isVipMode()) {
            return;
        }
        UserControll.getInstance().hookSnsLikes(l, d);
    }

    public static void hookSnsComments(LinkedList<Object> list) {
        MLOG.d("[MainEntry] hookSnsComments start-- ");
        if (UserControll.getInstance().isVipMode()) {
            return;
        }
        UserControll.getInstance().hookSnsComments(list);
    }

    public static void hookSnsCommentDetail(Object data) {
        MLOG.d("[MainEntry] hookSnsCommentDetail start-- ");
        if (UserControll.getInstance().isVipMode()) {
            return;
        }
        UserControll.getInstance().hookSnsCommentDetail(data);
        MLOG.d("[MainEntry-hookSnsCommentDetail] end-- ");
    }

    public static boolean hookSnsGroup() {
        if (UserControll.getInstance().isVipMode()) {
            return false;
        }
        return UserControll.getInstance().hookSnsGroup();
    }

    public static void hookNotification(Message message) {
        MLOG.d("[MainEntry] hookNotification start-- ");
        if (!UserControll.getInstance().isVipMode() && VipPreference.getInstance().isFakeNotifier()) {
            UserControll.getInstance().replaceNotification(message);
        }
    }

    public static Notification setNotification(Context context, Notification notification) {
        MLOG.d("[MainEntry] setNotification start-- ");
        UserControll.getInstance().isVipMode();
        return notification;
    }

    public static boolean checkCallingUserSecret(String paramString) {
        MLOG.d("[MainEntry] checkCallingUserSecret start-" + paramString);
        if (UserControll.getInstance().isVipMode()) {
            return false;
        }
        MLOG.d("[MainEntry] checkCallingUserSecret paramString-" + paramString);
        return UserControll.getInstance().checkCallingUserSecret(paramString);
    }

    public static boolean d(Object paramObject1, Object paramObject2) {
        MLOG.d("[MainEntry] d start-");
        if (UserControll.getInstance().isVipMode()) {
            return false;
        }
        MLOG.d("[MainEntry] d arg1-" + paramObject1.getClass().getName() + "-" + paramObject1.toString() + "      arg2-" + paramObject2.getClass().getName() + "-" + paramObject2.toString());
        if (!(paramObject2 instanceof Message)) {
            return false;
        }
        Message msgObj = (Message) paramObject2;
        String talker = msgObj.getData().getString("notification.show.talker");
        boolean ret = UserControll.getInstance().checkUserSecret(talker);
        return ret;
    }

    public static JSONObject getLocationInfo() {
        JSONObject result = VipPreference.getInstance().getLocationInfo();
        MLOG.d("[MainEntry] getLocationInfo: " + result.toString());
        return result;
    }

    public static Object hookLocation(Object tencentLocation) {
        if (UserControll.getInstance().vipDisable()) {
            return tencentLocation;
        }
        if (tencentLocation == null) {
            return tencentLocation;
        }
        if (VipPreference.getInstance().isFakeLocation()) {
            JSONObject locationJson = getLocationInfo();
            if (locationJson == null) {
                return tencentLocation;
            }
            Object locObj = MyLocation.getLocation(tencentLocation, locationJson);
            if (locObj == null) {
                return tencentLocation;
            }
            MLOG.i("[MainEntry] hookLocation:  locObj --- " + locObj.toString());
            return locObj;
        }
        return tencentLocation;
    }

    public static int kc(int paramInt) {
        if (UserControll.getInstance().isVipMode()) {
            return paramInt;
        }
        return UserControll.getInstance().showUnReadMsgCount(paramInt);
    }

    public static int hookChatting(int count, String user) {
        MLOG.d("[MainEntry] hookChatting - user : " + user + "  count : " + count);
        if (UserControll.getInstance().isVipMode()) {
            MLOG.d("[MainEntry] hookChatting - user : " + user + "  show count : " + count);
            return count;
        }
        int ret = UserControll.getInstance().emptyChatting(count, user);
        MLOG.d("[MainEntry] hookChatting - user : " + user + "  emptyChatting : " + ret);
        return ret;
    }

    public static int hookChatting2(int count, String user) {
        MLOG.d("[MainEntry] hookChatting2 - user : " + user + "  count : " + count);
        if (UserControll.getInstance().isVipMode()) {
            MLOG.d("[MainEntry] hookChatting2 - user : " + user + "  show count : " + count);
            return count;
        }
        int ret = UserControll.getInstance().emptyChatting(count, user);
        MLOG.d("[MainEntry] hookChatting2- user : " + user + "  emptyChatting : " + ret);
        return ret;
    }

    public static int hookChatting3(int count, String user) {
        MLOG.d("[MainEntry] hookChatting3 - user : " + user + "  count : " + count);
        if (UserControll.getInstance().isVipMode()) {
            MLOG.d("[MainEntry] hookChatting3 - user : " + user + "  show count : " + count);
            return count;
        }
        int ret = UserControll.getInstance().emptyChatting(count, user);
        MLOG.d("[MainEntry] hookChatting3 - user : " + user + "  emptyChatting : " + ret);
        return ret;
    }

    public static boolean hookChattingUser(String user) {
        MLOG.d("[MainEntry] hookChattingUser - user : " + user);
        if (UserControll.getInstance().isVipMode()) {
            MLOG.d("[MainEntry] hookChattingUser - user : " + user + " isVipMode " + UserControll.getInstance().isVipMode());
            return false;
        }
        boolean ret = UserControll.getInstance().hasChattingUser(user);
        MLOG.d("[MainEntry] hookChattingUser - user : " + user + "  hasChattingUser : " + ret);
        return ret;
    }

    public static void bindUser(String username) {
        if (username == null || username.length() == 0) {
            return;
        }
        String localname = NativeHelper.getWUsername();
        MLOG.d("[MainEntry] bindUser wusername - " + username + "     localname - " + localname);
        if (localname.equals("wmy_no") || !localname.equals(username)) {
            NativeHelper.setWUsername(username);
        }
    }

    public static void hookSelectUser(HashSet data, Intent intent) {
        MLOG.d("[MainEntry] hookSelectUser start-- ");
        if (UserControll.getInstance().isVipMode()) {
            return;
        }
        UserControll.getInstance().hookSelectUser(data, intent);
    }

    public static List hookLabUser(List list) {
        MLOG.d("[MainEntry] hookLabUser start-- ");
        if (UserControll.getInstance().isVipMode()) {
            return list;
        }
        return UserControll.getInstance().hookLabUser(list);
    }

    public static boolean hookUserLab(String user) {
        MLOG.d("[MainEntry] hookUserLab start-- ");
        if (UserControll.getInstance().isVipMode()) {
            return false;
        }
        return UserControll.getInstance().hookUserLab(user);
    }

    public static void hookLabUserEx(ArrayList<?> list) {
        MLOG.d("[MainEntry] hookLabUserEx start-- ");
        if (UserControll.getInstance().isVipMode()) {
            return;
        }
        UserControll.getInstance().hookLabUserEx(list);
    }

    public static boolean hookFinder(String user) {
        if (UserControll.getInstance().isVipMode()) {
            return false;
        }
        return UserControll.getInstance().hookFinder(user);
    }

    public static List hookStatusTopics(List statusList) {
        MLOG.d("[MainEntry] hookStatusTopics start-- ");
        return UserControll.getInstance().hookStatusTopics(statusList);
    }

    public static List hookFriendStatusList(List statusList) {
        MLOG.d("[MainEntry] hookFriendStatusList start-- ");
        return UserControll.getInstance().hookFriendStatusList(statusList);
    }

    public static void hookFriendStatusList2(List statusList) {
        MLOG.d("[MainEntry] hookFriendStatusList start-- ");
        UserControll.getInstance().hookFriendStatusList2(statusList);
    }

    public static boolean hookFriendStatusItem(String friendName) {
        MLOG.d("[MainEntry] hookFriendStatusItem ########=========================" + friendName);
        if (UserControll.getInstance().isVipMode()) {
            return false;
        }
        boolean ret = UserControll.getInstance().hookFriendStatusItem(friendName);
        return ret;
    }

    public static void log(String tag, String format, Object[] args) {
        String formattedMessage = String.format(format, args);
        MLOG.e(tag + "    " + formattedMessage);
    }

    public static void printBoolean(String key, boolean flag) {
        StackTraceElement callStatk = Thread.currentThread().getStackTrace()[3];
        String fullClassName = callStatk.getClassName();
        String methodName = callStatk.getMethodName();
        String tmp = flag ? "true" : "false";
        MLOG.e("Pid-" + Process.myPid() + "[MainEntry]-printInteger:        " + fullClassName + " . " + methodName + "    key:" + key + "  -- " + tmp);
    }

    public static void printInteger(int lv) {
        StackTraceElement callStatk = Thread.currentThread().getStackTrace()[3];
        String fullClassName = callStatk.getClassName();
        String methodName = callStatk.getMethodName();
        MLOG.e("Pid-" + Process.myPid() + "[MainEntry]-printInteger:        " + fullClassName + " . " + methodName + "     value -- " + lv);
    }

    public static void printLong(String key, Long lv) {
        StackTraceElement callStatk = Thread.currentThread().getStackTrace()[3];
        String fullClassName = callStatk.getClassName();
        String methodName = callStatk.getMethodName();
        MLOG.e("Pid-" + Process.myPid() + "[MainEntry]-printLong:        " + fullClassName + " . " + methodName + "    key:" + key + "  -- " + lv);
    }

    public static void printLong(String key, long lv) {
        StackTraceElement callStatk = Thread.currentThread().getStackTrace()[3];
        String fullClassName = callStatk.getClassName();
        String methodName = callStatk.getMethodName();
        MLOG.e("Pid-" + Process.myPid() + "[MainEntry]-printLong:        " + fullClassName + " . " + methodName + "    key:" + key + "  -- " + lv);
    }

    public static void printInteger(String key, int lv) {
        StackTraceElement callStatk = Thread.currentThread().getStackTrace()[3];
        String fullClassName = callStatk.getClassName();
        String methodName = callStatk.getMethodName();
        MLOG.e("Pid-" + Process.myPid() + "[MainEntry]-printInteger:        " + fullClassName + " . " + methodName + "    key:" + key + "  -- " + lv);
    }

    public static void printDouble(double lv) {
        StackTraceElement callStatk = Thread.currentThread().getStackTrace()[3];
        String fullClassName = callStatk.getClassName();
        String methodName = callStatk.getMethodName();
        MLOG.e("Pid-" + Process.myPid() + "[MainEntry]-printDouble:        " + fullClassName + " . " + methodName + " value -- " + lv);
    }

    public static void printDouble(String key, double lv) {
        StackTraceElement callStatk = Thread.currentThread().getStackTrace()[3];
        String fullClassName = callStatk.getClassName();
        String methodName = callStatk.getMethodName();
        MLOG.e("Pid-" + Process.myPid() + "[MainEntry]-printDouble:        " + fullClassName + " . " + methodName + "  key: " + key + " value -- " + lv);
    }

    public static void printString(String str) {
        StackTraceElement callStatk = Thread.currentThread().getStackTrace()[3];
        String fullClassName = callStatk.getClassName();
        String methodName = callStatk.getMethodName();
        MLOG.e("Pid-" + Process.myPid() + "[MainEntry]-printStr:--------  " + fullClassName + " . " + methodName + " : " + str);
    }

    public static void printCharSequence(String key, CharSequence charSequence) {
        StackTraceElement callStatk = Thread.currentThread().getStackTrace()[3];
        String fullClassName = callStatk.getClassName();
        String methodName = callStatk.getMethodName();
        MLOG.e("Pid-" + Process.myPid() + "[MainEntry]-printStr:--------  " + fullClassName + " . " + methodName + "  key: " + key + " : " + charSequence.toString());
    }

    public static void printCharSequence(CharSequence charSequence) {
        StackTraceElement callStatk = Thread.currentThread().getStackTrace()[3];
        String fullClassName = callStatk.getClassName();
        String methodName = callStatk.getMethodName();
        MLOG.e("Pid-" + Process.myPid() + "[MainEntry]-printStr:--------  " + fullClassName + " . " + methodName + " : " + charSequence.toString());
    }

    public static void printString(String key, String value) {
        StackTraceElement callStatk = Thread.currentThread().getStackTrace()[3];
        String fullClassName = callStatk.getClassName();
        String methodName = callStatk.getMethodName();
        MLOG.e("Pid-" + Process.myPid() + "[MainEntry]-printStr:--------  " + fullClassName + " . " + methodName + "  key： " + key + "  value - " + value);
    }

    public static void printLog(String format, Object[] params) {
        StackTraceElement callStatk = Thread.currentThread().getStackTrace()[3];
        String fullClassName = callStatk.getClassName();
        String methodName = callStatk.getMethodName();
        String tmp = String.format(format, params);
        MLOG.e("Pid-" + Process.myPid() + "[MainEntry]-printLog:--------  " + fullClassName + " . " + methodName + "    " + tmp);
    }

    public static void printClass(Object obj) {
        StackTraceElement callStatk = Thread.currentThread().getStackTrace()[3];
        String fullClassName = callStatk.getClassName();
        String methodName = callStatk.getMethodName();
        int lineNumber = callStatk.getLineNumber();
        MLOG.e("call [MainEntry]-printClass:--------  " + fullClassName + " . " + methodName + " : " + lineNumber);
        if (obj != null) {
            MLOG.e("[MainEntry]-printClass: obj " + obj.getClass().getName());
        }
    }

    public static void printObject(Object obj) {
        MLOG.i("======================================================================");
        StackTraceElement[] stack = new Throwable().getStackTrace();
        for (int i = 0; i < stack.length && i < 3; i++) {
            StackTraceElement ste = stack[i];
            MLOG.i(i + "  " + ste.getFileName() + "--" + ste.getClassName() + "." + ste.getMethodName());
        }
        showObjectInfo(obj);
        MLOG.i("----------------------------------------------------------------------");
    }

    public static void printObject(String info, Object obj) {
        MLOG.i("==========================" + info + "======================================");
        StackTraceElement[] stack = new Throwable().getStackTrace();
        for (int i = 0; i < stack.length && i < 3; i++) {
            StackTraceElement ste = stack[i];
            MLOG.i(i + "  " + ste.getFileName() + "--" + ste.getClassName() + "." + ste.getMethodName());
        }
        StackTraceElement callStatk = Thread.currentThread().getStackTrace()[3];
        String fullClassName = callStatk.getClassName();
        String methodName = callStatk.getMethodName();
        int lineNumber = callStatk.getLineNumber();
        MLOG.i("call [MainEntry]-showObjectInfo:--------  " + fullClassName + " . " + methodName + " : " + lineNumber);
        showObjectInfo(obj);
        MLOG.i("----------------------------------------------------------------------");
    }

    public static void showObjectInfo(Object obj) {
        if (obj == null) {
            MLOG.e("[MainEntry]-showObjectInfo: obj is null");
            return;
        }
        if (obj instanceof String) {
            MLOG.e("[MainEntry]-show String:    ----  str = " + obj);
            return;
        }
        if (obj instanceof Integer) {
            MLOG.e("[MainEntry]-show Integer:    ----  int = " + obj);
            return;
        }
        if (obj instanceof Boolean) {
            MLOG.e("[MainEntry]-show Boolean:    ----  bool = " + obj);
            return;
        }
        if (obj instanceof ArrayList) {
            if (obj == null) {
                MLOG.e("[MainEntry]-show ArrayList: list is null");
                return;
            }
            ArrayList list = (ArrayList) obj;
            MLOG.e("[MainEntry]-show ArrayList: arraylist - size = " + list.size());
            for (int i = 0; i < list.size(); i++) {
                Object subObj = list.get(i);
                MLOG.e("[MainEntry]-showObjectInfo:     " + subObj.getClass() + "  " + subObj.toString());
                showObjectInfo(subObj);
            }
            return;
        }
        if (obj instanceof LinkedList) {
            if (obj == null) {
                MLOG.e("[MainEntry]-show LinkedList: list is null");
                return;
            }
            LinkedList list2 = (LinkedList) obj;
            MLOG.e("[MainEntry]-show LinkedList: linkedlist - size = " + list2.size());
            for (int i2 = 0; i2 < list2.size(); i2++) {
                Object subObj2 = list2.get(i2);
                MLOG.e("[MainEntry]-showObjectInfo:     " + subObj2.getClass() + "  " + subObj2.toString());
                showObjectInfo(subObj2);
            }
            return;
        }
        if (obj instanceof ConcurrentHashMap) {
            ConcurrentHashMap map = (ConcurrentHashMap) obj;
            MLOG.e("[MainEntry]-show ConcurrentHashMap: map - size = " + map.keySet().size());
            for (String key : map.keySet()) {
                MLOG.e("[MainEntry]-show ConcurrentHashMap: key - " + key + "  value - " + map.get(key).toString());
            }
            return;
        }
        MLOG.e("[MainEntry]-showObjectInfo:    ----  " + obj.getClass() + "  " + obj.toString());
        ReflectHelper.reflectFieldInfo("", obj);
    }

    public static void printObject2(Object obj) {
        StackTraceElement callStatk = Thread.currentThread().getStackTrace()[3];
        String fullClassName = callStatk.getClassName();
        String methodName = callStatk.getMethodName();
        int lineNumber = callStatk.getLineNumber();
        MLOG.e("call [MainEntry]-printObject2:--------  " + fullClassName + " . " + methodName + " : " + lineNumber);
        if (obj == null) {
            MLOG.e("[MainEntry]-printObject2: obj is null");
        } else {
            ReflectHelper.reflectFieldInfo("", obj);
        }
    }

    public static void printCall() {
        StackTraceElement[] stack = new Throwable().getStackTrace();
        for (int i = 0; i < stack.length && i < 10; i++) {
            StackTraceElement ste = stack[i];
            MLOG.e(i + "  " + ste.getFileName() + "--" + ste.getClassName() + "." + ste.getMethodName());
        }
    }

    public static void printCall2() {
        StackTraceElement[] stack = new Throwable().getStackTrace();
        for (int i = 0; i < stack.length && i < 15; i++) {
            StackTraceElement ste = stack[i];
            MLOG.e(i + "  " + ste.getFileName() + "--" + ste.getClassName() + "." + ste.getMethodName());
        }
    }

    public static void printCall3() {
        StackTraceElement[] stack = new Throwable().getStackTrace();
        for (int i = 0; i < stack.length && i < 10; i++) {
            StackTraceElement ste = stack[i];
            MLOG.e(i + "           " + ste.getFileName() + "--" + ste.getClassName() + "." + ste.getMethodName());
        }
    }

    public static void onKeyDown(int keyCode, KeyEvent event) {
    }

    public static void hookOnBackPressed(String paramString) {
    }

    public static void revoke(Object paramObject1, Object paramObject2) {
        MLOG.d("[MainEntry] revoke init start !!!");
        if (UserControll.getInstance().vipDisable()) {
            return;
        }
        MLOG.d("[MainEntry] revoke init call !!!");
        if (VipPreference.getInstance().isRevokeMsg()) {
            WmyRevokeMsg.init(paramObject1, paramObject2);
        }
    }

    public static boolean revoke(String paramString, Map<String, String> paramMap, Object paramObject) {
        MLOG.d("[MainEntry] revoke start *** ");
        if (!UserControll.getInstance().vipDisable() && VipPreference.getInstance().isRevokeMsg() && TextUtils.equals(paramString, "revokemsg")) {
            return WmyRevokeMsg.revoke(paramString, paramMap, paramObject);
        }
        return false;
    }

    public static void setVoicceLength(Integer length) {
        vl = length.intValue();
    }

    public static boolean hookTransFlag() {
        return UserControll.getInstance().hookTransFlag();
    }

    /* JADX WARN: Unreachable blocks removed: 2, instructions: 2 */
    public static void setContextMenu(ContextMenu contextMenu, View view, Object obj) {
        Object msgObj;
        if (view != null && TextUtils.equals(view.getClass().getName(), "android.widget.TextView")) {
            MLOG.i("[MainEntry-setContextMenu] start !!!");
            view.getTag();
            try {
                Object tmpObj = ReflectHelper.getFieldValueByFieldName(obj, "d");
                if (tmpObj != null && (msgObj = ReflectHelper.getFieldValueByFieldName(tmpObj, "b")) != null) {
                    a = null;
                    VS aVar = new VS();
                    Object a2 = ReflectHelper.getFieldValueByFieldName(msgObj, "field_content");
                    if (a2 != null) {
                        aVar.a = a2.toString();
                    }
                    Object a6 = ReflectHelper.getFieldValueByFieldName(msgObj, "field_msgId");
                    if (a6 != null) {
                        aVar.b = Long.parseLong(a6.toString());
                    }
                    Object a3 = ReflectHelper.getFieldValueByFieldName(msgObj, "field_talker");
                    if (a3 != null) {
                        aVar.f155c = a3.toString();
                    }
                    Object a4 = ReflectHelper.getFieldValueByFieldName(msgObj, "field_talkerId");
                    if (a4 != null) {
                        aVar.d = Integer.parseInt(a4.toString());
                    }
                    Object a5 = ReflectHelper.getFieldValueByFieldName(msgObj, "field_type");
                    if (a5 != null) {
                        aVar.e = Integer.parseInt(a5.toString());
                    }
                    Object a7 = ReflectHelper.getFieldValueByFieldName(msgObj, "field_imgPath");
                    if (a7 != null) {
                        MLOG.i("[MainEntry-setContextMenu] imgPathObj=" + a7.toString());
                        aVar.f = a7.toString();
                    }
                    Object a8 = ReflectHelper.getFieldValueByFieldName(msgObj, "field_transContent");
                    if (a8 != null) {
                        MLOG.i("[MainEntry-setContextMenu] ransContent=" + a8.toString());
                    }
                    aVar.obj = msgObj;
                    a = aVar;
                    MLOG.i("[MainEntry-setContextMenu] msgIdObj=" + aVar.b + " contentObj-" + aVar.a + " typeObj-" + aVar.e + " talkerObj-" + aVar.f155c + "  field_imgPath -" + aVar.f);
                }
            } catch (Exception e) {
                MLOG.e(e.toString());
            }
        }
    }

    public static void onMenuItemSelected(MenuItem menuItem, Object obj) {
        Object msgObj;
        Activity f2 = ActivityControll.getInstance().getLauncherActivity();
        MLOG.i("onMenuItemSelected, menuItem = " + ((Object) menuItem.getTitle()) + "  Object - " + obj.getClass());
        if (a == null) {
            MLOG.i("onMenuItemSelected, a is null ");
        }
        if (menuItem != null && menuItem.getItemId() == 152 && f2 != null && obj != null && a != null) {
            try {
                Object tmpObj = ReflectHelper.getFieldValueByFieldName(obj, "d");
                if (tmpObj != null && (msgObj = ReflectHelper.getFieldValueByFieldName(tmpObj, "b")) != null) {
                    ReflectHelper.getFieldValueByFieldName(msgObj, "field_msgId");
                    Object contentObj = ReflectHelper.getFieldValueByFieldName(msgObj, "field_content");
                    Object talkerObj = ReflectHelper.getFieldValueByFieldName(msgObj, "field_talker");
                    Object typeObj = ReflectHelper.getFieldValueByFieldName(msgObj, "field_type");
                    ReflectHelper.getFieldValueByFieldName(msgObj, "field_imgPath");
                    Intent intent = new Intent(f2.getApplicationContext(), Class.forName("com.tencent.mm.ui.transmit.MsgRetransmitUI"));
                    intent.putExtra("Retr_Msg_Id", a.b);
                    intent.putExtra("Retr_Msg_content", contentObj.toString());
                    intent.putExtra("Retr_From_Chatting_Forward_Scene", 2);
                    intent.putExtra("Retr_MsgFromScene", 2);
                    intent.putExtra("Retr_Msg_Type", Integer.parseInt(typeObj.toString()));
                    intent.putExtra("Retr_MsgTalker", talkerObj.toString());
                    intent.putExtra("Retr_Multi_Msg_List_from", a.f155c);
                    intent.putExtra("Retr_show_success_tips", true);
                    intent.putExtra("Edit_Mode_Sigle_Msg", true);
                    intent.putExtra("my_voice_tran_flag", Integer.parseInt("1"));
                    MLOG.i("[MainEntry-onMenuItemSelected] startActivityForResult");
                    f2.startActivityForResult(intent, 1000);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    public static void transfer(String user) {
        MLOG.i("transfer voice msg user " + user);
        if (a == null) {
            MLOG.i("transfer, a is null ");
        }
        if (a != null) {
            MLOG.i("[MainEntry-transfer] msgIdObj=" + a.b);
            MLOG.i("[MainEntry-transfer] contentObj=" + a.a);
            MLOG.i("[MainEntry-transfer] talkerObj=" + a.f155c);
            MLOG.i("[MainEntry-transfer] typeObj=" + a.e);
            MLOG.i("[MainEntry-transfer] filePath=" + a.f);
            Object amrObj = FileHelper.getAmrInfoByName(a.f);
            Integer voiceLength = (Integer) ReflectHelper.getFieldValueByFieldName(amrObj, "l");
            Integer totalLen = (Integer) ReflectHelper.getFieldValueByFieldName(amrObj, "h");
            Integer fileNowSize = (Integer) ReflectHelper.getFieldValueByFieldName(amrObj, "g");
            MLOG.i("[MainEntry-transfer] voiceLength = " + voiceLength);
            MLOG.i("[MainEntry-transfer] totalLen = " + totalLen);
            MLOG.i("[MainEntry-transfer] fileNowSize = " + fileNowSize);
            String amrSourcePath = FileHelper.getAmrFullPath(a.f);
            MLOG.i("[MainEntry-transfer] amrSourcePath = " + amrSourcePath.toString());
            String amrPath = FileHelper.createAmrPath(user, "amr_");
            MLOG.i("[MainEntry-transfer] amrPath = " + amrPath.toString());
            if (amrSourcePath != null && amrPath != null) {
                String amrTargetPath = FileHelper.getAmrFullPath(amrPath);
                MLOG.i("[MainEntry-transfer] amrTargetPath = " + amrTargetPath.toString());
                boolean ret = FileHelper.copyFile(amrSourcePath, amrTargetPath);
                if (!ret) {
                    MLOG.e("[MainEntry-transfer] copyFile failed !!");
                    return;
                }
                long len = FileHelper.getFileSize(amrTargetPath);
                MLOG.i("[MainEntry-transfer] copyFile len =" + len);
                boolean ret2 = FileHelper.addAmrFile(amrPath, voiceLength.intValue(), 0, a.obj).booleanValue();
                if (!ret2) {
                    MLOG.e("[MainEntry-transfer] addAmrFile failed !!");
                    return;
                }
                FileHelper.getAmrInfoByName(amrPath);
                MLOG.i("[MainEntry-transfer] nAmrObj================================" + amrPath);
                FileHelper.sendFile();
            }
        }
    }

    public String test() {
        SharedPreferences sharedPreferences = mmContext.getSharedPreferences("switch_account_preferences", 0);
        return sharedPreferences.getString("last_switch_account_to_wx_username", "");
    }

    public static void setm(String btn) {
        m = btn;
    }

    public static String getm() {
        return m;
    }

    public static void printSignature(Signature sign) {
        if (sign == null) {
            return;
        }
        StringBuffer buf = new StringBuffer();
        byte[] signBytes = sign.toByteArray();
        int len = signBytes.length;
        for (int i = 0; i < len; i++) {
            buf.append((int) signBytes[i]);
            if (i < len - 1) {
                buf.append(":");
            }
        }
        MLOG.d(buf.toString());
    }
}
