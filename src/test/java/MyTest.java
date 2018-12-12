import com.ynpulse.com.ynpulse.entity.Config;
import com.ynpulse.com.ynpulse.entity.Device;
import org.ho.yaml.Yaml;
import org.junit.Test;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

public class MyTest {

    @Test
    public void writePic() throws Exception{
//       File directory=new File("D:\\backup");
//       if(!directory.exists()){
//           directory.mkdirs();
//       }
//        FileOutputStream fileOutputStream=new FileOutputStream("D:\\backup\\"+ UUID.randomUUID()+".jpg");
//       fileOutputStream.write("Hello".getBytes());
//       fileOutputStream.flush();
//       fileOutputStream.close();

        System.out.println(System.getProperty("user.dir"));

        InputStream inputStream=null;

        inputStream=new FileInputStream(System.getProperty("user.dir")+"/config/config.yml");
        Config config= Yaml.loadType(inputStream,Config.class);
        System.out.println(config);

        for(Device device:config.getDevices()){
            System.out.println(device);
        }

        TestThread t=new TestThread();
        t.start();
        t.join();
        System.out.println("thread started");


    }

    public class TestThread extends Thread{
        private SimpleDateFormat simpleDateFormat=new SimpleDateFormat("yyyy-MM-dd");
        private String runTimePath=System.getProperty("user.dir");
        private String m_sDeviceIP="1";
        private BufferedOutputStream logStream=null;
        private String logFile;

        public TestThread(){
            System.out.println("class init");
        }

        private void log(String content){

            String currentDate=new SimpleDateFormat("yyyy-MM-dd").format(new Date());

            String newLogFile=runTimePath+File.separator+"logs"+File.separator+this.m_sDeviceIP+"_"+currentDate+".log";

            if(logStream==null||!newLogFile.equals(logFile)){
                if(logStream!=null){
                    try {
                        logStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                        return;
                    }
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



        @Override
        public void run() {
            System.out.println("begin run");
            while (true){
                try {
                    Thread.sleep(1000);
                    String logPath=System.getProperty("user.dir")+File.separator+"logs";
                    File logDir=new File(logPath);
                    if (!logDir.exists()){
                        logDir.mkdirs();
                    }

                    log("sss");

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
