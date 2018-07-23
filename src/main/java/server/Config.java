package server;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

/**
 * Created by tiang on 2018/5/9.
 */
public class Config {
    public static String basePath;
    public static List<String> fileTypes;
    public static int port;

    static{
        Properties properties = new Properties();
        try {
            properties.load(new FileInputStream("config/config.properties"));
            port = Integer.parseInt(properties.getProperty("port", "8082"));
            String types = properties.getProperty("fileTypes", ".doc;.docx;.wps;.pdf");
            fileTypes = Arrays.asList(types.split(";"));
            basePath = properties.getProperty("basePath", "file/");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
