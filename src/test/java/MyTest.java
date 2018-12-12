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
        public TestThread(){
            System.out.println("class init");
        }
        @Override
        public void run() {
            System.out.println("begin run");
            while (true){
                try {
                    Thread.sleep(1000);
                    String logPath=System.getProperty("user.dir")+File.separator+simpleDateFormat.format(new Date());
                    File logDir=new File(logPath);
                    if (!logDir.exists()){
                        logDir.mkdirs();
                    }

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
