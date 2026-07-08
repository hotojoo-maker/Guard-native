package com.catfish.newvip.preference;

import android.app.Activity;
import com.catfish.newvip.core.ActivityControll;
import com.catfish.newvip.util.NativeHelper;
import org.json.JSONException;
import org.json.JSONObject;

/* loaded from: C:\Users\Me\Desktop\apk\dex_extract\classes17.dex */
public class VipPreference {
    public static final String APP_NAME = "微密友";
    public static final String EXPIRED_TIME = "expired_time";
    public static final String PAY_URL = "http://api.wxsecret.com/b/oem.html";
    public static final String VIP_GROUPS = "vip_groups";
    public static final String VIP_MEMBER = "vip_member";
    private static VipPreference sInstance = new VipPreference();
    public long currNoticeId = 0;
    public String currNotice = "";
    public String desc = "";

    public static VipPreference getInstance() {
        return sInstance;
    }

    private VipPreference() {
    }

    public void setLocationInfo(JSONObject info) {
        NativeHelper.setLocationInfo(info.toString());
    }

    public JSONObject getLocationInfo() {
        String str = NativeHelper.getLocationInfo();
        if (str == null || str.length() <= 0) {
            return null;
        }
        try {
            JSONObject jsonObject = new JSONObject(str);
            return jsonObject;
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void setNoticeInfo(JSONObject info) {
        NativeHelper.setNoticeInfo(info.toString());
    }

    public long getCurrNoticeId() {
        return this.currNoticeId;
    }

    public void setCurrNoticeId(long noticeId) {
        this.currNoticeId = noticeId;
    }

    public String getCurrNotice() {
        return this.currNotice;
    }

    public void setCurrNotice(String notice) {
        this.currNotice = notice;
    }

    public String getDesc() {
        return this.desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public JSONObject getNoticeInfo() {
        String str = NativeHelper.getNoticeInfo();
        if (str == null || str.length() <= 0) {
            return null;
        }
        try {
            JSONObject jsonObject = new JSONObject(str);
            return jsonObject;
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    public int getVipMemberCount() {
        String vipMembers = getVipMember();
        if (vipMembers == null || vipMembers.length() <= 0) {
            return 0;
        }
        String[] arrMembers = vipMembers.split(",");
        int count = arrMembers.length;
        return count;
    }

    public int getVipGroupsCount() {
        String vipGroups = getVipGroups();
        if (vipGroups.length() <= 0) {
            return 0;
        }
        String[] arrGroups = vipGroups.split(",");
        int count = arrGroups.length;
        return count;
    }

    public String getVipSecret() {
        String vipMembers = getVipMember();
        String vipGroups = getVipGroups();
        if (vipMembers != null && vipMembers.length() > 0) {
            if (vipGroups != null && vipGroups.length() > 0) {
                String result = vipMembers + "," + vipGroups;
                return result;
            }
            return vipMembers;
        }
        return vipGroups;
    }

    public String getVipMember() {
        Activity activity = ActivityControll.getInstance().getCurrActivity();
        String result = NativeHelper.getVipMember(activity);
        return result;
    }

    public void setVipMember(String vip) {
        NativeHelper.setVipMember(vip);
    }

    public String getVipGroups() {
        Activity activity = ActivityControll.getInstance().getCurrActivity();
        return NativeHelper.getVipGroups(activity);
    }

    public void setVipGroups(String groups) {
        NativeHelper.setVipGroups(groups);
    }

    public void setPassword(String passwd) {
        NativeHelper.setPassword(passwd);
    }

    public String getBottomTip() {
        return NativeHelper.getBottomTip();
    }

    public void enableVip(boolean b) {
        NativeHelper.setVipEnable(b);
    }

    public boolean isVipEnable() {
        return NativeHelper.getVipEnable() && NativeHelper.getVerified();
    }

    public void hidingSettings(boolean b) {
        NativeHelper.setHidingSettings(b);
    }

    public boolean isHidingSettings() {
        return NativeHelper.getHidingSettings();
    }

    public void fakeNotifier(boolean b) {
        NativeHelper.setFakeNotifier(b);
    }

    public boolean isFakeNotifier() {
        return NativeHelper.getFakeNotifier();
    }

    public void secretVibrateTips(boolean b) {
        NativeHelper.setSecretVibrateTips(b);
    }

    public void setPirateTips(boolean b) {
        NativeHelper.setPirateTips(b);
    }

    public String getPirateInfo() {
        return NativeHelper.getPirateInfo();
    }

    public boolean getPirateTips() {
        return NativeHelper.getPirateTips();
    }

    public void setBuySwitch(boolean b) {
        NativeHelper.setBuySwitch(b);
    }

    public boolean getBuySwitch() {
        return NativeHelper.getBuySwitch();
    }

    public boolean isSecretVibrateTips() {
        return NativeHelper.getSecretVibrateTips();
    }

    public void secretTips(boolean b) {
        NativeHelper.setSecretTips(b);
    }

    public boolean isSecretTips() {
        return NativeHelper.getSecretTips();
    }

    public void showUnReadMsg(boolean b) {
        NativeHelper.setShowUnReadMsg(b);
    }

    public boolean isShowUnReadMsg() {
        return NativeHelper.getShowUnReadMsg();
    }

    public void revokeMsg(boolean b) {
        NativeHelper.setRevokeMsg(b);
    }

    public boolean isRevokeMsg() {
        return NativeHelper.getRevokeMsg();
    }

    public void transVoiceMsg(boolean b) {
        NativeHelper.setTransVoiceMsg(b);
    }

    public boolean isTransVoiceMsg() {
        return NativeHelper.getTransVoiceMsg();
    }

    public void hideGroup(boolean b) {
        NativeHelper.setHideGroup(b);
    }

    public boolean isHideGroup() {
        return NativeHelper.getHideGroup();
    }

    public void fakeLocation(boolean b) {
        NativeHelper.setFakeLocation(b);
    }

    public boolean isFakeLocation() {
        return NativeHelper.getFakeLocation();
    }

    public boolean secretNotification() {
        return NativeHelper.getSecretNotification();
    }

    public void setSecretNotification(boolean flag) {
        NativeHelper.setSecretNotification(flag);
    }

    public boolean secretVirbrate() {
        return NativeHelper.getSecretVirbrate();
    }

    public void setSecretVirbrate(boolean flag) {
        NativeHelper.setSecretVirbrate(flag);
    }

    public void setSecretAudioIdx(int idx) {
        NativeHelper.setSecretAudioIdx(idx);
    }

    public int secretAudioIdx() {
        return NativeHelper.getSecretAudioIdx();
    }

    public void setSecretAudioIdx(boolean flag) {
        NativeHelper.setSecretVirbrate(flag);
    }

    public void setVerified(boolean b) {
        NativeHelper.setVerified(b);
    }

    public void setExpiredTime(String v) {
        NativeHelper.setExpiredTime(v);
    }

    public String getExpiredTime() {
        return NativeHelper.getExpiredTime();
    }
}
