package com.ynpulse;


import com.burgstaller.okhttp.AuthenticationCacheInterceptor;
import com.burgstaller.okhttp.CachingAuthenticatorDecorator;
import com.burgstaller.okhttp.*;
import com.burgstaller.okhttp.digest.CachingAuthenticator;
import com.burgstaller.okhttp.digest.Credentials;
import com.burgstaller.okhttp.digest.DigestAuthenticator;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.*;
import java.net.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public  class Util {
    private DigestAuthenticator authenticator;
    private Map<String, CachingAuthenticator> authCache;
    private OkHttpClient client ;
    public Util(String username,String password){
        this.authenticator=new DigestAuthenticator(new Credentials(username,password));
        this.authCache=new ConcurrentHashMap<String, CachingAuthenticator>();
        client=new OkHttpClient.Builder()
                .authenticator(new CachingAuthenticatorDecorator(this.authenticator, this.authCache))
                .addInterceptor(new AuthenticationCacheInterceptor(authCache))
                .build();
    }
    public  byte[] getPicDataFromUrl(String sUrl)  {

        try {
            Request request=new Request.Builder().url(sUrl).get().build();
            Response response=client.newCall(request).execute();
            int code=response.code();
            if(code==HttpURLConnection.HTTP_OK){
//                InputStream inputStream=response.body()..getInputStream();
//                BufferedInputStream bufferInput=new BufferedInputStream(inputStream);
//
//                int length=bufferInput.available();
//                byte[] imageData=new byte[length];
//                bufferInput.read(imageData,0,length);
//                inputStream.close();
//                bufferInput.close();
                byte[] result=response.body().bytes();
                        response.body().close();
                return  result;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;

    }

}
