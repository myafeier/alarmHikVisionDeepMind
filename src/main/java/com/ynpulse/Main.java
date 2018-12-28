package com.ynpulse;
import com.ynpulse.com.ynpulse.entity.Config;
import com.ynpulse.com.ynpulse.entity.Device;
import org.ho.yaml.Yaml;

import java.io.*;

public class Main {

    static Config config; //配置

    static Object lock = new Object();


    public static void main(String args[]) {
        FileInputStream inputStream= null;
        try {
            inputStream = new FileInputStream(System.getProperty("user.dir")+File.separator+"config"+File.separator+"config.yml");
            config= Yaml.loadType(inputStream,Config.class);
            System.out.println(config.toString());

        } catch (FileNotFoundException e) {
            e.printStackTrace();
            System.exit(-1);
        }

        for (Device device:config.getDevices()) {
            HkAlarm ha=new HkAlarm(config.getSchoolId(),device.getIp(),device.getPort(),device.getUser(),device.getPwd(),config.getServerPostUrl(),config.getServerUser(),config.getServerPWD());
            ha.start();
            System.out.println("device started:"+device.getIp());
        }

    }

}



