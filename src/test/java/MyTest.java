import com.ynpulse.com.ynpulse.entity.Config;
import org.ho.yaml.Yaml;
import org.junit.Test;

import java.io.*;
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




    }
}
