package com.ynpulse;

import com.burgstaller.okhttp.AuthenticationCacheInterceptor;
import com.burgstaller.okhttp.CachingAuthenticatorDecorator;
import com.burgstaller.okhttp.digest.CachingAuthenticator;
import com.burgstaller.okhttp.digest.Credentials;
import com.burgstaller.okhttp.digest.DigestAuthenticator;
import com.hikvision.HCNetSDK;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import okhttp3.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class HkAlarm extends Thread {

    private String schoolId ;  //学校id
    private String m_sDeviceIP;//已登录设备的IP地址
    private String m_sDevicePort; //设备ip
    private String m_sDeviceUser; //设备用户
    private String m_sDevicePwd ; //设备密码
    private String serverPostUrl;  //服务器url
    private String serverUser; //服务器端用户名
    private String serverPwd; //服务器端密码
    private String runTimePath; //运行路径

    private String logFile; //日志文件名
    public BufferedOutputStream logStream=null;

    private HCNetSDK hCNetSDK = HCNetSDK.INSTANCE;
    HCNetSDK.NET_DVR_DEVICEINFO_V30 m_strDeviceInfo;//设备信息
    NativeLong lUserID;//用户句柄
    NativeLong lAlarmHandle;//报警布防句柄
    NativeLong lListenHandle;//报警监听句柄
    HCNetSDK.FMSGCallBack fMSFCallBack;//报警回调函数实现
    HCNetSDK.FMSGCallBack_V31 fMSFCallBack_V31;//报警回调函数实现

    private DigestAuthenticator authenticatorOfDevice=null;
    final Map<String, CachingAuthenticator> authCacheOfDevice=new ConcurrentHashMap<String, CachingAuthenticator>();
    private OkHttpClient clientOfDevice=null;



    private DigestAuthenticator authenticatorOfServer=null;
    final Map<String, CachingAuthenticator> authCacheOfServer=new ConcurrentHashMap<String, CachingAuthenticator>();
    private OkHttpClient clientOfServer=null ;


    final OkHttpClient tempClient=new OkHttpClient.Builder().build();

    public HkAlarm(String schoolId, String m_sDeviceIP, String m_sDevicePort, String m_sDeviceUser, String m_sDevicePwd, String serverPostUrl, String serverUser, String serverPwd) {
        this.schoolId = schoolId;
        this.m_sDeviceIP = m_sDeviceIP;
        this.m_sDevicePort = m_sDevicePort;
        this.m_sDeviceUser = m_sDeviceUser;
        this.m_sDevicePwd = m_sDevicePwd;
        this.serverPostUrl = serverPostUrl;
        this.serverUser = serverUser;
        this.serverPwd = serverPwd;


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

        runTimePath=System.getProperty("user.dir");

        //建立日志目录
        File logDir=new File(runTimePath+File.separator+"logs");
        if(!logDir.exists()){
            logDir.mkdir();
        }


        this.lUserID = new NativeLong(-1);
        this.lAlarmHandle = new NativeLong(-1);
        this.lListenHandle = new NativeLong(-1);
    }


    //初始化设备
    private boolean loginToDevice() {
        boolean initSuc = hCNetSDK.NET_DVR_Init();
        if (initSuc != true) {
            System.out.printf("设备%s初始化失败\n",this.m_sDeviceIP);
            return false;
        }

        //注册之前先注销已注册的用户,预览情况下不可注销
        if (lUserID.longValue() > -1) {
            hCNetSDK.NET_DVR_Logout(lUserID);
            lUserID = new NativeLong(-1);
        }

        //注册
        lUserID = hCNetSDK.NET_DVR_Login_V30(m_sDeviceIP,
                (short)Integer.parseInt(m_sDevicePort), m_sDeviceUser, m_sDevicePwd, m_strDeviceInfo);

        long userID = lUserID.longValue();
        if (userID == -1) {
            System.out.printf("设备%s注册失败\n",this.m_sDeviceIP);
            return false;
        }
        System.out.printf("设备%s注册成功\n",this.m_sDeviceIP);

        return true;
    }


    //布防
    private boolean setupAlarmChan() {
        if (lUserID.intValue() == -1) {
            System.out.printf("请先注册设备:%s\n",this.m_sDeviceIP);
            return false;
        }
        if (lAlarmHandle.intValue() < 0)//尚未布防,需要布防
        {
            if (fMSFCallBack_V31 == null) {
                fMSFCallBack_V31 = new FMSGCallBack_V31();
                Pointer pUser = null;
                if (!hCNetSDK.NET_DVR_SetDVRMessageCallBack_V31(fMSFCallBack_V31, pUser)) {
                    System.out.printf("设置回调函数失败,%s!\n",m_sDeviceIP);
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
                System.out.printf("%s布防失败\n",m_sDeviceIP);
                return false;
            }
        }

        System.out.printf("%s布防成功\n",m_sDeviceIP);
        return true;
    }

    //记录日志
    private void log(String content){

        String currentDate=new SimpleDateFormat("yyyy-MM-dd").format(new Date());

        String newLogFile=runTimePath+File.separator+"logs"+File.separator+this.m_sDeviceIP+"_"+currentDate+".log";

        if(logStream==null||!newLogFile.equals(logFile)){
            if(logStream!=null){
                try {
                    logStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return;
            }
            logFile=newLogFile;
            File file=new File(logFile);
            if(!file.exists()){
                try {
                    boolean created=file.createNewFile();
                    if(!created){
                        System.out.printf("设备%s日志创建失败\n",this.m_sDeviceIP);
                        return;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                }
            }
            try {
                FileOutputStream outputStream=new FileOutputStream(file);
                logStream=new BufferedOutputStream(outputStream);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return;
            }
        }



        try {
            this.logStream.write(content.getBytes());
            this.logStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
            if(logStream!=null){
                try {
                    logStream.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        }
    }

    public class FMSGCallBack_V31 implements HCNetSDK.FMSGCallBack_V31 {
        //报警信息回调函数
        public boolean invoke(NativeLong lCommand, HCNetSDK.NET_DVR_ALARMER pAlarmer, Pointer pAlarmInfo, int dwBufLen, Pointer pUser) {
            AlarmDataHandle(lCommand, pAlarmer, pAlarmInfo, dwBufLen, pUser);
            return true;
        }
    }


    //消息处理器
    public void AlarmDataHandle(NativeLong lCommand, HCNetSDK.NET_DVR_ALARMER pAlarmer, Pointer pAlarmInfo, int dwBufLen, Pointer pUser) {
        try {
            String sAlarmType = new String();

            //报警时间
            Date today = new Date();
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String[] sIP = new String[2];
            sAlarmType = new String("lCommand=") + lCommand.intValue();
            //lCommand是传的报警类型
            switch (lCommand.intValue()) {

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
                        log("人脸未识别："+sAlarmType);
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
                                            envirmentPic=snapStudentId.trim()+"_"+ UUID.randomUUID()+".jpg";
                                            SimpleDateFormat simpleDateFormat=new SimpleDateFormat("yyyy-MM-dd");
                                            String snapshotPath=runTimePath+File.separator+"snapshot"+File.separator+simpleDateFormat.format(new Date());
                                            File directory=new File(snapshotPath);
                                            if (!directory.exists()){
                                                directory.mkdirs();
                                            }
                                            System.out.println(snapshotPath+File.separator+envirmentPic);
                                            FileOutputStream fileOutputStream=new FileOutputStream(snapshotPath+File.separator+envirmentPic);
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

                    }
                    log(sAlarmType);
                    final MediaType typeJpeg=MediaType.parse("image/jpeg");
                    MultipartBody.Builder builder = new MultipartBody.Builder();
                    builder.addFormDataPart("schoolId",schoolId);
                    builder.addFormDataPart("studentUuid",snapStudentId);
                    builder.addFormDataPart("deviceIP",snapDeviceIP);
                    builder.addFormDataPart("environmentImageName",envirmentPic);
                    SimpleDateFormat simpleDateFormat=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
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
                        log(response.code()+response.body().string());
                    }catch (Exception e){
                        e.printStackTrace();

                    }finally {
                        response.close();
                    }

                    break;

                default:
                    sIP = new String(pAlarmer.sDeviceIP).split("\0", 2);
                    log("unknown message:" + sAlarmType + ",from:" + sIP[0] + ",receive:" + dateFormat.format(today));
                    break;
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void run() {
        loginToDevice();
        setupAlarmChan();
        while (true){
            try {
                sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
