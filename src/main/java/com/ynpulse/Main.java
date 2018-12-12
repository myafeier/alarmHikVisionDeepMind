package com.ynpulse;
import com.burgstaller.okhttp.AuthenticationCacheInterceptor;
import com.burgstaller.okhttp.CachingAuthenticatorDecorator;
import com.burgstaller.okhttp.digest.CachingAuthenticator;
import com.burgstaller.okhttp.digest.Credentials;
import com.burgstaller.okhttp.digest.DigestAuthenticator;
import com.hikvision.HCNetSDK;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.ynpulse.com.ynpulse.entity.Config;
import okhttp3.*;
import org.ho.yaml.Yaml;

import java.io.*;
import java.net.HttpURLConnection;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Main {
    static Config config; //配置

    public BufferedOutputStream logStream=null;

    static HCNetSDK hCNetSDK = HCNetSDK.INSTANCE;
    HCNetSDK.NET_DVR_DEVICEINFO_V30 m_strDeviceInfo;//设备信息
    NativeLong lUserID;//用户句柄
    NativeLong lAlarmHandle;//报警布防句柄
    NativeLong lListenHandle;//报警监听句柄
    HCNetSDK.FMSGCallBack fMSFCallBack;//报警回调函数实现
    HCNetSDK.FMSGCallBack_V31 fMSFCallBack_V31;//报警回调函数实现

    static DigestAuthenticator authenticatorOfDevice=null;
    final Map<String, CachingAuthenticator> authCacheOfDevice=new ConcurrentHashMap<String, CachingAuthenticator>();
    static  OkHttpClient clientOfDevice=null;



    static DigestAuthenticator authenticatorOfServer=null;
    final Map<String, CachingAuthenticator> authCacheOfServer=new ConcurrentHashMap<String, CachingAuthenticator>();
    static OkHttpClient clientOfServer=null ;


    final OkHttpClient tempClient=new OkHttpClient.Builder().build();

    public static void main(String args[]) {
        System.out.println(System.getProperty("user.dir"));
        Main m = new Main();
        m.loginToDevice();
        m.setupAlarmChan();
        while (true){
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    Main(){
        System.out.println(System.getProperty("user.dir"));
        FileInputStream inputStream= null;
        try {
            inputStream = new FileInputStream(System.getProperty("user.dir")+"\\config\\config.yml");
            config= Yaml.loadType(inputStream,Config.class);
            System.out.println(config.toString());

        } catch (FileNotFoundException e) {
            e.printStackTrace();
            System.exit(-1);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }

        //初始化okhttpclient

        authenticatorOfDevice=new DigestAuthenticator(new Credentials(m_sDeviceUser,m_sDevicePwd));
        clientOfDevice=new OkHttpClient.Builder()
                .authenticator(new CachingAuthenticatorDecorator(this.authenticatorOfDevice, this.authCacheOfDevice))
                .addInterceptor(new AuthenticationCacheInterceptor(authCacheOfDevice))
                .build() ;

        authenticatorOfServer=new DigestAuthenticator(new Credentials(serverUser,serverPwd));
        clientOfServer=new OkHttpClient.Builder()
                .authenticator(new CachingAuthenticatorDecorator(this.authenticatorOfServer, this.authCacheOfServer))
                .addInterceptor(new AuthenticationCacheInterceptor(authCacheOfServer))
                .build() ;

        //打开日志
        String currentDate=new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        File file=new File(System.getProperty("user.dir")+"\\"+currentDate+".log");
        if(!file.exists()){
            try {
                boolean created=file.createNewFile();
                if(!created){
                    System.out.println("create log file error");
                    System.exit(-1);
                }
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(-1);
            }
        }
        try {
            FileOutputStream outputStream=new FileOutputStream(file);
            logStream=new BufferedOutputStream(outputStream);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            System.exit(-1);
        }

        this.lUserID = new NativeLong(-1);
        this.lAlarmHandle = new NativeLong(-1);
        this.lListenHandle = new NativeLong(-1);
    }

    public boolean loginToDevice() {
        boolean initSuc = hCNetSDK.NET_DVR_Init();
        if (initSuc != true) {
            System.out.println("初始化失败");
            return false;
        }

        //注册之前先注销已注册的用户,预览情况下不可注销
        if (lUserID.longValue() > -1) {
            hCNetSDK.NET_DVR_Logout(lUserID);
            lUserID = new NativeLong(-1);
        }

        //注册
        lUserID = hCNetSDK.NET_DVR_Login_V30(m_sDeviceIP,
                m_sDevicePort, m_sDeviceUser, m_sDevicePwd, m_strDeviceInfo);

        long userID = lUserID.longValue();
        if (userID == -1) {
            System.out.println("注册失败");
            return false;
        }
        System.out.println("注册成功");

        return true;
    }

    //布防
    public boolean setupAlarmChan() {
        if (lUserID.intValue() == -1) {
            System.out.println("请先注册");
            return false;
        }
        if (lAlarmHandle.intValue() < 0)//尚未布防,需要布防
        {
            if (fMSFCallBack_V31 == null) {
                fMSFCallBack_V31 = new FMSGCallBack_V31();
                Pointer pUser = null;
                if (!hCNetSDK.NET_DVR_SetDVRMessageCallBack_V31(fMSFCallBack_V31, pUser)) {
                    System.out.println("设置回调函数失败!");
                    return false;
                }
            }
            HCNetSDK.NET_DVR_SETUPALARM_PARAM m_strAlarmInfo = new HCNetSDK.NET_DVR_SETUPALARM_PARAM();
            m_strAlarmInfo.dwSize = m_strAlarmInfo.size();
            m_strAlarmInfo.byLevel = 1;
            m_strAlarmInfo.byAlarmInfoType = 1;
            m_strAlarmInfo.write();
            lAlarmHandle = hCNetSDK.NET_DVR_SetupAlarmChan_V41(lUserID, m_strAlarmInfo);
            if (lAlarmHandle.intValue() == -1) {
                System.out.println("布防失败");
                return false;
            }
        }

        System.out.println("布防成功");
        return true;
    }

    public class FMSGCallBack_V31 implements HCNetSDK.FMSGCallBack_V31 {
        //报警信息回调函数
        public boolean invoke(NativeLong lCommand, HCNetSDK.NET_DVR_ALARMER pAlarmer, Pointer pAlarmInfo, int dwBufLen, Pointer pUser) {
            AlarmDataHandle(lCommand, pAlarmer, pAlarmInfo, dwBufLen, pUser);
            return true;
        }
    }

    public class FMSGCallBack implements HCNetSDK.FMSGCallBack {
        //报警信息回调函数
        public void invoke(NativeLong lCommand, HCNetSDK.NET_DVR_ALARMER pAlarmer, Pointer pAlarmInfo, int dwBufLen, Pointer pUser) {
            AlarmDataHandle(lCommand, pAlarmer, pAlarmInfo, dwBufLen, pUser);
        }
    }


    //消息处理器
    public void AlarmDataHandle(NativeLong lCommand, HCNetSDK.NET_DVR_ALARMER pAlarmer, Pointer pAlarmInfo, int dwBufLen, Pointer pUser) {
        try {
            String sAlarmType = new String();

            //报警时间
            Date today = new Date();
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
            String[] sIP = new String[2];
            sAlarmType = new String("lCommand=") + lCommand.intValue();
            //lCommand是传的报警类型
            switch (lCommand.intValue()) {

                /**  用不到的消息类型
                 case HCNetSDK.COMM_ALARM_V30:
                 HCNetSDK.NET_DVR_ALARMINFO_V30 strAlarmInfoV30 = new HCNetSDK.NET_DVR_ALARMINFO_V30();
                 strAlarmInfoV30.write();
                 Pointer pInfoV30 = strAlarmInfoV30.getPointer();
                 pInfoV30.write(0, pAlarmInfo.getByteArray(0, strAlarmInfoV30.size()), 0, strAlarmInfoV30.size());
                 strAlarmInfoV30.read();
                 switch (strAlarmInfoV30.dwAlarmType)
                 {
                 case 0:
                 sAlarmType = sAlarmType + new String("：信号量报警") + "，"+ "报警输入口：" + (strAlarmInfoV30.dwAlarmInputNumber+1);
                 break;
                 case 1:
                 sAlarmType = sAlarmType + new String("：硬盘满");
                 break;
                 case 2:
                 sAlarmType = sAlarmType + new String("：信号丢失");
                 break;
                 case 3:
                 sAlarmType = sAlarmType + new String("：移动侦测") + "，"+ "报警通道：";
                 for (int i=0; i<64; i++)
                 {
                 if (strAlarmInfoV30.byChannel[i] == 1)
                 {
                 sAlarmType=sAlarmType + "ch"+(i+1)+" ";
                 }
                 }
                 break;
                 case 4:
                 sAlarmType = sAlarmType + new String("：硬盘未格式化");
                 break;
                 case 5:
                 sAlarmType = sAlarmType + new String("：读写硬盘出错");
                 break;
                 case 6:
                 sAlarmType = sAlarmType + new String("：遮挡报警");
                 break;
                 case 7:
                 sAlarmType = sAlarmType + new String("：制式不匹配");
                 break;
                 case 8:
                 sAlarmType = sAlarmType + new String("：非法访问");
                 break;
                 }
                 break;
                 case HCNetSDK.COMM_ALARM_RULE:
                 HCNetSDK.NET_VCA_RULE_ALARM strVcaAlarm = new HCNetSDK.NET_VCA_RULE_ALARM();
                 strVcaAlarm.write();
                 Pointer pVcaInfo = strVcaAlarm.getPointer();
                 pVcaInfo.write(0, pAlarmInfo.getByteArray(0, strVcaAlarm.size()), 0, strVcaAlarm.size());
                 strVcaAlarm.read();

                 switch (strVcaAlarm.struRuleInfo.wEventTypeEx)
                 {

                 case 1:
                 sAlarmType = sAlarmType + new String("：穿越警戒面") + "，" +
                 "_wPort:" + strVcaAlarm.struDevInfo.wPort +
                 "_byChannel:" + strVcaAlarm.struDevInfo.byChannel +
                 "_byIvmsChannel:" +  strVcaAlarm.struDevInfo.byIvmsChannel +
                 "_Dev IP：" + new String(strVcaAlarm.struDevInfo.struDevIP.sIpV4);
                 break;
                 case 2:
                 sAlarmType = sAlarmType + new String("：目标进入区域") + "，" +
                 "_wPort:" + strVcaAlarm.struDevInfo.wPort +
                 "_byChannel:" + strVcaAlarm.struDevInfo.byChannel +
                 "_byIvmsChannel:" +  strVcaAlarm.struDevInfo.byIvmsChannel +
                 "_Dev IP：" + new String(strVcaAlarm.struDevInfo.struDevIP.sIpV4);
                 break;
                 case 3:
                 sAlarmType = sAlarmType + new String("：目标离开区域") + "，" +
                 "_wPort:" + strVcaAlarm.struDevInfo.wPort +
                 "_byChannel:" + strVcaAlarm.struDevInfo.byChannel +
                 "_byIvmsChannel:" +  strVcaAlarm.struDevInfo.byIvmsChannel +
                 "_Dev IP：" + new String(strVcaAlarm.struDevInfo.struDevIP.sIpV4);
                 break;
                 default:
                 sAlarmType = sAlarmType + new String("：其他行为分析报警，事件类型：")
                 + strVcaAlarm.struRuleInfo.wEventTypeEx +
                 "_wPort:" + strVcaAlarm.struDevInfo.wPort +
                 "_byChannel:" + strVcaAlarm.struDevInfo.byChannel +
                 "_byIvmsChannel:" +  strVcaAlarm.struDevInfo.byIvmsChannel +
                 "_Dev IP：" + new String(strVcaAlarm.struDevInfo.struDevIP.sIpV4);
                 break;
                 }
                 newRow[0] = dateFormat.format(today);
                 //报警类型
                 newRow[1] = sAlarmType;
                 //报警设备IP地址
                 sIP = new String(pAlarmer.sDeviceIP).split("\0", 2);
                 newRow[2] = sIP[0];
                 alarmTableModel.insertRow(0, newRow);

                 if(strVcaAlarm.dwPicDataLen>0)
                 {
                 SimpleDateFormat sf = new SimpleDateFormat("yyyyMMddHHmmss");
                 String newName = sf.format(new Date());
                 FileOutputStream fout;
                 try {
                 fout = new FileOutputStream("E:"+newName+"01.jpg");
                 //将字节写入文件
                 long offset = 0;
                 ByteBuffer buffers = strVcaAlarm.pImage.getPointer().getByteBuffer(offset, strVcaAlarm.dwPicDataLen);
                 byte [] bytes = new byte[strVcaAlarm.dwPicDataLen];
                 buffers.rewind();
                 buffers.get(bytes);
                 fout.write(bytes);
                 fout.close();
                 }catch (FileNotFoundException e) {
                 // TODO Auto-generated catch block
                 e.printStackTrace();
                 } catch (IOException e) {
                 // TODO Auto-generated catch block
                 e.printStackTrace();
                 }
                 }
                 break;
                 case HCNetSDK.COMM_UPLOAD_PLATE_RESULT:
                 HCNetSDK.NET_DVR_PLATE_RESULT strPlateResult = new HCNetSDK.NET_DVR_PLATE_RESULT();
                 strPlateResult.write();
                 Pointer pPlateInfo = strPlateResult.getPointer();
                 pPlateInfo.write(0, pAlarmInfo.getByteArray(0, strPlateResult.size()), 0, strPlateResult.size());
                 strPlateResult.read();
                 try {
                 String srt3=new String(strPlateResult.struPlateInfo.sLicense,"GBK");
                 sAlarmType = sAlarmType + "：交通抓拍上传，车牌："+ srt3;
                 }
                 catch (UnsupportedEncodingException e1) {
                 // TODO Auto-generated catch block
                 e1.printStackTrace();
                 } catch (IOException e) {
                 // TODO Auto-generated catch block
                 e.printStackTrace();
                 }

                 newRow[0] = dateFormat.format(today);
                 //报警类型
                 newRow[1] = sAlarmType;
                 //报警设备IP地址
                 sIP = new String(pAlarmer.sDeviceIP).split("\0", 2);
                 newRow[2] = sIP[0];
                 alarmTableModel.insertRow(0, newRow);

                 if(strPlateResult.dwPicLen>0)
                 {
                 SimpleDateFormat sf = new SimpleDateFormat("yyyyMMddHHmmss");
                 String newName = sf.format(new Date());
                 FileOutputStream fout;
                 try {
                 fout = new FileOutputStream("E://"+newName+"01.jpg");
                 //将字节写入文件
                 long offset = 0;
                 ByteBuffer buffers = strPlateResult.pBuffer1.getByteBuffer(offset, strPlateResult.dwPicLen);
                 byte [] bytes = new byte[strPlateResult.dwPicLen];
                 buffers.rewind();
                 buffers.get(bytes);
                 fout.write(bytes);
                 fout.close();
                 } catch (FileNotFoundException e) {
                 // TODO Auto-generated catch block
                 e.printStackTrace();
                 } catch (IOException e) {
                 // TODO Auto-generated catch block
                 e.printStackTrace();
                 }
                 }
                 break;
                 case HCNetSDK.COMM_ITS_PLATE_RESULT:
                 HCNetSDK.NET_ITS_PLATE_RESULT strItsPlateResult = new HCNetSDK.NET_ITS_PLATE_RESULT();
                 strItsPlateResult.write();
                 Pointer pItsPlateInfo = strItsPlateResult.getPointer();
                 pItsPlateInfo.write(0, pAlarmInfo.getByteArray(0, strItsPlateResult.size()), 0, strItsPlateResult.size());
                 strItsPlateResult.read();
                 try {
                 String srt3=new String(strItsPlateResult.struPlateInfo.sLicense,"GBK");
                 sAlarmType = sAlarmType + ",车辆类型："+strItsPlateResult.byVehicleType + ",交通抓拍上传，车牌："+ srt3;
                 }
                 catch (UnsupportedEncodingException e1) {
                 // TODO Auto-generated catch block
                 e1.printStackTrace();
                 } catch (IOException e) {
                 // TODO Auto-generated catch block
                 e.printStackTrace();
                 }

                 newRow[0] = dateFormat.format(today);
                 //报警类型
                 newRow[1] = sAlarmType;
                 //报警设备IP地址
                 sIP = new String(pAlarmer.sDeviceIP).split("\0", 2);
                 newRow[2] = sIP[0];
                 alarmTableModel.insertRow(0, newRow);

                 for(int i=0;i<strItsPlateResult.dwPicNum;i++)
                 {
                 if(strItsPlateResult.struPicInfo[i].dwDataLen>0)
                 {
                 SimpleDateFormat sf = new SimpleDateFormat("yyyyMMddHHmmss");
                 String newName = sf.format(new Date());
                 FileOutputStream fout;
                 try {
                 String filename = "E://"+ newName+"_type"+strItsPlateResult.struPicInfo[i].byType+".jpg";
                 fout = new FileOutputStream(filename);
                 //将字节写入文件
                 long offset = 0;
                 ByteBuffer buffers = strItsPlateResult.struPicInfo[i].pBuffer.getByteBuffer(offset, strItsPlateResult.struPicInfo[i].dwDataLen);
                 byte [] bytes = new byte[strItsPlateResult.struPicInfo[i].dwDataLen];
                 buffers.rewind();
                 buffers.get(bytes);
                 fout.write(bytes);
                 fout.close();
                 } catch (FileNotFoundException e) {
                 // TODO Auto-generated catch block
                 e.printStackTrace();
                 } catch (IOException e) {
                 // TODO Auto-generated catch block
                 e.printStackTrace();
                 }
                 }
                 }
                 break;
                 case HCNetSDK.COMM_ALARM_PDC:
                 HCNetSDK.NET_DVR_PDC_ALRAM_INFO strPDCResult = new HCNetSDK.NET_DVR_PDC_ALRAM_INFO();
                 strPDCResult.write();
                 Pointer pPDCInfo = strPDCResult.getPointer();
                 pPDCInfo.write(0, pAlarmInfo.getByteArray(0, strPDCResult.size()), 0, strPDCResult.size());
                 strPDCResult.read();

                 if(strPDCResult.byMode == 0)
                 {
                 strPDCResult.uStatModeParam.setType(HCNetSDK.NET_DVR_STATFRAME.class);
                 sAlarmType = sAlarmType + "：客流量统计，进入人数："+ strPDCResult.dwEnterNum + "，离开人数：" + strPDCResult.dwLeaveNum +
                 ", byMode:" + strPDCResult.byMode + ", dwRelativeTime:" + strPDCResult.uStatModeParam.struStatFrame.dwRelativeTime +
                 ", dwAbsTime:" + strPDCResult.uStatModeParam.struStatFrame.dwAbsTime;
                 }
                 if(strPDCResult.byMode == 1)
                 {
                 strPDCResult.uStatModeParam.setType(HCNetSDK.NET_DVR_STATTIME.class);
                 String strtmStart = "" + String.format("%04d", strPDCResult.uStatModeParam.struStatTime.tmStart.dwYear) +
                 String.format("%02d", strPDCResult.uStatModeParam.struStatTime.tmStart.dwMonth) +
                 String.format("%02d", strPDCResult.uStatModeParam.struStatTime.tmStart.dwDay) +
                 String.format("%02d", strPDCResult.uStatModeParam.struStatTime.tmStart.dwHour) +
                 String.format("%02d", strPDCResult.uStatModeParam.struStatTime.tmStart.dwMinute) +
                 String.format("%02d", strPDCResult.uStatModeParam.struStatTime.tmStart.dwSecond);
                 String strtmEnd = "" + String.format("%04d", strPDCResult.uStatModeParam.struStatTime.tmEnd.dwYear) +
                 String.format("%02d", strPDCResult.uStatModeParam.struStatTime.tmEnd.dwMonth) +
                 String.format("%02d", strPDCResult.uStatModeParam.struStatTime.tmEnd.dwDay) +
                 String.format("%02d", strPDCResult.uStatModeParam.struStatTime.tmEnd.dwHour) +
                 String.format("%02d", strPDCResult.uStatModeParam.struStatTime.tmEnd.dwMinute) +
                 String.format("%02d", strPDCResult.uStatModeParam.struStatTime.tmEnd.dwSecond);
                 sAlarmType = sAlarmType + "：客流量统计，进入人数："+ strPDCResult.dwEnterNum + "，离开人数：" + strPDCResult.dwLeaveNum +
                 ", byMode:" + strPDCResult.byMode + ", tmStart:" + strtmStart + ",tmEnd :" + strtmEnd;
                 }

                 newRow[0] = dateFormat.format(today);
                 //报警类型
                 newRow[1] = sAlarmType;
                 //报警设备IP地址
                 sIP = new String(strPDCResult.struDevInfo.struDevIP.sIpV4).split("\0", 2);
                 newRow[2] = sIP[0];
                 alarmTableModel.insertRow(0, newRow);
                 break;

                 case HCNetSDK.COMM_ITS_PARK_VEHICLE:
                 HCNetSDK.NET_ITS_PARK_VEHICLE strItsParkVehicle = new HCNetSDK.NET_ITS_PARK_VEHICLE();
                 strItsParkVehicle.write();
                 Pointer pItsParkVehicle = strItsParkVehicle.getPointer();
                 pItsParkVehicle.write(0, pAlarmInfo.getByteArray(0, strItsParkVehicle.size()), 0, strItsParkVehicle.size());
                 strItsParkVehicle.read();
                 try {
                 String srtParkingNo=new String(strItsParkVehicle.byParkingNo).trim(); //车位编号
                 String srtPlate=new String(strItsParkVehicle.struPlateInfo.sLicense,"GBK").trim(); //车牌号码
                 sAlarmType = sAlarmType + ",停产场数据,车位编号："+ srtParkingNo + ",车位状态："
                 + strItsParkVehicle.byLocationStatus+ ",车牌："+ srtPlate;
                 }
                 catch (UnsupportedEncodingException e1) {
                 // TODO Auto-generated catch block
                 e1.printStackTrace();
                 } catch (IOException e) {
                 // TODO Auto-generated catch block
                 e.printStackTrace();
                 }

                 newRow[0] = dateFormat.format(today);
                 //报警类型
                 newRow[1] = sAlarmType;
                 //报警设备IP地址
                 sIP = new String(pAlarmer.sDeviceIP).split("\0", 2);
                 newRow[2] = sIP[0];
                 alarmTableModel.insertRow(0, newRow);

                 for(int i=0;i<strItsParkVehicle.dwPicNum;i++)
                 {
                 if(strItsParkVehicle.struPicInfo[i].dwDataLen>0)
                 {
                 SimpleDateFormat sf = new SimpleDateFormat("yyyyMMddHHmmss");
                 String newName = sf.format(new Date());
                 FileOutputStream fout;
                 try {
                 String filename = "E://"+ newName+"_type"+strItsParkVehicle.struPicInfo[i].byType+".jpg";
                 fout = new FileOutputStream(filename);
                 //将字节写入文件
                 long offset = 0;
                 ByteBuffer buffers = strItsParkVehicle.struPicInfo[i].pBuffer.getByteBuffer(offset, strItsParkVehicle.struPicInfo[i].dwDataLen);
                 byte [] bytes = new byte[strItsParkVehicle.struPicInfo[i].dwDataLen];
                 buffers.rewind();
                 buffers.get(bytes);
                 fout.write(bytes);
                 fout.close();
                 } catch (FileNotFoundException e) {
                 // TODO Auto-generated catch block
                 e.printStackTrace();
                 } catch (IOException e) {
                 // TODO Auto-generated catch block
                 e.printStackTrace();
                 }
                 }
                 }
                 break;
                 case HCNetSDK.COMM_ALARM_TFS:
                 HCNetSDK.NET_DVR_TFS_ALARM strTFSAlarmInfo = new HCNetSDK.NET_DVR_TFS_ALARM();
                 strTFSAlarmInfo.write();
                 Pointer pTFSInfo = strTFSAlarmInfo.getPointer();
                 pTFSInfo.write(0, pAlarmInfo.getByteArray(0, strTFSAlarmInfo.size()), 0, strTFSAlarmInfo.size());
                 strTFSAlarmInfo.read();

                 try {
                 String srtPlate=new String(strTFSAlarmInfo.struPlateInfo.sLicense,"GBK").trim(); //车牌号码
                 sAlarmType = sAlarmType + "：交通取证报警信息，违章类型："+ strTFSAlarmInfo.dwIllegalType + "，车牌号码：" + srtPlate
                 + "，车辆出入状态：" + strTFSAlarmInfo.struAIDInfo.byVehicleEnterState;
                 }
                 catch (UnsupportedEncodingException e1) {
                 // TODO Auto-generated catch block
                 e1.printStackTrace();
                 } catch (IOException e) {
                 // TODO Auto-generated catch block
                 e.printStackTrace();
                 }

                 newRow[0] = dateFormat.format(today);
                 //报警类型
                 newRow[1] = sAlarmType;
                 //报警设备IP地址
                 sIP = new String(strTFSAlarmInfo.struDevInfo.struDevIP.sIpV4).split("\0", 2);
                 newRow[2] = sIP[0];
                 alarmTableModel.insertRow(0, newRow);
                 break;
                 case HCNetSDK.COMM_ALARM_AID_V41:
                 HCNetSDK.NET_DVR_AID_ALARM_V41 struAIDAlarmInfo = new HCNetSDK.NET_DVR_AID_ALARM_V41();
                 struAIDAlarmInfo.write();
                 Pointer pAIDInfo = struAIDAlarmInfo.getPointer();
                 pAIDInfo.write(0, pAlarmInfo.getByteArray(0, struAIDAlarmInfo.size()), 0, struAIDAlarmInfo.size());
                 struAIDAlarmInfo.read();
                 sAlarmType = sAlarmType + "：交通事件报警信息，交通事件类型："+ struAIDAlarmInfo.struAIDInfo.dwAIDType + "，规则ID："
                 + struAIDAlarmInfo.struAIDInfo.byRuleID + "，车辆出入状态：" + struAIDAlarmInfo.struAIDInfo.byVehicleEnterState;

                 newRow[0] = dateFormat.format(today);
                 //报警类型
                 newRow[1] = sAlarmType;
                 //报警设备IP地址
                 sIP = new String(struAIDAlarmInfo.struDevInfo.struDevIP.sIpV4).split("\0", 2);
                 newRow[2] = sIP[0];
                 alarmTableModel.insertRow(0, newRow);
                 break;
                 case HCNetSDK.COMM_ALARM_TPS_V41:
                 HCNetSDK.NET_DVR_TPS_ALARM_V41 struTPSAlarmInfo = new HCNetSDK.NET_DVR_TPS_ALARM_V41();
                 struTPSAlarmInfo.write();
                 Pointer pTPSInfo = struTPSAlarmInfo.getPointer();
                 pTPSInfo.write(0, pAlarmInfo.getByteArray(0, struTPSAlarmInfo.size()), 0, struTPSAlarmInfo.size());
                 struTPSAlarmInfo.read();

                 sAlarmType = sAlarmType + "：交通统计报警信息，绝对时标："+ struTPSAlarmInfo.dwAbsTime
                 + "，能见度:" + struTPSAlarmInfo.struDevInfo.byIvmsChannel
                 + "，车道1交通状态:" + struTPSAlarmInfo.struTPSInfo.struLaneParam[0].byTrafficState
                 + "，监测点编号：" + new String(struTPSAlarmInfo.byMonitoringSiteID).trim()
                 + "，设备编号：" + new String(struTPSAlarmInfo.byDeviceID ).trim()
                 + "，开始统计时间：" + struTPSAlarmInfo.dwStartTime
                 + "，结束统计时间：" + struTPSAlarmInfo.dwStopTime;

                 newRow[0] = dateFormat.format(today);
                 //报警类型
                 newRow[1] = sAlarmType;
                 //报警设备IP地址
                 sIP = new String(struTPSAlarmInfo.struDevInfo.struDevIP.sIpV4).split("\0", 2);
                 newRow[2] = sIP[0];
                 alarmTableModel.insertRow(0, newRow);
                 break;

                 case HCNetSDK.COMM_UPLOAD_FACESNAP_RESULT: //实时人脸抓拍上传
                 HCNetSDK.NET_VCA_FACESNAP_RESULT strFaceSnapInfo = new HCNetSDK.NET_VCA_FACESNAP_RESULT();
                 strFaceSnapInfo.write();
                 Pointer pFaceSnapInfo = strFaceSnapInfo.getPointer();
                 pFaceSnapInfo.write(0, pAlarmInfo.getByteArray(0, strFaceSnapInfo.size()), 0, strFaceSnapInfo.size());
                 strFaceSnapInfo.read();

                 sAlarmType = sAlarmType + "：人脸抓拍上传，人脸评分："+ strFaceSnapInfo.dwFaceScore + "，年龄段：" +
                 strFaceSnapInfo.struFeature.byAgeGroup + "，性别：" + strFaceSnapInfo.struFeature.bySex;
                 break;
                 case HCNetSDK.COMM_ALARM_ACS: //门禁主机报警信息
                 HCNetSDK.NET_DVR_ACS_ALARM_INFO strACSInfo = new HCNetSDK.NET_DVR_ACS_ALARM_INFO();
                 strACSInfo.write();
                 Pointer pACSInfo = strACSInfo.getPointer();
                 pACSInfo.write(0, pAlarmInfo.getByteArray(0, strACSInfo.size()), 0, strACSInfo.size());
                 strACSInfo.read();

                 sAlarmType = sAlarmType + "：门禁主机报警信息，卡号："+  new String(strACSInfo.struAcsEventInfo.byCardNo).trim() + "，卡类型：" +
                 strACSInfo.struAcsEventInfo.byCardType + "，报警主类型：" + strACSInfo.dwMajor + "，报警次类型：" + strACSInfo.dwMinor;

                 newRow[0] = dateFormat.format(today);
                 //报警类型
                 newRow[1] = sAlarmType;
                 //报警设备IP地址
                 sIP = new String(pAlarmer.sDeviceIP).split("\0", 2);
                 newRow[2] = sIP[0];
                 alarmTableModel.insertRow(0, newRow);

                 if(strACSInfo.dwPicDataLen>0)
                 {
                 SimpleDateFormat sf = new SimpleDateFormat("yyyyMMddHHmmss");
                 String newName = sf.format(new Date());
                 FileOutputStream fout;
                 try {
                 String filename = newName+"_ACS_card_"+ new String(strACSInfo.struAcsEventInfo.byCardNo).trim()+".jpg";
                 fout = new FileOutputStream(filename);
                 //将字节写入文件
                 long offset = 0;
                 ByteBuffer buffers = strACSInfo.pPicData.getByteBuffer(offset, strACSInfo.dwPicDataLen);
                 byte [] bytes = new byte[strACSInfo.dwPicDataLen];
                 buffers.rewind();
                 buffers.get(bytes);
                 fout.write(bytes);
                 fout.close();
                 } catch (FileNotFoundException e) {
                 // TODO Auto-generated catch block
                 e.printStackTrace();
                 } catch (IOException e) {
                 // TODO Auto-generated catch block
                 e.printStackTrace();
                 }
                 }
                 break;
                 case HCNetSDK.COMM_ID_INFO_ALARM: //身份证信息
                 HCNetSDK.NET_DVR_ID_CARD_INFO_ALARM strIDCardInfo = new HCNetSDK.NET_DVR_ID_CARD_INFO_ALARM();
                 strIDCardInfo.write();
                 Pointer pIDCardInfo = strIDCardInfo.getPointer();
                 pIDCardInfo.write(0, pAlarmInfo.getByteArray(0, strIDCardInfo.size()), 0, strIDCardInfo.size());
                 strIDCardInfo.read();

                 sAlarmType = sAlarmType + "：门禁身份证刷卡信息，身份证号码："+  new String(strIDCardInfo.struIDCardCfg.byIDNum).trim() + "，姓名：" +
                 new String(strIDCardInfo.struIDCardCfg.byName).trim() + "，报警主类型：" + strIDCardInfo.dwMajor + "，报警次类型：" + strIDCardInfo.dwMinor;

                 newRow[0] = dateFormat.format(today);
                 //报警类型
                 newRow[1] = sAlarmType;
                 //报警设备IP地址
                 sIP = new String(pAlarmer.sDeviceIP).split("\0", 2);
                 newRow[2] = sIP[0];
                 alarmTableModel.insertRow(0, newRow);
                 break;
                 case HCNetSDK.COMM_ALARM_TPS_STATISTICS: //TPS统计过车数据上传
                 HCNetSDK.NET_DVR_TPS_STATISTICS_INFO strTPSStateInfo = new HCNetSDK.NET_DVR_TPS_STATISTICS_INFO();
                 strTPSStateInfo.write();
                 Pointer pTPSStateInfo = strTPSStateInfo.getPointer();
                 pTPSStateInfo.write(0, pAlarmInfo.getByteArray(0, strTPSStateInfo.size()), 0, strTPSStateInfo.size());
                 strTPSStateInfo.read();

                 String strStartTime  = "" + String.format("%04d", strTPSStateInfo.struTPSStatisticsInfo.struStartTime.wYear) +
                 String.format("%02d", strTPSStateInfo.struTPSStatisticsInfo.struStartTime.byMonth) +
                 String.format("%02d", strTPSStateInfo.struTPSStatisticsInfo.struStartTime.byDay) +
                 String.format("%02d", strTPSStateInfo.struTPSStatisticsInfo.struStartTime.byHour) +
                 String.format("%02d", strTPSStateInfo.struTPSStatisticsInfo.struStartTime.byMinute) +
                 String.format("%02d", strTPSStateInfo.struTPSStatisticsInfo.struStartTime.bySecond);

                 sAlarmType = sAlarmType + "：TPS统计过车数据，通道号："+  strTPSStateInfo.dwChan + "，开始统计时间：" + strStartTime +
                 "车道号："+  strTPSStateInfo.struTPSStatisticsInfo.struLaneParam[0].byLane + "，小型车：" +
                 strTPSStateInfo.struTPSStatisticsInfo.struLaneParam[0].dwLightVehicle+ "，中型车：" +
                 strTPSStateInfo.struTPSStatisticsInfo.struLaneParam[0].dwMidVehicle+ "，重型车：" +
                 strTPSStateInfo.struTPSStatisticsInfo.struLaneParam[0].dwHeavyVehicle+ "，空间占有率：" +
                 strTPSStateInfo.struTPSStatisticsInfo.struLaneParam[0].fSpaceOccupyRation + "，时间占有率：" +
                 strTPSStateInfo.struTPSStatisticsInfo.struLaneParam[0].fTimeOccupyRation;

                 newRow[0] = dateFormat.format(today);
                 //报警类型
                 newRow[1] = sAlarmType;
                 //报警设备IP地址
                 sIP = new String(pAlarmer.sDeviceIP).split("\0", 2);
                 newRow[2] = sIP[0];
                 alarmTableModel.insertRow(0, newRow);
                 break;
                 */

                case HCNetSDK.COMM_SNAP_MATCH_ALARM: //人脸黑名单比对报警
                    HashMap<String,byte[]> snapImages=new HashMap<String, byte[]>();
                    float snapSimilarity=0f; //相似度
                    String snapStudentId="";  //学生id
                    String snapDeviceIP=""; //设备ip
                    String envirmentPic=""; //环境大图


                    //获取人脸信息
                    HCNetSDK.NET_VCA_FACESNAP_MATCH_ALARM strFaceSnapMatch = new HCNetSDK.NET_VCA_FACESNAP_MATCH_ALARM();
                    strFaceSnapMatch.write();
                    Pointer pFaceSnapMatch = strFaceSnapMatch.getPointer();
                    pFaceSnapMatch.write(0, pAlarmInfo.getByteArray(0, strFaceSnapMatch.size()), 0, strFaceSnapMatch.size());
                    strFaceSnapMatch.read();

                    sAlarmType = sAlarmType + "：人脸黑名单比对报警，相识度：" + strFaceSnapMatch.fSimilarity + "，黑名单姓名：" +
                            new String(strFaceSnapMatch.struBlackListInfo.struBlackListInfo.struAttribute.byName, "GBK").trim();

                    if(strFaceSnapMatch.fSimilarity<0.5){
                        System.out.println("人脸未识别："+sAlarmType);
                        return;
                    }



                    //获取人脸库ID,正儿八经是库id
                    byte[] FDIDbytes;
                    if ((strFaceSnapMatch.struBlackListInfo.dwFDIDLen > 0) && (strFaceSnapMatch.struBlackListInfo.pFDID != null)) {
                        ByteBuffer FDIDbuffers = strFaceSnapMatch.struBlackListInfo.pFDID.getByteBuffer(0, strFaceSnapMatch.struBlackListInfo.dwFDIDLen);
                        FDIDbytes = new byte[strFaceSnapMatch.struBlackListInfo.dwFDIDLen];
                        FDIDbuffers.rewind();
                        FDIDbuffers.get(FDIDbytes);
                        sAlarmType = sAlarmType + "，人脸库ID:" +new String(FDIDbytes).trim();
                    }
                    //获取设备ip
                    if (strFaceSnapMatch.struSnapInfo.struDevInfo.struDevIP.sIpV4.length>0){
                        snapDeviceIP=new String(strFaceSnapMatch.struSnapInfo.struDevInfo.struDevIP.sIpV4);
                        sAlarmType = sAlarmType + "，设备ip:" +snapDeviceIP;
                    }
                    //获取学生的证件号码
                    if(strFaceSnapMatch.struBlackListInfo.struBlackListInfo.struAttribute.byCertificateNumber.length>0){
                        snapStudentId=new String(strFaceSnapMatch.struBlackListInfo.struBlackListInfo.struAttribute.byCertificateNumber);
                        sAlarmType = sAlarmType + "，\n 证件信息："+snapStudentId;
                    }

                    //获取人脸图片ID
                    byte[] PIDbytes;
                    if ((strFaceSnapMatch.struBlackListInfo.dwPIDLen > 0) && (strFaceSnapMatch.struBlackListInfo.pPID != null)) {
                        ByteBuffer PIDbuffers = strFaceSnapMatch.struBlackListInfo.pPID.getByteBuffer(0, strFaceSnapMatch.struBlackListInfo.dwPIDLen);
                        PIDbytes = new byte[strFaceSnapMatch.struBlackListInfo.dwPIDLen];
                        PIDbuffers.rewind();
                        PIDbuffers.get(PIDbytes);

//                        snapStudentId=new String(PIDbytes).trim();
                        sAlarmType = sAlarmType + "，人脸图片ID:" + snapStudentId;
                    }



                    String fileType=".txt";
                    if (strFaceSnapMatch.byPicTransType ==0){
                        fileType=".jpg";
                    }
                    if(fileType==".txt"){
                        if(strFaceSnapMatch.dwSnapPicLen >0) //获取背景大图
                        {
                            byte[] bufferByte= strFaceSnapMatch.pSnapPicBuffer.getByteArray(0,strFaceSnapMatch.dwSnapPicLen);
                            String picUrl=new String(bufferByte);
                            Request request=new Request.Builder().url(picUrl).get().build();
                            Response response= null;
                            try {
                                response = clientOfDevice.newCall(request).execute();
                                int code=response.code();
                                if(code== HttpURLConnection.HTTP_OK){
                                    byte[] imageData=response.body().bytes();
                                    if(imageData.length>0){
//                                        snapImages.put("originImage",imageData); 暂时不写入服务器
                                        //本地存储
                                        try {
                                            envirmentPic=snapStudentId+"_"+UUID.randomUUID()+".jpg";
                                            File directory=new File("d:\\backup");
                                            if (!directory.exists()){
                                                directory.createNewFile();
                                            }

                                            FileOutputStream fileOutputStream=new FileOutputStream("d:\\backup\\"+envirmentPic);
                                            fileOutputStream.write(imageData);
                                            fileOutputStream.flush();
                                            fileOutputStream.close();
                                        }catch (Exception e){
                                            e.printStackTrace();
                                        }
                                    }
                                }
                            } catch (IOException ex) {
                                ex.printStackTrace();
                            }finally {
                                response.close();
                            }

                        }

                        if(strFaceSnapMatch.struSnapInfo.dwSnapFacePicLen >0)  //对比结果
                        {
                            byte[] bufferByte= strFaceSnapMatch.struSnapInfo.pBuffer1.getByteArray(0,strFaceSnapMatch.struSnapInfo.dwSnapFacePicLen);
                            String picUrl=new String(bufferByte);
                            Request request=new Request.Builder().url(picUrl).get().build();
                            Response response= null;
                            try {
                                response = clientOfDevice.newCall(request).execute();
                                int code=response.code();
                                if(code== HttpURLConnection.HTTP_OK){
                                    byte[] imageData=response.body().bytes();
                                    if(imageData.length>0){
                                        snapImages.put("thumbImage",imageData);
                                    }
                                }
                            } catch (IOException ex) {
                                ex.printStackTrace();
                            }finally {
                                response.close();
                            }
                        }

                        /* 证件照片获取
                        if(strFaceSnapMatch.struBlackListInfo.dwBlackListPicLen >0)
                        {

                            byte[] bufferByte= strFaceSnapMatch.struBlackListInfo.pBuffer1.getByteArray(0,strFaceSnapMatch.struBlackListInfo.dwBlackListPicLen);
                            String picUrl=new String(bufferByte);
                            Request request=new Request.Builder().url(picUrl).get().build();
                            Response response= null;
                            try {
                                response = clientOfDevice.newCall(request).execute();
                                int code=response.code();
                                if(code== HttpURLConnection.HTTP_OK){
                                    byte[] imageData=response.body().bytes();
                                    if(imageData.length>0){
                                        snapImages.put("libraryImage",imageData);
                                    }
                                }
                            } catch (IOException ex) {
                                ex.printStackTrace();
                            }finally {
                                response.close();
                            }
                        }
                        */
                    }
                    System.out.println(sAlarmType);

                    final MediaType typeJpeg=MediaType.parse("image/jpeg");
                    MultipartBody.Builder builder = new MultipartBody.Builder();
                    builder.addFormDataPart("schoolId",schoolId);
                    builder.addFormDataPart("studentUuid",snapStudentId);
                    builder.addFormDataPart("deviceIP",snapDeviceIP);
                    builder.addFormDataPart("environmentImageName",envirmentPic);
                    SimpleDateFormat simpleDateFormat=new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
                    builder.addFormDataPart("atTime",simpleDateFormat.format(today).toString());
                    builder.setType(MultipartBody.FORM);
                    Iterator iterator=snapImages.entrySet().iterator();
                    while (iterator.hasNext()){
                        Map.Entry<String,byte[]> entry= (Map.Entry) iterator.next();
                        RequestBody requestBody=RequestBody.create(typeJpeg,entry.getValue());
                        builder.addFormDataPart(entry.getKey(),entry.getKey()+".jpg",requestBody);
                    }
                    Request request;
                    //提交服务器
                    Response response= null;
                    try {
                        request=new Request.Builder().url(serverPostUrl).post(builder.build()).build();
                        response=tempClient.newCall(request).execute();
                        System.out.println(response.code()+response.body().string());
                    }catch (Exception e){

                        e.printStackTrace();

                    }finally {
                        response.close();
                    }

                    break;

                default:
                    sIP = new String(pAlarmer.sDeviceIP).split("\0", 2);
                    System.out.println("unknown message:" + sAlarmType + ",from:" + sIP[0] + ",receive:" + dateFormat.format(today));
                    break;
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}



