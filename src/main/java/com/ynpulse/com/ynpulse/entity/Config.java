package com.ynpulse.com.ynpulse.entity;

import java.util.List;

public class Config {
    private String schoolId;
    private String serverPostUrl;
    private String serverUser;
    private String serverPWD;
    private Device[] devices;

    @Override
    public String toString() {
        return "Config{" +
                "schoolId='" + schoolId + '\'' +
                ", serverPostUrl='" + serverPostUrl + '\'' +
                ", serverUser='" + serverUser + '\'' +
                ", serverPWD='" + serverPWD + '\'' +
                ", devices=" + devices +
                '}';
    }

    public String getSchoolId() {
        return schoolId;
    }

    public void setSchoolId(String schoolId) {
        this.schoolId = schoolId;
    }

    public String getServerPostUrl() {
        return serverPostUrl;
    }

    public void setServerPostUrl(String serverPostUrl) {
        this.serverPostUrl = serverPostUrl;
    }

    public String getServerUser() {
        return serverUser;
    }

    public void setServerUser(String serverUser) {
        this.serverUser = serverUser;
    }

    public String getServerPWD() {
        return serverPWD;
    }

    public void setServerPWD(String serverPWD) {
        this.serverPWD = serverPWD;
    }

    public Device[] getDevices() {
        return devices;
    }

    public void setDevices(Device[] devices) {
        this.devices = devices;
    }
}
