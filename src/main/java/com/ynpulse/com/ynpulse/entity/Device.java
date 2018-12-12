package com.ynpulse.com.ynpulse.entity;

public class Device {
    private String ip;
    private String port;
    private String user;
    private String pwd;


    @Override
    public String toString() {
        return "Device{" +
                "ip='" + ip + '\'' +
                ", port='" + port + '\'' +
                ", user='" + user + '\'' +
                ", pwd='" + pwd + '\'' +
                '}';
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPwd() {
        return pwd;
    }

    public void setPwd(String pwd) {
        this.pwd = pwd;
    }
}
